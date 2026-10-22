package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.ListingImage
import com.lerchenflo.relisto.marketplace.willhaben.domain.PaidPackage
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

/** A listing willhaben accepted for publishing; [reloadedJson] is its JSON right before activation. */
data class FinishedListing(val remoteId: String, val reloadedJson: String)

/**
 * The last steps of publishing a saved draft, shared by renew and publish: upload the local images,
 * finish (save) the draft, choose the free package where willhaben asks for one, stop when willhaben
 * wants a payment, and activate. Paid products are never bought.
 */
class WillhabenDraftFinisher(
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val listingsDataSource: KtorWillhabenListingsDataSource,
    private val imageUploader: WillhabenImageUploader,
    private val runner: WillhabenRequestRunner,
) {

    /** [draft] holds the local content and the ids and links of the presaved draft. */
    suspend fun finishAndActivate(
        draft: JsonObject,
        images: List<ListingImage>,
        adTypeId: Int?,
        onStep: (PublishStep) -> Unit = {},
    ): Result<FinishedListing, PublishError> {
        if (images.isNotEmpty()) {
            onStep(PublishStep.IMAGES)
            val imageList = ContextLinks.find(draft, "imageList") ?: return Result.Error(PublishError.MissingLink("imageList"))
            imageUploader.replaceImages(images, imageList)?.let { return Result.Error(it) }
        }

        onStep(PublishStep.FINISH)
        val finishLink = ContextLinks.find(draft, "selfLinkFinish") ?: ContextLinks.find(draft, "selfLinkSave")
            ?: return Result.Error(PublishError.MissingLink("selfLinkFinish"))
        runner.step(PublishStep.FINISH) { writeDataSource.post(finishLink.route, draft) }.valueOr { return it }

        onStep(PublishStep.RELOAD)
        val newId = (draft["adId"] as? JsonPrimitive)?.contentOrNull ?: return Result.Error(PublishError.MissingLink("adId"))
        val reloadRoute = WillhabenAdTypes.detailRoute(newId, adTypeId) ?: return Result.Error(PublishError.MissingLink("adId"))
        var reloadedJson = runner.step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(reloadRoute) }.valueOr { return it }
        var reloaded = AppJson.parseToJsonElement(reloadedJson) as JsonObject
        if (choosePackage(reloaded, onStep).valueOr { return it }) {
            reloadedJson = runner.step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(reloadRoute) }.valueOr { return it }
            reloaded = AppJson.parseToJsonElement(reloadedJson) as JsonObject
        }
        if (ContextLinks.find(reloaded, "paymentURI") != null) return Result.Error(PublishError.PaidProduct())

        onStep(PublishStep.ACTIVATE)
        val activateLink = ContextLinks.find(reloaded, "selfLinkActivate")
            ?: return Result.Error(PublishError.MissingLink("selfLinkActivate"))
        runner.step(PublishStep.ACTIVATE) { writeDataSource.put(activateLink.route) }.valueOr { return it }
        return Result.Success(FinishedListing(remoteId = newId, reloadedJson = reloadedJson))
    }

    /** The draft's JSON; null when the draft is gone on willhaben. */
    suspend fun loadDraft(draftId: String, adTypeId: Int?): Result<JsonObject?, PublishError> {
        val route = WillhabenAdTypes.detailRoute(draftId, adTypeId) ?: return Result.Success(null)
        return when (val result = runner.run { listingsDataSource.fetchListingJson(route) }) {
            is Result.Success -> Result.Success(AppJson.parseToJsonElement(result.data) as JsonObject)
            is Result.Error ->
                if (result.error == DataError.Network.NOT_FOUND) Result.Success(null)
                else Result.Error(PublishError.Failed(PublishStep.RELOAD, result.error))
        }
    }

    /**
     * Listings that offer packages (Auto & Motor: "Basic", "Turbo", ...) need exactly one chosen before
     * activation, like the web client's "Pakete" step. Only a free package is ever chosen.
     * Returns true when a package was chosen now, so the listing has to be reloaded.
     */
    private suspend fun choosePackage(listing: JsonObject, onStep: (PublishStep) -> Unit): Result<Boolean, PublishError> {
        val groups = ((listing["upsellingProductList"] as? JsonObject)?.get("upsellingProducts") as? JsonArray)
            .orEmpty().mapNotNull { it as? JsonObject }
        val bundles = groups.filter { (it["isBundle"] as? JsonPrimitive)?.booleanOrNull == true }
        if (bundles.isEmpty()) return Result.Success(false)

        val products = bundles.flatMap { (it["upsellingProducts"] as? JsonArray).orEmpty().mapNotNull { p -> p as? JsonObject } }
        val freeProduct = products.firstOrNull { it.price() == 0.0 }
            ?: return Result.Error(PublishError.PaidProduct(cheapestPackage(bundles)))
        val freeId = freeProduct["productId"]
        val selected = products.filter { (it["selected"] as? JsonPrimitive)?.booleanOrNull == true }
        if (selected.size == 1 && selected.single()["productId"] == freeId) return Result.Success(false)

        onStep(PublishStep.PACKAGE)
        val link = ContextLinks.find(listing, "upsellings") ?: return Result.Error(PublishError.MissingLink("upsellings"))
        val body = buildJsonObject {
            put("upsellingProducts", buildJsonArray {
                groups.forEach { group ->
                    val groupProducts = (group["upsellingProducts"] as? JsonArray).orEmpty().map { product ->
                        val productObject = product as? JsonObject ?: return@map product
                        JsonObject(productObject + ("selected" to JsonPrimitive(productObject["productId"] == freeId)))
                    }
                    add(JsonObject(group + ("upsellingProducts" to JsonArray(groupProducts))))
                }
            })
        }
        runner.step(PublishStep.PACKAGE) { writeDataSource.put(link.route, body) }.valueOr { return it }
        return Result.Success(true)
    }

    /** The cheapest package of the bundle groups, named by its group ("Basic - 14 Tage"). */
    private fun cheapestPackage(bundles: List<JsonObject>): PaidPackage? =
        bundles.flatMap { group ->
            val name = (group["title"] as? JsonPrimitive)?.contentOrNull ?: return@flatMap emptyList()
            (group["upsellingProducts"] as? JsonArray).orEmpty().mapNotNull { product ->
                (product as? JsonObject)?.price()?.let { PaidPackage(name, (it * 100).roundToLong()) }
            }
        }.minByOrNull { it.priceCents }

    private fun JsonObject.price(): Double? = (this["price"] as? JsonPrimitive)?.doubleOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
