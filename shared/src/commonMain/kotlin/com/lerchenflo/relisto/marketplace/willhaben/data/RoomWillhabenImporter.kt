package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.listings.domain.ImageDownloader
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.WillhabenFieldsDto
import com.lerchenflo.relisto.marketplace.willhaben.domain.ImportSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.ImportedListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.first

/**
 * Brings the user's willhaben marketplace listings into the local database:
 * - new on willhaben: new local listing with images, state SYNCED;
 * - changed on willhaben, not edited locally: local content follows willhaben;
 * - changed on both sides: local content stays, state CONFLICT;
 * - unchanged: only status and expiry are refreshed.
 */
class RoomWillhabenImporter(
    private val source: WillhabenListingsSource,
    private val catalogRepository: WillhabenCatalogRepository,
    private val listingRepository: ListingRepository,
    private val publicationDao: PublicationDao,
    private val imageDownloader: ImageDownloader,
    private val timeProvider: TimeProvider,
) : WillhabenImporter {

    override suspend fun importMyListings(): Result<ImportSummary, DataError> {
        val fetchedListings = when (val fetched = source.fetchMyListings()) {
            is Result.Error -> return fetched
            is Result.Success -> fetched.data
        }
        // The old listing of a republish stays on willhaben as expired; it is not imported again
        val replacedIds = publicationDao.getAll()
            .flatMap { it.replacedRemoteIds?.split(REMOTE_ID_SEPARATOR).orEmpty() }
            .toSet()
        val remoteListings = fetchedListings.filter { it.summary.remoteId !in replacedIds }
        val tree = (catalogRepository.getCategoryTree() as? Result.Success)?.data
        val categoryPathOf: (Int) -> List<WillhabenCategoryRef>? = { treeId ->
            tree?.pathOf(treeId)?.map { WillhabenCategoryRef(treeId = it.treeId, code = it.code, label = it.label) }
        }

        var created = 0
        var updated = 0
        remoteListings.forEach { remote ->
            val imported = WillhabenListingMapper.toImportedListing(remote, categoryPathOf) ?: return@forEach
            val existing = publicationDao.getByMarketplaceAndRemoteId(MARKETPLACE, imported.remoteId)
            if (existing == null) {
                when (val result = createLocal(imported)) {
                    is Result.Error -> return result
                    is Result.Success -> created++
                }
            } else if (refreshLocal(existing, imported)) {
                updated++
            }
        }
        return Result.Success(ImportSummary(found = remoteListings.size, created = created, updated = updated))
    }

    private suspend fun createLocal(imported: ImportedListing): Result<Unit, DataError.Local> {
        val listingId = when (val result = listingRepository.createListing(imported.content)) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        publicationDao.insert(
            ListingPublicationEntity(
                listingId = listingId,
                marketplace = MARKETPLACE,
                marketplaceFieldsJson = imported.fieldsJson(),
                remoteId = imported.remoteId,
                remoteUuid = imported.remoteUuid,
                remoteUrl = "https://www.willhaben.at/iad/object?adId=${imported.remoteId}",
                remoteStatus = imported.remoteStatus,
                expiresAt = imported.expiresAt,
                remoteSnapshotJson = imported.snapshotJson,
                remoteSnapshotHash = imported.contentHash(),
                syncState = SyncState.SYNCED.name,
                lastSyncedAt = timeProvider.nowEpochMillis(),
                lastError = null,
            )
        )
        addImages(listingId, imported.imageUrls)
        return Result.Success(Unit)
    }

    private suspend fun addImages(listingId: Long, urls: List<String>) {
        // A missing image must not stop the import
        val images = urls.mapNotNull { url -> (imageDownloader.download(url) as? Result.Success)?.data }
        if (images.isNotEmpty()) listingRepository.addImageBytes(listingId, images)
    }

    /** Returns true when the local content was replaced by the willhaben content. */
    private suspend fun refreshLocal(existing: ListingPublicationEntity, imported: ImportedListing): Boolean {
        val hash = imported.contentHash()
        val remoteChanged = existing.remoteSnapshotHash != hash
        val editedLocally = existing.syncState != SyncState.SYNCED.name && existing.syncState != SyncState.REMOTE_CHANGED.name
        val takeRemote = remoteChanged && !editedLocally
        if (takeRemote) listingRepository.updateContent(existing.listingId, imported.content)
        // Listings imported before their images could be read get them now; existing images stay as they are
        val hasNoImages = listingRepository.getListingByIdAsFlow(existing.listingId).first()?.images?.isEmpty() == true
        if (hasNoImages && imported.imageUrls.isNotEmpty()) addImages(existing.listingId, imported.imageUrls)

        // Written after updateContent, which marks synced publications as changed
        publicationDao.update(
            existing.copy(
                marketplaceFieldsJson = if (takeRemote) imported.fieldsJson() else existing.marketplaceFieldsJson,
                remoteUuid = imported.remoteUuid ?: existing.remoteUuid,
                remoteStatus = imported.remoteStatus,
                expiresAt = imported.expiresAt,
                remoteSnapshotJson = imported.snapshotJson,
                remoteSnapshotHash = hash,
                syncState = when {
                    takeRemote -> SyncState.SYNCED.name
                    remoteChanged -> SyncState.CONFLICT.name
                    else -> existing.syncState
                },
                lastSyncedAt = timeProvider.nowEpochMillis(),
            )
        )
        return takeRemote
    }

    private fun ImportedListing.fieldsJson(): String = AppJson.encodeToString(WillhabenFieldsDto.serializer(), fields.toDto())

    private fun ImportedListing.contentHash(): String = willhabenContentHash(content, fields)

    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
    }
}

/** Separates the ids in `replacedRemoteIds`. */
internal const val REMOTE_ID_SEPARATOR = ","
