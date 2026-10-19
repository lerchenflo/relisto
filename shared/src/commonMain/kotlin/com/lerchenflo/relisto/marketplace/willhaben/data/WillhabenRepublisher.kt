package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.listings.domain.Listing
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.domain.WILLHABEN_DRAFT_STATUS
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenPublisher
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * "Neu veröffentlichen" like the willhaben web app: copies the expired listing into a new draft,
 * writes the local content into it, uploads the local images, finishes and activates it.
 * Works for every ad type with a free product (marketplace, Auto & Motor).
 * Paid products are never bought: ad types without a free product are refused before anything
 * is written, and the flow stops when willhaben asks for a payment.
 */
class WillhabenRepublisher(
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val listingsDataSource: KtorWillhabenListingsDataSource,
    private val catalogDataSource: KtorWillhabenCatalogDataSource,
    private val runner: WillhabenRequestRunner,
    private val listingRepository: ListingRepository,
    private val fieldsDataSource: WillhabenFieldsLocalDataSource,
    private val publicationDao: PublicationDao,
    private val imageStorage: ImageStorage,
    private val timeProvider: TimeProvider,
) : WillhabenPublisher {

    override suspend fun republish(listingId: Long): EmptyResult<PublishError> {
        val publication = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
        val remoteId = publication?.remoteId ?: return Result.Error(PublishError.NotPublished)
        val listing = listingRepository.getListingByIdAsFlow(listingId).first() ?: return Result.Error(PublishError.NotPublished)
        val content = listing.toContent()
        val fields = fieldsDataSource.getFields(listingId) ?: WillhabenListingFields()
        val adTypeId = fields.adTypeId ?: MARKETPLACE_AD_TYPE_ID

        // Like the web client, the copy always gets the product for its ad type (here: the free one)
        val freeProductId = step(PublishStep.PRODUCT) { catalogDataSource.fetchFreeProductId(adTypeId) }
            .valueOr { return it } ?: return Result.Error(PublishError.PaidProduct)

        val saved = if (publication.remoteStatus == WILLHABEN_DRAFT_STATUS) {
            // A draft (e.g. left by a renew whose activation failed) is finished, not copied again
            val draft = loadDraft(remoteId, adTypeId).valueOr { return it } ?: return Result.Error(PublishError.NotPublished)
            JsonObject(AdJsonPatcher.patch(draft, content, fields) + ("productId" to JsonPrimitive(freeProductId)))
        } else {
            createDraft(publication, remoteId, adTypeId, content, fields, freeProductId).valueOr { return it }
        }

        uploadImages(listing, saved)?.let { return Result.Error(it) }

        val finishLink = ContextLinks.find(saved, "selfLinkFinish") ?: ContextLinks.find(saved, "selfLinkSave")
            ?: return Result.Error(PublishError.MissingLink("selfLinkFinish"))
        step(PublishStep.FINISH) { writeDataSource.post(finishLink.route, saved) }.valueOr { return it }

        val newId = (saved["adId"] as? JsonPrimitive)?.content ?: return Result.Error(PublishError.MissingLink("adId"))
        val reloadRoute = WillhabenAdTypes.detailRoute(newId, adTypeId) ?: return Result.Error(PublishError.MissingLink("adId"))
        var reloadedJson = step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(reloadRoute) }.valueOr { return it }
        var reloaded = AppJson.parseToJsonElement(reloadedJson) as JsonObject
        if (choosePackage(reloaded).valueOr { return it }) {
            reloadedJson = step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(reloadRoute) }.valueOr { return it }
            reloaded = AppJson.parseToJsonElement(reloadedJson) as JsonObject
        }
        if (ContextLinks.find(reloaded, "paymentURI") != null) return Result.Error(PublishError.PaidProduct)

        val activateLink = ContextLinks.find(reloaded, "selfLinkActivate")
            ?: return Result.Error(PublishError.MissingLink("selfLinkActivate"))
        step(PublishStep.ACTIVATE) { writeDataSource.put(activateLink.route) }.valueOr { return it }

        storeNewPublication(listingId, newId, reloadedJson, content, fields)
        return Result.Success(Unit)
    }

    /**
     * Copies the expired listing into a new draft and presaves it. willhaben replaces the expired listing
     * by the draft, so the publication follows it at once: a failed step later leaves a draft to finish.
     */
    private suspend fun createDraft(
        publication: ListingPublicationEntity,
        remoteId: String,
        adTypeId: Int,
        content: ListingContent,
        fields: WillhabenListingFields,
        freeProductId: Int,
    ): Result<JsonObject, PublishError> {
        val copyRoute = WillhabenAdTypes.copyRoute(remoteId, adTypeId) ?: return Result.Error(PublishError.MissingLink("copy"))
        val copy = step(PublishStep.COPY) { writeDataSource.post(copyRoute) }.valueOr { return it }
        val draft = JsonObject(AdJsonPatcher.patch(copy, content, fields) + ("productId" to JsonPrimitive(freeProductId)))

        val presaveLink = ContextLinks.find(draft, "presave") ?: return Result.Error(PublishError.MissingLink("presave"))
        val presaved = step(PublishStep.PRESAVE) { writeDataSource.post(presaveLink.route, draft) }.valueOr { return it }
        val saved = draft.mergeIds(presaved)

        (saved["adId"] as? JsonPrimitive)?.content?.let { draftId ->
            // Written directly: following the draft is no local change of the listing
            publicationDao.update(
                publication.copy(
                    remoteId = draftId,
                    replacedRemoteIds = listOfNotNull(publication.replacedRemoteIds, remoteId).joinToString(REMOTE_ID_SEPARATOR),
                    remoteStatus = WILLHABEN_DRAFT_STATUS,
                    remoteUrl = "https://www.willhaben.at/iad/object?adId=$draftId",
                    expiresAt = null,
                    remoteActionsJson = null,
                )
            )
        }
        return Result.Success(saved)
    }

    /** The draft's JSON; null when the draft is gone on willhaben. */
    private suspend fun loadDraft(draftId: String, adTypeId: Int): Result<JsonObject?, PublishError> {
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
    private suspend fun choosePackage(listing: JsonObject): Result<Boolean, PublishError> {
        val groups = ((listing["upsellingProductList"] as? JsonObject)?.get("upsellingProducts") as? JsonArray)
            .orEmpty().mapNotNull { it as? JsonObject }
        val bundles = groups.filter { (it["isBundle"] as? JsonPrimitive)?.booleanOrNull == true }
        if (bundles.isEmpty()) return Result.Success(false)

        val products = bundles.flatMap { (it["upsellingProducts"] as? JsonArray).orEmpty().mapNotNull { p -> p as? JsonObject } }
        val freeProduct = products.firstOrNull { it.price() == 0.0 } ?: return Result.Error(PublishError.PaidProduct)
        val freeId = freeProduct["productId"]
        val selected = products.filter { (it["selected"] as? JsonPrimitive)?.booleanOrNull == true }
        if (selected.size == 1 && selected.single()["productId"] == freeId) return Result.Success(false)

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
        step(PublishStep.PACKAGE) { writeDataSource.put(link.route, body) }.valueOr { return it }
        return Result.Success(true)
    }

    private fun JsonObject.price(): Double? = (this["price"] as? JsonPrimitive)?.doubleOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()


    /** Replaces the images of the draft with the local images; returns the error, null on success. */
    private suspend fun uploadImages(listing: Listing, draft: JsonObject): PublishError? {
        if (listing.images.isEmpty()) return null
        val imageList = ContextLinks.find(draft, "imageList") ?: return PublishError.MissingLink("imageList")
        val references = listing.images.sortedBy { it.position }.map { image ->
            val bytes = imageStorage.readBytes(image.filePath)
                ?: return PublishError.Failed(PublishStep.IMAGES, DataError.Local.NOT_FOUND)
            val fileName = image.filePath.substringAfterLast('/').substringAfterLast('\\')
            step(PublishStep.IMAGES) { writeDataSource.uploadTempImage(bytes, fileName) }.valueOr { return it.error }
        }
        val order = buildJsonObject {
            put("advertImage", buildJsonArray { references.forEach { add(buildJsonObject { put("reference", it) }) } })
            put("floorPlans", JsonArray(emptyList()))
        }
        // Image endpoints live on the upload service, the link names the normal one
        val route = "/iad-upload${imageList.relativePath}/order-and-associate"
        step(PublishStep.IMAGES) { writeDataSource.put(route, order) }.valueOr { return it.error }
        return null
    }

    private suspend fun storeNewPublication(
        listingId: Long,
        newId: String,
        reloadedJson: String,
        content: ListingContent,
        fields: WillhabenListingFields,
    ) {
        // Title and ad type are the ones just sent, for JSON that does not carry them (other verticals)
        val remote = RemoteListing(
            summary = RemoteListingSummary(remoteId = newId, remoteUuid = null, adTypeId = fields.adTypeId, title = content.title),
            detailJson = reloadedJson,
        )
        // The category comes from the local fields, like the importer resolves it from the catalog
        val imported = WillhabenListingMapper.toImportedListing(remote) { treeId ->
            fields.categoryPath.takeIf { it.lastOrNull()?.treeId == treeId }
        }
        // Read again: the publication followed the draft since the start
        val old = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE) ?: return
        publicationDao.update(
            old.copy(
                remoteId = newId,
                remoteUuid = imported?.remoteUuid,
                remoteUrl = "https://www.willhaben.at/iad/object?adId=$newId",
                remoteStatus = null,
                remoteActionsJson = null,
                expiresAt = imported?.expiresAt,
                remoteSnapshotJson = reloadedJson,
                remoteSnapshotHash = imported?.let { willhabenContentHash(it.content, it.fields) },
                syncState = SyncState.SYNCED.name,
                lastSyncedAt = timeProvider.nowEpochMillis(),
                lastError = null,
            )
        )
    }

    private suspend fun <T> step(
        step: PublishStep,
        request: suspend () -> Result<T, DataError.Network>,
    ): Result<T, PublishError> = when (val result = runner.run(request)) {
        is Result.Error -> Result.Error(PublishError.Failed(step, result.error))
        is Result.Success -> result
    }

    private inline fun <T> Result<T, PublishError>.valueOr(onError: (Result.Error<PublishError>) -> Nothing): T =
        when (this) {
            is Result.Error -> onError(this)
            is Result.Success -> data
        }

    /** Takes the ids and links willhaben assigned to the draft. */
    private fun JsonObject.mergeIds(presaved: JsonObject): JsonObject =
        JsonObject(this + presaved.filterKeys { it in PRESAVE_KEYS && presaved[it] !is JsonNull })

    private fun Listing.toContent() = ListingContent(
        title = title,
        description = description,
        priceCents = priceCents,
        condition = condition,
        postcode = postcode,
    )

    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
        val PRESAVE_KEYS = setOf("adId", "adUuid", "productId", "contextLinkList")
    }
}
