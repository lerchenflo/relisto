package com.lerchenflo.willhabenupdater.utilities

/**
 * Formats and parses euro prices in the Austrian notation ("1.234,50 €").
 * Prices are stored as euro cents.
 */
object PriceFormatter {

    private val NORMALIZED_PRICE = Regex("""\d+(\.\d{1,2})?""")

    fun format(cents: Long): String = "${formatAmount(cents)} €"

    /** Amount without currency sign, as shown in the price input field. */
    fun formatAmount(cents: Long): String {
        val groupedEuros = (cents / 100).toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
        val rest = cents % 100
        return if (rest == 0L) groupedEuros else "$groupedEuros,${rest.toString().padStart(2, '0')}"
    }

    /**
     * Parses user input like "1234", "1.234", "12,5" or "1.234,50 €" to cents.
     * Returns null when the input is not a valid price.
     */
    fun parse(input: String): Long? {
        val cleaned = input.replace("€", "").replace(" ", "").trim()
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
            // A single dot followed by exactly three digits is a thousands separator ("1.234")
            cleaned.count { it == '.' } == 1 && cleaned.substringAfter('.').length != 3 -> cleaned
            else -> cleaned.replace(".", "")
        }
        if (!NORMALIZED_PRICE.matches(normalized)) return null

        val euros = normalized.substringBefore('.').toLongOrNull() ?: return null
        val cents = normalized.substringAfter('.', "").padEnd(2, '0').toLong()
        return euros * 100 + cents
    }
}
