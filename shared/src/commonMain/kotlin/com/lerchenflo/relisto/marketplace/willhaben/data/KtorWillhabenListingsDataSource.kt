package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.map
import com.lerchenflo.relisto.datasource.network.get
import com.lerchenflo.relisto.marketplace.willhaben.domain.MyAdvertsPage
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActionLink
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Logged-in willhaben endpoints for the user's own listings (read only). The list is parsed by
 * hand: only the fields the web client itself uses are read, so new or changed fields never break it.
 */
class KtorWillhabenListingsDataSource(
    private val httpClient: HttpClient,
) {

    suspend fun fetchMyAdverts(page: Int, rows: Int): Result<MyAdvertsPage, DataError.Network> {
        return httpClient.get<JsonObject>(
            route = "/myatz/atverz/showpagedadsfromuser",
            queryParameters = mapOf("page" to page, "rows" to rows, "vertical" to "bap"),
        ).map { it.toMyAdvertsPage() }
    }

    /** The empty listing of [productId] a new listing starts from (`GET /iad/bap?productId=67`), as raw JSON. */
    suspend fun fetchTemplateJson(productId: Int): Result<String, DataError.Network> {
        return httpClient.get<JsonObject>(route = "/iad/bap", queryParameters = mapOf("productId" to productId)).map { it.toString() }
    }

    /** Raw JSON of one listing as the edit form loads it; [route] comes from [WillhabenAdTypes.detailRoute]. */
    suspend fun fetchListingJson(route: String): Result<String, DataError.Network> {
        return httpClient.get<JsonObject>(route = route).map { it.toString() }
    }

    /**
     * All images of one listing in their order ([route] is the listing's `imageList` link):
     * the full-size image where there is one, else the large preview.
     */
    suspend fun fetchImageUrls(route: String): Result<List<String>, DataError.Network> {
        return httpClient.get<JsonObject>(route = route).map { list ->
            (list["advertImage"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { image -> image.text("referenceImageUrl") ?: image.text("mainImageUrl") }
        }
    }

    private fun JsonObject.toMyAdvertsPage(): MyAdvertsPage {
        val adverts = (this["verticalList"] as? JsonArray).orEmpty()
            .flatMap { vertical -> ((vertical as? JsonObject)?.get("advertDetails") as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonObject)?.toSummary() }
        val total = (this["totalNumberOfAdvertDetails"] as? JsonPrimitive)?.intOrNull ?: adverts.size
        return MyAdvertsPage(adverts = adverts, total = total)
    }

    private fun JsonObject.toSummary(): RemoteListingSummary? {
        val id = text("id") ?: return null
        return RemoteListingSummary(
            remoteId = id,
            remoteUuid = text("uuid"),
            adTypeId = (this["adTypeId"] as? JsonPrimitive)?.intOrNull,
            title = text("description"),
            // The edit status name is what the web app shows ("Online", "Abgelaufen", ...)
            statusText = (this["advertEditStatusActionsList"] as? JsonObject)?.text("statusName")
                ?: (this["advertStatus"] as? JsonObject)?.text("description"),
            statusActions = statusActions(),
        )
    }

    /** The status changes willhaben offers for this listing; unknown and paid actions are left out. */
    private fun JsonObject.statusActions(): List<WillhabenStatusActionLink> {
        val links = ((this["advertEditStatusActionsList"] as? JsonObject)?.get("contextLinkList") as? JsonObject)
            ?.get("contextLink") as? JsonArray
        return links.orEmpty().mapNotNull { element ->
            val link = element as? JsonObject ?: return@mapNotNull null
            val action = link.text("id")?.let(WillhabenStatusAction::ofLinkId) ?: return@mapNotNull null
            WillhabenStatusActionLink(
                action = action,
                serviceName = link.text("serviceName") ?: return@mapNotNull null,
                relativePath = link.text("relativePath") ?: return@mapNotNull null,
            )
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
