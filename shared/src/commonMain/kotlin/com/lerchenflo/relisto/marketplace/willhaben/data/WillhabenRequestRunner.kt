package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs logged-in willhaben requests one at a time, renews an expired session once and backs off
 * when willhaben limits the request rate (2 s, 4 s, 8 s).
 */
class WillhabenRequestRunner(
    private val renewSession: suspend () -> Boolean,
    private val backoff: suspend (millis: Long) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun <T> run(request: suspend () -> Result<T, DataError.Network>): Result<T, DataError.Network> =
        mutex.withLock {
            var renewed = false
            var retries = 0
            var result = request()
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
                result = request()
            }
            result
        }

    private companion object {
        val RATE_LIMITED = setOf(DataError.Network.TOO_MANY_REQUESTS, DataError.Network.SERVICE_UNAVAILABLE)
        const val MAX_RETRIES = 3
        const val FIRST_BACKOFF_MILLIS = 2_000L
    }
}
