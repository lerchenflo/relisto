package com.lerchenflo.willhabenupdater.utilities

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class PriceFormatterTest {

    @Test
    fun formatWholeEurosHasNoDecimals() {
        assertThat(PriceFormatter.format(4500)).isEqualTo("45 €")
    }

    @Test
    fun formatCentsUsesCommaAndTwoDigits() {
        assertThat(PriceFormatter.format(1205)).isEqualTo("12,05 €")
    }

    @Test
    fun formatGroupsThousandsWithDots() {
        assertThat(PriceFormatter.format(123456750)).isEqualTo("1.234.567,50 €")
    }

    @Test
    fun formatAmountOmitsCurrencySign() {
        assertThat(PriceFormatter.formatAmount(123450)).isEqualTo("1.234,50")
    }

    @Test
    fun parsePlainNumber() {
        assertThat(PriceFormatter.parse("45")).isEqualTo(4500L)
    }

    @Test
    fun parseCommaAsDecimalSeparator() {
        assertThat(PriceFormatter.parse("12,5")).isEqualTo(1250L)
    }

    @Test
    fun parseDotWithThreeDigitsAsThousandsSeparator() {
        assertThat(PriceFormatter.parse("1.234")).isEqualTo(123400L)
    }

    @Test
    fun parseDotWithTwoDigitsAsDecimalPoint() {
        assertThat(PriceFormatter.parse("12.99")).isEqualTo(1299L)
    }

    @Test
    fun parseFormattedPriceWithCurrencyAndSpaces() {
        assertThat(PriceFormatter.parse(" 1.234,50 € ")).isEqualTo(123450L)
    }

    @Test
    fun parseEmptyInputReturnsNull() {
        assertThat(PriceFormatter.parse("  ")).isNull()
    }

    @Test
    fun parseTextReturnsNull() {
        assertThat(PriceFormatter.parse("abc")).isNull()
    }

    @Test
    fun parseMoreThanTwoDecimalsReturnsNull() {
        assertThat(PriceFormatter.parse("1,999")).isNull()
    }

    @Test
    fun parseNegativeReturnsNull() {
        assertThat(PriceFormatter.parse("-5")).isNull()
    }
}
