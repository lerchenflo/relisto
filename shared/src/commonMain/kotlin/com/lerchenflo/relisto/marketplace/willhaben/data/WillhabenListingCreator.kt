package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.listings.domain.imagesFingerprint
import com.lerchenflo.relisto.listings.domain.toContent
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.MissingField
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCreator
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Publishes a local listing as a new free private marketplace listing, like the web client's
 * "Anzeige aufgeben": free product, empty listing of that product, local content patched in,
 * `presave` (the draft id is stored at once, so an interrupted publish continues with it), then the
 * shared finish steps. Paid products are never bought.
 */
class WillhabenListingCreator(
    private val catalogDataSource: KtorWillhabenCatalogDataSource,
    private val listingsDataSource: KtorWillhabenListingsDataSource,
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val finisher: WillhabenDraftFinisher,
    private val runner: WillhabenRequestRunner,
    private val listingRepository: ListingRepository,
    private val fieldsDataSource: WillhabenFieldsLocalDataSource,
    private val publicationDao: PublicationDao,
    private val activityLog: ActivityLog,
    private val timeProvider: TimeProvider,
) : WillhabenCreator {

    override suspend fun publish(listingId: Long, onStep: (PublishStep) -> Unit): EmptyResult<PublishError> {
        val publication = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
        if (publication?.remoteId != null && publication.syncState !in REPUBLISHABLE_STATES) {
            return Result.Error(PublishError.AlreadyPublished)
        }
        val listing = listingRepository.getListingByIdAsFlow(listingId).first() ?: return Result.Error(PublishError.NotPublished)
        val content = listing.toContent()
        val fields = fieldsDataSource.getFields(listingId) ?: WillhabenListingFields()
        missingFields(content, fields).takeIf { it.isNotEmpty() }?.let { return Result.Error(PublishError.Incomplete(it)) }

        val result = create(listingId, publication, content, fields, onStep)
        activityLog.record(
            listingId = listingId,
            marketplace = Marketplace.WILLHABEN,
            action = ActivityAction.PUBLISH,
            success = result is Result.Success,
            message = (result as? Result.Error)?.error?.toString(),
        )
        return result
    }

    private suspend fun create(
        listingId: Long,
        publication: ListingPublicationEntity?,
        content: ListingContent,
        fields: WillhabenListingFields,
        onStep: (PublishStep) -> Unit,
    ): EmptyResult<PublishError> {
        onStep(PublishStep.PRODUCT)
        val productId = runner.step(PublishStep.PRODUCT) { catalogDataSource.fetchFreeProductId(MARKETPLACE_AD_TYPE_ID) }
            .valueOr { return it } ?: return Result.Error(PublishError.PaidProduct())

        // An interrupted publish continues with its draft, unless the draft is gone on willhaben
        val interruptedDraft = publication?.remoteId
            ?.takeIf { publication.syncState == SyncState.PUBLISHING.name }
            ?.let { draftId -> finisher.loadDraft(draftId, MARKETPLACE_AD_TYPE_ID).valueOr { return it } }
        val draft = if (interruptedDraft != null) {
            withProduct(AdJsonPatcher.patch(interruptedDraft, content, fields, withCategory = true), productId)
        } else {
            createDraft(listingId, content, fields, productId, onStep).valueOr { return it }
        }

        val images = listingRepository.getListingByIdAsFlow(listingId).first()?.images.orEmpty()
        val finished = finisher.finishAndActivate(draft, images, MARKETPLACE_AD_TYPE_ID, onStep).valueOr { return it }
        storePublished(listingId, finished, content, fields, images.map { it.sha256 }.imagesFingerprint())
        return Result.Success(Unit)
    }

    /** The empty listing of the free product with the local content, presaved as a draft. */
    private suspend fun createDraft(
        listingId: Long,
        content: ListingContent,
        fields: WillhabenListingFields,
        productId: Int,
        onStep: (PublishStep) -> Unit,
    ): Result<JsonObject, PublishError> {
        onStep(PublishStep.TEMPLATE)
        val templateJson = runner.step(PublishStep.TEMPLATE) { listingsDataSource.fetchTemplateJson(productId) }.valueOr { return it }
        val template = AppJson.parseToJsonElement(templateJson) as JsonObject
        val patched = withProduct(AdJsonPatcher.patch(template, content, fields, withCategory = true), productId)

        onStep(PublishStep.PRESAVE)
        val presaveLink = ContextLinks.find(patched, "presave") ?: return Result.Error(PublishError.MissingLink("presave"))
        val presaved = runner.step(PublishStep.PRESAVE) { writeDataSource.post(presaveLink.route, patched) }.valueOr { return it }
        val draft = patched.mergeIds(presaved)

        val draftId = (draft["adId"] as? JsonPrimitive)?.contentOrNull ?: return Result.Error(PublishError.MissingLink("adId"))
        // Stored at once: a failed step later leaves a draft the next publish continues
        val current = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
        val publishing = (current ?: newPublication(listingId)).copy(
            remoteId = draftId,
            remoteUuid = (draft["adUuid"] as? JsonPrimitive)?.contentOrNull,
            remoteUrl = remoteUrl(draftId),
            remoteStatus = null,
            expiresAt = null,
            remoteActionsJson = null,
            syncState = SyncState.PUBLISHING.name,
        )
        if (current == null) publicationDao.insert(publishing) else publicationDao.update(publishing)
        return Result.Success(draft)
    }

    private suspend fun storePublished(
        listingId: Long,
        finished: FinishedListing,
        content: ListingContent,
        fields: WillhabenListingFields,
        imagesFingerprint: String,
    ) {
        val remote = RemoteListing(
            summary = RemoteListingSummary(remoteId = finished.remoteId, remoteUuid = null, adTypeId = fields.adTypeId, title = content.title),
            detailJson = finished.reloadedJson,
        )
        val imported = WillhabenListingMapper.toImportedListing(remote) { treeId ->
            fields.categoryPath.takeIf { it.lastOrNull()?.treeId == treeId }
        }
        val current = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE) ?: return
        publicationDao.update(
            current.copy(
                remoteId = finished.remoteId,
                remoteUuid = imported?.remoteUuid ?: current.remoteUuid,
                remoteUrl = remoteUrl(finished.remoteId),
                remoteStatus = null,
                expiresAt = imported?.expiresAt,
                remoteSnapshotJson = finished.reloadedJson,
                remoteSnapshotHash = imported?.let { willhabenContentHash(it.content, it.fields) },
                syncState = SyncState.SYNCED.name,
                syncedImagesHash = imagesFingerprint,
                lastSyncedAt = timeProvider.nowEpochMillis(),
                lastError = null,
            )
        )
    }

    private fun missingFields(content: ListingContent, fields: WillhabenListingFields): List<MissingField> = buildList {
        if (content.title.isBlank()) add(MissingField.TITLE)
        if (fields.categoryPath.isEmpty()) add(MissingField.CATEGORY)
        if (fields.locationId == null) add(MissingField.LOCATION)
    }

    private fun withProduct(json: JsonObject, productId: Int) = JsonObject(json + ("productId" to JsonPrimitive(productId)))

    private fun newPublication(listingId: Long) = ListingPublicationEntity(
        listingId = listingId,
        marketplace = MARKETPLACE,
        marketplaceFieldsJson = null,
        remoteId = null,
        remoteUuid = null,
        remoteUrl = null,
        remoteStatus = null,
        expiresAt = null,
        remoteSnapshotJson = null,
        remoteSnapshotHash = null,
        syncState = SyncState.LOCAL_ONLY.name,
        lastSyncedAt = null,
        lastError = null,
    )

    private fun remoteUrl(adId: String) = "https://www.willhaben.at/iad/object?adId=$adId"

    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
        /** A gone listing is published as a new one; an interrupted publish continues. */
        val REPUBLISHABLE_STATES = setOf(SyncState.PUBLISHING.name, SyncState.REMOTE_GONE.name, SyncState.LOCAL_ONLY.name)
    }
}
