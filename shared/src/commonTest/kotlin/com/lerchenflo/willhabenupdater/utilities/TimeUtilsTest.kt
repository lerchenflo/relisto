package com.lerchenflo.willhabenupdater.utilities

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.TimeZone
import kotlin.test.Test

class TimeUtilsTest {

    @Test
    fun formatDateTimePadsDayMonthHourAndMinute() {
        // 2026-03-05T07:04:00Z
        assertThat(formatDateTime(1772694240000L, TimeZone.UTC)).isEqualTo("05.03.2026 07:04")
    }

    @Test
    fun formatDateTimeUsesGivenTimeZone() {
        // 2026-10-18T22:30:00Z is already the next day in Vienna (UTC+2 in October)
        assertThat(formatDateTime(1792362600000L, TimeZone.of("Europe/Vienna"))).isEqualTo("19.10.2026 00:30")
    }
}
