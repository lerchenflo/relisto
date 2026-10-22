package com.lerchenflo.relisto.datasource.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Keeps at least [minIntervalMillis] between the starts of two requests to one server, so a bulk
 * action or an import never fires requests back to back (willhaben must not see the app as a bot).
 */
class RequestSpacing(
    private val minIntervalMillis: Long,
    private val nowMillis: () -> Long = monotonicMillis(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastStart: Long? = null

    /** Suspends until the next request may start. */
    suspend fun awaitTurn() = mutex.withLock {
        lastStart?.let { last ->
            val wait = minIntervalMillis - (nowMillis() - last)
            if (wait > 0) pause(wait)
        }
        lastStart = nowMillis()
    }

    companion object {
        /** No spacing, e.g. in tests. */
        val NONE get() = RequestSpacing(minIntervalMillis = 0)

        private fun monotonicMillis(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
