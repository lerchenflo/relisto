package com.lerchenflo.willhabenupdater.utilities

/** Current time, injectable so tests control it. */
interface TimeProvider {
    fun nowEpochMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = com.lerchenflo.willhabenupdater.utilities.nowEpochMillis()
}
