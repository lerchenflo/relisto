package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeCodes
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.utilities.HtmlText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** A willhaben context link: the URL is `/{serviceName}{relativePath}` below the web API. */
data class ContextLink(val serviceName: String, val relativePath: String) {
    val route: String get() = "/$serviceName$relativePath"
}

object ContextLinks {
    /** Finds a link in `contextLinkList` (either `{ "contextLink": [...] }` or a plain list). */
    fun find(json: JsonObject, id: String): ContextLink? {
        val container = json["contextLinkList"]
        val links = when (container) {
            is JsonObject -> container["contextLink"] as? JsonArray
            is JsonArray -> container
            else -> null
        } ?: return null
        val link = links.mapNotNull { it as? JsonObject }.firstOrNull { it.text("id") == id } ?: return null
        val serviceName = link.text("serviceName") ?: return null
        val relativePath = link.text("relativePath") ?: return null
        return ContextLink(serviceName, relativePath)
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * Writes the fields the app manages into a listing JSON received from willhaben and keeps
 * everything else unchanged, so new or unknown willhaben fields survive.
 * Attributes are sent as codes only, like the willhaben web app does.
 * Listings of other verticals (Auto & Motor, ...) only get the text keys they already have:
 * their JSON has not been recorded yet, and marketplace attributes do not belong there.
 */
object AdJsonPatcher {

    private val CONDITION_CODES = mapOf(
        Condition.NEW to "neu",
        Condition.LIKE_NEW to "MintCondition",
        Condition.USED to "gebraucht",
        Condition.DEFECTIVE to "defekt",
    )

    /**
     * [withCategory] also writes the local category (new listings only): an existing listing keeps its
     * category, because willhaben converts a listing for a category change.
     */
    fun patch(
        serverJson: JsonObject,
        content: ListingContent,
        fields: WillhabenListingFields,
        withCategory: Boolean = false,
    ): JsonObject {
        val textValues = mapOf(
            "heading" to JsonPrimitive(content.title),
            "description" to JsonPrimitive(HtmlText.fromPlainText(content.description)),
            "price" to (content.priceCents?.let { JsonPrimitive(it / 100.0) } ?: JsonNull),
            "postCode" to JsonPrimitive(content.postcode),
        )
        val location = fields.locationId?.let { mapOf("locationId" to JsonPrimitive(it)) } ?: emptyMap()
        if (!WillhabenAdTypes.isMarketplace(fields.adTypeId)) {
            return JsonObject(serverJson + (textValues + location).filterKeys { it in serverJson })
        }

        val managedAttributes = buildMap {
            putAll(fields.attributes)
            content.condition?.let { put(WillhabenAttributeCodes.CONDITION, listOf(CONDITION_CODES.getValue(it))) }
            put(
                WillhabenAttributeCodes.HANDOVER,
                buildList {
                    if (fields.delivery.pickup) add("PICKUP")
                    if (fields.delivery.shipping) add("Versand")
                },
            )
        }.filterValues { it.isNotEmpty() }

        val category = if (withCategory && fields.categoryPath.isNotEmpty()) categoryValues(serverJson, fields) else emptyMap()
        return JsonObject(
            serverJson + textValues +
                ("treeAttributes" to patchAttributes(serverJson["treeAttributes"] as? JsonArray, managedAttributes)) +
                location + category
        )
    }

    /** Leaf id and path from the top category down, like the listing JSON willhaben sends. */
    private fun categoryValues(serverJson: JsonObject, fields: WillhabenListingFields): Map<String, JsonElement> {
        val path = buildJsonArray {
            fields.categoryPath.forEach { add(buildJsonObject { put("label", it.label); put("code", it.code) }) }
        }
        val categoryPath = (serverJson["categoryPath"] as? JsonObject).orEmptyObject() + ("path" to path)
        return mapOf(
            "categoryTreeId" to JsonPrimitive(fields.categoryPath.last().treeId),
            "categoryPath" to JsonObject(categoryPath),
        )
    }

    private fun JsonObject?.orEmptyObject(): Map<String, JsonElement> = this ?: emptyMap()

    /** Keeps attributes the app does not know, replaces the managed ones with code-only nodes. */
    private fun patchAttributes(existing: JsonArray?, managed: Map<String, List<String>>): JsonArray = buildJsonArray {
        existing.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { attributeCode(it) !in managed.keys && attributeCode(it) !in MANAGED_CODES }
            .forEach { add(it) }
        managed.forEach { (code, values) ->
            add(
                buildJsonObject {
                    put("attributeNode", buildJsonObject { put("code", code) })
                    put("selectedAttributeValueNodes", buildJsonArray { values.forEach { value -> add(buildJsonObject { put("code", value) }) } })
                }
            )
        }
    }

    /** Condition and handover always come from the app, even when cleared there. */
    private val MANAGED_CODES = setOf(WillhabenAttributeCodes.CONDITION, WillhabenAttributeCodes.HANDOVER)

    private fun attributeCode(attribute: JsonObject): String? =
        ((attribute["attributeNode"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
