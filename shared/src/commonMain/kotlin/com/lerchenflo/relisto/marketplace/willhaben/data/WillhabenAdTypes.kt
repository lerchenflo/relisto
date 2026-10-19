package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenVertical

/**
 * willhaben ad types (`adTypeId`) and their routes, as the willhaben web client (bbx-adin) builds them.
 * A missing ad type means a marketplace listing: listings imported before the ad type was stored are all marketplace listings.
 */
internal object WillhabenAdTypes {

    private const val MARKETPLACE_PRO = 69

    /** The editable listing JSON; null for ad types the app does not know (e.g. jobs). */
    fun detailRoute(adId: String, adTypeId: Int?): String? = when (WillhabenVertical.of(adTypeId)) {
        WillhabenVertical.MARKETPLACE -> if (adTypeId == MARKETPLACE_PRO) "/iad/bapcom/$adId" else "/iad/bap/$adId"
        WillhabenVertical.MOTOR -> "/iad/motor/get/$adId"
        WillhabenVertical.REAL_ESTATE -> "/iad/estate/$adId"
        null -> null
    }

    /** "Neu veröffentlichen" copies the listing into a new draft; only seen for private marketplace and motor listings. */
    fun copyRoute(adId: String, adTypeId: Int?): String? = when {
        (adTypeId ?: MARKETPLACE_AD_TYPE_ID) == MARKETPLACE_AD_TYPE_ID -> "/iad/bap/copy/$adId"
        WillhabenVertical.of(adTypeId) == WillhabenVertical.MOTOR -> "/iad/motor/copy/$adId"
        else -> null
    }

    fun isMarketplace(adTypeId: Int?): Boolean = WillhabenVertical.of(adTypeId) == WillhabenVertical.MARKETPLACE
}
