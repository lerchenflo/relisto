package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.listings.domain.imagesFingerprint
import com.lerchenflo.relisto.listings.domain.toContent
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * "Neu veröffentlichen" like the willhaben web app: copies the expired listing into a new draft,
 * writes the local content into it, uploads the local images, finishes and activates it.
 * Works for every ad type with a free product (marketplace, Auto & Motor).
 * Paid products are never bought: ad types without a free product are refused before anything
 * is written, and the flow stops when willhaben asks for a payment.
 */
class WillhabenRepublisher(
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val catalogDataSource: KtorWillhabenCatalogDataSource,
    private val runner: WillhabenRequestRunner,
    private val listingRepository: ListingRepository,
    private val fieldsDataSource: WillhabenFieldsLocalDataSource,
    private val publicationDao: PublicationDao,
    private val finisher: WillhabenDraftFinisher,
    private val activityLog: ActivityLog,
    private val timeProvider: TimeProvider,
) : WillhabenPublisher {

    override suspend fun republish(listingId: Long): EmptyResult<PublishError> {
        val result = renew(listingId)
        activityLog.record(
            listingId = listingId,
            marketplace = Marketplace.WILLHABEN,
            action = ActivityAction.REPUBLISH,
            success = result is Result.Success,
            message = (result as? Result.Error)?.error?.toString(),
        )
        return result
    }

    private suspend fun renew(listingId: Long): EmptyResult<PublishError> {
        val publication = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
        val remoteId = publication?.remoteId ?: return Result.Error(PublishError.NotPublished)
        val listing = listingRepository.getListingByIdAsFlow(listingId).first() ?: return Result.Error(PublishError.NotPublished)
        val content = listing.toContent()
        val fields = fieldsDataSource.getFields(listingId) ?: WillhabenListingFields()
        val adTypeId = fields.adTypeId ?: MARKETPLACE_AD_TYPE_ID

        // Like the web client, the copy always gets the product for its ad type (here: the free one)
        val freeProductId = runner.step(PublishStep.PRODUCT) { catalogDataSource.fetchFreeProductId(adTypeId) }
            .valueOr { return it } ?: return Result.Error(PublishError.PaidProduct())

        val saved = if (publication.remoteStatus == WILLHABEN_DRAFT_STATUS) {
            // A draft (e.g. left by a renew whose activation failed) is finished, not copied again
            val draft = finisher.loadDraft(remoteId, adTypeId).valueOr { return it } ?: return Result.Error(PublishError.NotPublished)
            JsonObject(AdJsonPatcher.patch(draft, content, fields) + ("productId" to JsonPrimitive(freeProductId)))
        } else {
            createDraft(publication, remoteId, adTypeId, content, fields, freeProductId).valueOr { return it }
        }

        val finished = finisher.finishAndActivate(saved, listing.images, adTypeId).valueOr { return it }
        storeNewPublication(listingId, finished.remoteId, finished.reloadedJson, content, fields, listing.images.map { it.sha256 }.imagesFingerprint())
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
        val copy = runner.step(PublishStep.COPY) { writeDataSource.post(copyRoute) }.valueOr { return it }
        val draft = JsonObject(AdJsonPatcher.patch(copy, content, fields) + ("productId" to JsonPrimitive(freeProductId)))

        val presaveLink = ContextLinks.find(draft, "presave") ?: return Result.Error(PublishError.MissingLink("presave"))
        val presaved = runner.step(PublishStep.PRESAVE) { writeDataSource.post(presaveLink.route, draft) }.valueOr { return it }
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

    private suspend fun storeNewPublication(
        listingId: Long,
        newId: String,
        reloadedJson: String,
        content: ListingContent,
        fields: WillhabenListingFields,
        imagesFingerprint: String,
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
                syncedImagesHash = imagesFingerprint,
                lastSyncedAt = timeProvider.nowEpochMillis(),
                lastError = null,
            )
        )
    }

    /** Takes the ids and links willhaben assigned to the draft. */
    private fun JsonObject.mergeIds(presaved: JsonObject): JsonObject =
        JsonObject(this + presaved.filterKeys { it in PRESAVE_KEYS && presaved[it] !is JsonNull })

    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
        val PRESAVE_KEYS = setOf("adId", "adUuid", "productId", "contextLinkList")
    }
}
