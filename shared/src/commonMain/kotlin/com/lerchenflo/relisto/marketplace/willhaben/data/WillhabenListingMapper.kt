package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.ImportedListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeCodes
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.utilities.HtmlText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Reads the editable willhaben listing JSON (GET /iad/bap/{adId}, other verticals see [WillhabenAdTypes]).
 * Only listing fields are read; the contact data in the same JSON is never touched here.
 */
object WillhabenListingMapper {

    /** willhaben "Zustand" value codes and their neutral condition. */
    private val CONDITIONS = mapOf(
        "neu" to Condition.NEW,
        "MintCondition" to Condition.LIKE_NEW,
        "gebraucht" to Condition.USED,
        "defekt" to Condition.DEFECTIVE,
    )
    private const val HANDOVER_PICKUP = "PICKUP"
    private const val HANDOVER_SHIPPING = "Versand"

    fun toImportedListing(
        listing: RemoteListing,
        categoryPathOf: (Int) -> List<WillhabenCategoryRef>?,
    ): ImportedListing? {
        val json = runCatching { AppJson.parseToJsonElement(listing.detailJson) as? JsonObject }.getOrNull() ?: return null
        val attributes = json.treeAttributes()
        val handover = attributes[WillhabenAttributeCodes.HANDOVER].orEmpty()

        val content = ListingContent(
            title = json.text("heading") ?: listing.summary.title.orEmpty(),
            description = HtmlText.toPlainText(json.text("description").orEmpty()),
            priceCents = (json["price"] as? JsonPrimitive)?.doubleOrNull?.let { (it * 100).roundToLong() },
            condition = attributes[WillhabenAttributeCodes.CONDITION]?.firstOrNull()?.let { CONDITIONS[it] },
            postcode = json.text("postCode").orEmpty(),
        )
        val fields = WillhabenListingFields(
            categoryPath = json.categoryPath(categoryPathOf),
            attributes = attributes - WillhabenAttributeCodes.CONDITION - WillhabenAttributeCodes.HANDOVER,
            locationId = (json["locationId"] as? JsonPrimitive)?.intOrNull,
            locationLabel = json.text("location"),
            delivery = DeliveryOptions(
                pickup = HANDOVER_PICKUP in handover,
                shipping = HANDOVER_SHIPPING in handover,
            ),
            adTypeId = listing.summary.adTypeId ?: (json["adTypeId"] as? JsonPrimitive)?.intOrNull,
        )
        return ImportedListing(
            remoteId = listing.summary.remoteId,
            remoteUuid = json.text("adUuid") ?: listing.summary.remoteUuid,
            content = content,
            fields = fields,
            remoteStatus = listing.summary.statusText,
            expiresAt = json.text("endDate")?.let(::parseInstantMillis),
            imageUrls = listing.summary.imageUrls,
            snapshotJson = listing.detailJson,
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** attribute code -> selected value codes */
    private fun JsonObject.treeAttributes(): Map<String, List<String>> =
        (this["treeAttributes"] as? JsonArray).orEmpty().mapNotNull { element ->
            val attribute = element as? JsonObject ?: return@mapNotNull null
            val code = (attribute["attributeNode"] as? JsonObject)?.text("code") ?: return@mapNotNull null
            val values = (attribute["selectedAttributeValueNodes"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.text("code") }
            code to values
        }.toMap()

    private fun JsonObject.categoryPath(categoryPathOf: (Int) -> List<WillhabenCategoryRef>?): List<WillhabenCategoryRef> {
        val leafId = (this["categoryTreeId"] as? JsonPrimitive)?.intOrNull
        leafId?.let(categoryPathOf)?.let { return it }
        val path = ((this["categoryPath"] as? JsonObject)?.get("path") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
        return path.mapIndexed { index, node ->
            WillhabenCategoryRef(
                treeId = if (index == path.lastIndex) leafId ?: 0 else 0,
                code = node.text("code").orEmpty(),
                label = node.text("label").orEmpty(),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun parseInstantMillis(text: String): Long? = runCatching { Instant.parse(text).toEpochMilliseconds() }.getOrNull()

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
