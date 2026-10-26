package com.lerchenflo.relisto.utilities

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Formats epoch millis as "2026-10-18" (sorts by name, e.g. in file names). */
@OptIn(ExperimentalTime::class)
fun formatIsoDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date.toString()

/** Formats epoch millis as "18.10.2026 19:05". */
@OptIn(ExperimentalTime::class)
fun formatDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val day = dateTime.day.toString().padStart(2, '0')
    val month = (dateTime.month.ordinal + 1).toString().padStart(2, '0')
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$day.$month.${dateTime.year} $hour:$minute"
}
