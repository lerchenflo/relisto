package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.RequestSpacing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Runs logged-in willhaben requests one at a time with a pause between them ([spacing]), renews an
 * expired session once and backs off when willhaben limits the request rate (2 s, 4 s, 8 s).
 * Image uploads may run up to four at a time, like the web client.
 */
class WillhabenRequestRunner(
    private val renewSession: suspend () -> Boolean,
    private val backoff: suspend (millis: Long) -> Unit,
    private val spacing: RequestSpacing = RequestSpacing.NONE,
) {
    private val mutex = Mutex()
    private val uploads = Semaphore(MAX_PARALLEL_UPLOADS)

    suspend fun <T> run(request: suspend () -> Result<T, DataError.Network>): Result<T, DataError.Network> =
        mutex.withLock { withRetries(request) }

    /** For image uploads: up to [MAX_PARALLEL_UPLOADS] at the same time, each still spaced and retried. */
    suspend fun <T> runParallel(request: suspend () -> Result<T, DataError.Network>): Result<T, DataError.Network> =
        uploads.withPermit { withRetries(request) }

    private suspend fun <T> withRetries(request: suspend () -> Result<T, DataError.Network>): Result<T, DataError.Network> {
        var renewed = false
        var retries = 0
        var result = spaced(request)
        while (true) {
            val error = (result as? Result.Error)?.error ?: break
            if (error == DataError.Network.UNAUTHORIZED && !renewed) {
                renewed = true
                if (!renewSession()) break
            } else if (error in RATE_LIMITED && retries < MAX_RETRIES) {
                backoff(FIRST_BACKOFF_MILLIS shl retries)
                retries++
            } else {
                break
            }
            result = spaced(request)
        }
        return result
    }

    private suspend fun <T> spaced(request: suspend () -> T): T {
        spacing.awaitTurn()
        return request()
    }

    private companion object {
        val RATE_LIMITED = setOf(DataError.Network.TOO_MANY_REQUESTS, DataError.Network.SERVICE_UNAVAILABLE)
        const val MAX_RETRIES = 3
        const val FIRST_BACKOFF_MILLIS = 2_000L
        const val MAX_PARALLEL_UPLOADS = 4
    }
}
