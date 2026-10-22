package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.listings.domain.imagesFingerprint
import com.lerchenflo.relisto.listings.domain.toContent
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenUpdater
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * Edits a listing that is on willhaben, like the web client's edit form: reload the listing, check that
 * nobody changed it on willhaben since the last sync, patch the local content into the fresh JSON,
 * `PUT` it to `selfLinkEdit`, replace the images when they changed locally, and keep the answer as
 * the new snapshot. The category is not changed (willhaben converts listings for that).
 */
class WillhabenListingUpdater(
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val listingsDataSource: KtorWillhabenListingsDataSource,
    private val imageUploader: WillhabenImageUploader,
    private val runner: WillhabenRequestRunner,
    private val listingRepository: ListingRepository,
    private val fieldsDataSource: WillhabenFieldsLocalDataSource,
    private val publicationDao: PublicationDao,
    private val activityLog: ActivityLog,
    private val timeProvider: TimeProvider,
) : WillhabenUpdater {

    override suspend fun pushChanges(listingId: Long, overwriteRemote: Boolean): EmptyResult<PublishError> {
        val publication = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
        val remoteId = publication?.remoteId
        if (publication == null || remoteId == null || publication.syncState == SyncState.REMOTE_GONE.name) {
            return Result.Error(PublishError.NotPublished)
        }
        val result = push(publication, remoteId, overwriteRemote)
        if (result is Result.Error && result.error != PublishError.Conflict) {
            // The local changes stay to be sent again
            publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)?.let {
                publicationDao.update(it.copy(lastError = result.error.toString()))
            }
        }
        activityLog.record(
            listingId = listingId,
            marketplace = Marketplace.WILLHABEN,
            action = ActivityAction.PUSH_CHANGES,
            success = result is Result.Success,
            message = (result as? Result.Error)?.error?.toString(),
        )
        return result
    }

    private suspend fun push(
        publication: ListingPublicationEntity,
        remoteId: String,
        overwriteRemote: Boolean,
    ): EmptyResult<PublishError> {
        val listingId = publication.listingId
        val listing = listingRepository.getListingByIdAsFlow(listingId).first() ?: return Result.Error(PublishError.NotPublished)
        val content = listing.toContent()
        val fields = fieldsDataSource.getFields(listingId) ?: WillhabenListingFields()
        val route = WillhabenAdTypes.detailRoute(remoteId, fields.adTypeId) ?: return Result.Error(PublishError.MissingLink("adId"))

        val freshJson = runner.step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(route) }.valueOr { return it }
        if (!overwriteRemote && changedOnWillhaben(publication, freshJson, fields.adTypeId)) {
            publicationDao.update(publication.copy(remoteSnapshotJson = freshJson, syncState = SyncState.CONFLICT.name))
            return Result.Error(PublishError.Conflict)
        }
        val fresh = AppJson.parseToJsonElement(freshJson) as JsonObject

        val editLink = ContextLinks.find(fresh, "selfLinkEdit") ?: return Result.Error(PublishError.MissingLink("selfLinkEdit"))
        val patched = AdJsonPatcher.patch(fresh, content, fields)
        runner.step(PublishStep.EDIT) { writeDataSource.put(editLink.route, patched) }.valueOr { return it }

        val imagesFingerprint = listing.images.map { it.sha256 }.imagesFingerprint()
        if (imagesFingerprint != publication.syncedImagesHash) {
            val imageList = ContextLinks.find(fresh, "imageList") ?: return Result.Error(PublishError.MissingLink("imageList"))
            imageUploader.replaceImages(listing.images, imageList)?.let { return Result.Error(it) }
        }

        val reloadedJson = runner.step(PublishStep.RELOAD) { listingsDataSource.fetchListingJson(route) }.valueOr { return it }
        val reloaded = WillhabenListingMapper.toImportedListing(remoteListing(remoteId, fields.adTypeId, reloadedJson)) { treeId ->
            fields.categoryPath.takeIf { it.lastOrNull()?.treeId == treeId }
        }
        // Read again: edits made while the requests ran are kept as local changes
        val current = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE) ?: return Result.Success(Unit)
        val editedMeanwhile = listingRepository.getListingByIdAsFlow(listingId).first()?.let {
            it.toContent() != content || it.images.map { image -> image.sha256 }.imagesFingerprint() != imagesFingerprint
        } ?: false
        publicationDao.update(
            current.copy(
                remoteSnapshotJson = reloadedJson,
                remoteSnapshotHash = reloaded?.let { willhabenContentHash(it.content, it.fields) },
                expiresAt = reloaded?.expiresAt ?: current.expiresAt,
                syncState = if (editedMeanwhile) SyncState.LOCAL_CHANGES.name else SyncState.SYNCED.name,
                syncedImagesHash = imagesFingerprint,
                lastSyncedAt = timeProvider.nowEpochMillis(),
                lastError = null,
            )
        )
        return Result.Success(Unit)
    }

    /**
     * Compares what the app manages (text, price, attributes, location) in willhaben's current listing
     * with the snapshot of the last sync. Without a snapshot there is nothing to compare.
     */
    private fun changedOnWillhaben(publication: ListingPublicationEntity, freshJson: String, adTypeId: Int?): Boolean {
        val snapshot = publication.remoteSnapshotJson ?: return false
        val remoteId = publication.remoteId ?: return false
        fun managed(json: String) = WillhabenListingMapper.toImportedListing(remoteListing(remoteId, adTypeId, json)) { null }
            ?.let { it.content to it.fields }
        return managed(snapshot) != managed(freshJson)
    }

    private fun remoteListing(remoteId: String, adTypeId: Int?, json: String) = RemoteListing(
        summary = RemoteListingSummary(remoteId = remoteId, remoteUuid = null, adTypeId = adTypeId, title = null),
        detailJson = json,
    )


    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
    }
}
