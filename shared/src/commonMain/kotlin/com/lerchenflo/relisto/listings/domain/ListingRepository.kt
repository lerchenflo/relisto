package com.lerchenflo.relisto.listings.domain

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import kotlinx.coroutines.flow.Flow

/** Local listings: database rows plus the image files on disk. */
interface ListingRepository {
    fun getListingsAsFlow(): Flow<List<Listing>>
    fun getListingByIdAsFlow(id: Long): Flow<Listing?>

    suspend fun createListing(content: ListingContent): Result<Long, DataError.Local>
    suspend fun updateContent(id: Long, content: ListingContent): EmptyResult<DataError.Local>
    suspend fun setArchived(id: Long, archived: Boolean): EmptyResult<DataError.Local>
    suspend fun duplicateListing(id: Long): Result<Long, DataError.Local>
    suspend fun deleteListing(id: Long): EmptyResult<DataError.Local>

    /** Copies the files into the image store and appends them to the listing. */
    suspend fun addImages(listingId: Long, sourcePaths: List<String>): Result<AddImagesResult, DataError.Local>
    /** Stores downloaded images (bytes and file extension) and appends them; returns how many were added. */
    suspend fun addImageBytes(listingId: Long, images: List<Pair<ByteArray, String>>): Result<Int, DataError.Local>

    suspend fun removeImage(imageId: Long): EmptyResult<DataError.Local>

    /** Saves the given image order; the first id becomes the cover. */
    suspend fun reorderImages(listingId: Long, orderedImageIds: List<Long>): EmptyResult<DataError.Local>

    /** Deletes image files that no listing references any more. */
    suspend fun deleteUnusedImageFiles(): EmptyResult<DataError.Local>
}

data class AddImagesResult(
    val addedCount: Int,
    val failedFiles: List<FailedImage>,
)

data class FailedImage(
    val path: String,
    val error: ImageImportError,
)
