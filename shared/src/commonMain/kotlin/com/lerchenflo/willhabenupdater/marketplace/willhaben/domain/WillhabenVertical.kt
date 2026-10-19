package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

/** willhaben's areas ("Marktplatz", "Auto & Motor", "Immobilien") and their ad types (`adTypeId`). */
enum class WillhabenVertical(internal val adTypeIds: Set<Int>) {
    MARKETPLACE(setOf(67, 68, 69)),
    MOTOR(setOf(20, 21, 25, 26)),
    REAL_ESTATE(setOf(1, 2, 3, 4, 5, 6, 8, 15, 16));

    companion object {
        /** A missing ad type means marketplace: listings stored before it was kept are all marketplace listings. */
        fun of(adTypeId: Int?): WillhabenVertical? {
            val type = adTypeId ?: return MARKETPLACE
            return entries.firstOrNull { type in it.adTypeIds }
        }
    }
}
