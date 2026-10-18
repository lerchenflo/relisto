package com.lerchenflo.willhabenupdater.listings.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.EmptyResult
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.database.ListingDao
import com.lerchenflo.willhabenupdater.datasource.database.ListingImageDao
import com.lerchenflo.willhabenupdater.datasource.database.PublicationDao
import com.lerchenflo.willhabenupdater.listings.domain.AddImagesResult
import com.lerchenflo.willhabenupdater.listings.domain.FailedImage
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.Listing
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.SyncState
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Listings in Room plus their image files in [ImageStorage]. */
class RoomListingRepository(
    private val listingDao: ListingDao,
    private val imageDao: ListingImageDao,
    private val publicationDao: PublicationDao,
    private val imageStorage: ImageStorage,
    private val timeProvider: TimeProvider,
) : ListingRepository {

    override fun getListingsAsFlow(): Flow<List<Listing>> {
        return listingDao.getAllAsFlow().map { rows -> rows.map { it.toListing(imageStorage::absolutePath) } }
    }

    override fun getListingByIdAsFlow(id: Long): Flow<Listing?> {
        return listingDao.getByIdAsFlow(id).map { it?.toListing(imageStorage::absolutePath) }
    }

    override suspend fun createListing(content: ListingContent): Result<Long, DataError.Local> = local {
        val now = timeProvider.nowEpochMillis()
        listingDao.insert(
            ListingEntity(
                title = content.title,
                description = content.description,
                priceCents = content.priceCents,
                condition = content.condition?.name,
                postcode = content.postcode,
                archived = false,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun updateContent(id: Long, content: ListingContent): EmptyResult<DataError.Local> = local {
        val current = listingDao.getById(id)?.listing ?: return Result.Error(DataError.Local.NOT_FOUND)
        val updated = current.copy(
            title = content.title,
            description = content.description,
            priceCents = content.priceCents,
            condition = content.condition?.name,
            postcode = content.postcode,
        )
        if (updated != current) {
            listingDao.update(updated.copy(updatedAt = timeProvider.nowEpochMillis()))
            markPublicationsChanged(id)
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): EmptyResult<DataError.Local> = local {
        val current = listingDao.getById(id)?.listing ?: return Result.Error(DataError.Local.NOT_FOUND)
        listingDao.update(current.copy(archived = archived, updatedAt = timeProvider.nowEpochMillis()))
    }

    override suspend fun duplicateListing(id: Long): Result<Long, DataError.Local> = local {
        val source = listingDao.getById(id) ?: return Result.Error(DataError.Local.NOT_FOUND)
        val now = timeProvider.nowEpochMillis()
        val newId = listingDao.insert(source.listing.copy(id = 0, archived = false, createdAt = now, updatedAt = now))
        imageDao.insertAll(source.images.map { it.copy(id = 0, listingId = newId) })
        // The copy keeps its marketplace fields but is a new, unpublished listing everywhere
        publicationDao.getByListingId(id).forEach { publication ->
            publicationDao.insert(
                publication.copy(
                    id = 0,
                    listingId = newId,
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
            )
        }
        newId
    }

    override suspend fun deleteListing(id: Long): EmptyResult<DataError.Local> = local {
        val current = listingDao.getById(id)?.listing ?: return Result.Error(DataError.Local.NOT_FOUND)
        listingDao.delete(current)
        deleteUnusedFiles()
    }

    override suspend fun addImages(
        listingId: Long,
        sourcePaths: List<String>
    ): Result<AddImagesResult, DataError.Local> = local {
        val existing = imageDao.getByListingId(listingId)
        val knownHashes = existing.map { it.sha256 }.toMutableSet()
        var nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val failed = mutableListOf<FailedImage>()
        var added = 0

        sourcePaths.forEach { path ->
            when (val stored = imageStorage.importImage(path)) {
                is Result.Error -> failed += FailedImage(path, stored.error)
                is Result.Success -> if (knownHashes.add(stored.data.sha256)) {
                    imageDao.insert(
                        ListingImageEntity(
                            listingId = listingId,
                            position = nextPosition++,
                            fileName = stored.data.fileName,
                            sha256 = stored.data.sha256,
                            width = stored.data.width,
                            height = stored.data.height,
                        )
                    )
                    added++
                }
            }
        }
        if (added > 0) touchListing(listingId)
        AddImagesResult(addedCount = added, failedFiles = failed)
    }

    override suspend fun removeImage(imageId: Long): EmptyResult<DataError.Local> = local {
        val image = imageDao.getById(imageId) ?: return Result.Error(DataError.Local.NOT_FOUND)
        imageDao.delete(image)
        val remaining = imageDao.getByListingId(image.listingId)
        imageDao.updateAll(remaining.mapIndexed { index, entity -> entity.copy(position = index) })
        touchListing(image.listingId)
        deleteUnusedFiles()
    }

    override suspend fun reorderImages(
        listingId: Long,
        orderedImageIds: List<Long>
    ): EmptyResult<DataError.Local> = local {
        val imagesById = imageDao.getByListingId(listingId).associateBy { it.id }
        val ordered = orderedImageIds.mapNotNull { imagesById[it] } +
            imagesById.values.filter { it.id !in orderedImageIds }
        imageDao.updateAll(ordered.mapIndexed { index, entity -> entity.copy(position = index) })
        touchListing(listingId)
    }

    override suspend fun deleteUnusedImageFiles(): EmptyResult<DataError.Local> = local {
        deleteUnusedFiles()
    }

    private suspend fun deleteUnusedFiles() {
        val referenced = imageDao.getAllFileNames().toSet()
        imageStorage.listFileNames()
            .filter { it !in referenced }
            .forEach { imageStorage.deleteFile(it) }
    }

    private suspend fun touchListing(listingId: Long) {
        val current = listingDao.getById(listingId)?.listing ?: return
        listingDao.update(current.copy(updatedAt = timeProvider.nowEpochMillis()))
        markPublicationsChanged(listingId)
    }

    /** Published listings now differ from what the marketplace shows. */
    private suspend fun markPublicationsChanged(listingId: Long) {
        publicationDao.getByListingId(listingId)
            .filter { it.syncState == SyncState.SYNCED.name }
            .forEach { publicationDao.update(it.copy(syncState = SyncState.LOCAL_CHANGES.name)) }
    }

    /** Runs a database operation and maps unexpected exceptions to [DataError.Local]. */
    private inline fun <T> local(block: () -> T): Result<T, DataError.Local> {
        return try {
            Result.Success(block())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            val diskFull = e.message?.contains("full", ignoreCase = true) == true
            Result.Error(if (diskFull) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
        }
    }
}
