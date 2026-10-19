package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

/**
 * willhaben ad types (`adTypeId`) and their routes, as the willhaben web client (bbx-adin) builds them.
 * A missing ad type means a marketplace listing: listings imported before the ad type was stored are all marketplace listings.
 */
internal object WillhabenAdTypes {

    private const val MARKETPLACE_PRO = 69
    private val MARKETPLACE = setOf(MARKETPLACE_AD_TYPE_ID, 68, MARKETPLACE_PRO)
    private val MOTOR = setOf(20, 21, 25, 26)
    private val REAL_ESTATE = setOf(1, 2, 3, 4, 5, 6, 8, 15, 16)

    /** The editable listing JSON; null for ad types the app does not know (e.g. jobs). */
    fun detailRoute(adId: String, adTypeId: Int?): String? {
        val type = adTypeId ?: MARKETPLACE_AD_TYPE_ID
        return when (type) {
            MARKETPLACE_PRO -> "/iad/bapcom/$adId"
            in MARKETPLACE -> "/iad/bap/$adId"
            in MOTOR -> "/iad/motor/get/$adId"
            in REAL_ESTATE -> "/iad/estate/$adId"
            else -> null
        }
    }

    /** "Neu veröffentlichen" copies the listing into a new draft; only seen for private marketplace and motor listings. */
    fun copyRoute(adId: String, adTypeId: Int?): String? {
        val type = adTypeId ?: MARKETPLACE_AD_TYPE_ID
        return when (type) {
            MARKETPLACE_AD_TYPE_ID -> "/iad/bap/copy/$adId"
            in MOTOR -> "/iad/motor/copy/$adId"
            else -> null
        }
    }

    fun isMarketplace(adTypeId: Int?): Boolean = (adTypeId ?: MARKETPLACE_AD_TYPE_ID) in MARKETPLACE
}
