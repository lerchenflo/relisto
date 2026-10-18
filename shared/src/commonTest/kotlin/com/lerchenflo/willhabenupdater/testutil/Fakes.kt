package com.lerchenflo.willhabenupdater.testutil

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.EmptyResult
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.listings.domain.AddImagesResult
import com.lerchenflo.willhabenupdater.listings.domain.FailedImage
import com.lerchenflo.willhabenupdater.listings.domain.ImageImportError
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.Listing
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.listings.domain.ListingImage
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.willhabenupdater.settings.domain.AppSettings
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

fun listing(
    id: Long,
    title: String = "Listing $id",
    description: String = "",
    priceCents: Long? = null,
    archived: Boolean = false,
    postcode: String = "",
    images: List<ListingImage> = emptyList(),
    updatedAt: Long = id,
) = Listing(
    id = id,
    title = title,
    description = description,
    priceCents = priceCents,
    condition = null,
    postcode = postcode,
    archived = archived,
    createdAt = 0,
    updatedAt = updatedAt,
    images = images,
)

fun image(id: Long, listingId: Long, position: Int) = ListingImage(
    id = id,
    listingId = listingId,
    position = position,
    filePath = "/images/$id.jpg",
    width = 800,
    height = 600,
)

/** In-memory listings; sorted newest first like the real repository. */
class FakeListingRepository : ListingRepository {
    val listings = MutableStateFlow<List<Listing>>(emptyList())
    val updatedContents = mutableListOf<Pair<Long, ListingContent>>()
    val addedImagePaths = mutableListOf<List<String>>()
    val removedImageIds = mutableListOf<Long>()
    val reorders = mutableListOf<List<Long>>()
    val deletedIds = mutableListOf<Long>()
    var cleanupCalls = 0
    var failingImagePaths = setOf<String>()
    var createError: DataError.Local? = null
    private var nextId = 100L

    override fun getListingsAsFlow(): Flow<List<Listing>> = listings.map { all -> all.sortedByDescending { it.updatedAt } }

    override fun getListingByIdAsFlow(id: Long): Flow<Listing?> = listings.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createListing(content: ListingContent): Result<Long, DataError.Local> {
        createError?.let { return Result.Error(it) }
        val id = nextId++
        listings.update {
            it + listing(id).copy(
                title = content.title,
                description = content.description,
                priceCents = content.priceCents,
                condition = content.condition,
                postcode = content.postcode,
                updatedAt = 10_000 + id,
            )
        }
        return Result.Success(id)
    }

    override suspend fun updateContent(id: Long, content: ListingContent): EmptyResult<DataError.Local> {
        updatedContents += id to content
        listings.update { all ->
            all.map {
                if (it.id == id) it.copy(
                    title = content.title,
                    description = content.description,
                    priceCents = content.priceCents,
                    condition = content.condition,
                    postcode = content.postcode,
                ) else it
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun setArchived(id: Long, archived: Boolean): EmptyResult<DataError.Local> {
        listings.update { all -> all.map { if (it.id == id) it.copy(archived = archived) else it } }
        return Result.Success(Unit)
    }

    override suspend fun duplicateListing(id: Long): Result<Long, DataError.Local> {
        val source = listings.value.first { it.id == id }
        val newId = nextId++
        listings.update { it + source.copy(id = newId, archived = false) }
        return Result.Success(newId)
    }

    override suspend fun deleteListing(id: Long): EmptyResult<DataError.Local> {
        deletedIds += id
        listings.update { all -> all.filterNot { it.id == id } }
        return Result.Success(Unit)
    }

    override suspend fun addImages(listingId: Long, sourcePaths: List<String>): Result<AddImagesResult, DataError.Local> {
        addedImagePaths += sourcePaths
        val failed = sourcePaths.filter { it in failingImagePaths }
            .map { FailedImage(it, ImageImportError.UNSUPPORTED_FORMAT) }
        return Result.Success(AddImagesResult(addedCount = sourcePaths.size - failed.size, failedFiles = failed))
    }

    override suspend fun removeImage(imageId: Long): EmptyResult<DataError.Local> {
        removedImageIds += imageId
        return Result.Success(Unit)
    }

    override suspend fun reorderImages(listingId: Long, orderedImageIds: List<Long>): EmptyResult<DataError.Local> {
        reorders += orderedImageIds
        return Result.Success(Unit)
    }

    override suspend fun deleteUnusedImageFiles(): EmptyResult<DataError.Local> {
        cleanupCalls++
        return Result.Success(Unit)
    }
}

class FakeSettingsDataSource(initial: AppSettings = AppSettings()) : SettingsDataSource {
    val settings = MutableStateFlow(initial)
    override fun getSettingsAsFlow(): Flow<AppSettings> = settings
    override suspend fun getSettings(): AppSettings = settings.value
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)
}

class FakeWillhabenFieldsDataSource : WillhabenFieldsLocalDataSource {
    val fields = MutableStateFlow<Map<Long, WillhabenListingFields>>(emptyMap())

    override fun getFieldsAsFlow(listingId: Long): Flow<WillhabenListingFields?> = fields.map { it[listingId] }
    override suspend fun getFields(listingId: Long): WillhabenListingFields? = fields.value[listingId]
    override suspend fun saveFields(listingId: Long, fields: WillhabenListingFields): EmptyResult<DataError.Local> {
        this.fields.update { it + (listingId to fields) }
        return Result.Success(Unit)
    }
}

class FakeWillhabenCatalogRepository(
    var tree: Result<WillhabenCategoryTree, DataError> = Result.Error(DataError.Network.NO_INTERNET),
) : WillhabenCatalogRepository {
    val suggestionsByTitle = mutableMapOf<String, List<Int>>()
    val locationsByPostcode = mutableMapOf<String, List<WillhabenLocation>>()
    val locationRequests = mutableListOf<String>()

    override suspend fun getCategoryTree(forceRefresh: Boolean): Result<WillhabenCategoryTree, DataError> = tree

    override suspend fun suggestCategories(title: String): Result<List<Int>, DataError.Network> =
        Result.Success(suggestionsByTitle[title].orEmpty())

    override suspend fun findLocations(postcode: String): Result<List<WillhabenLocation>, DataError.Network> {
        locationRequests += postcode
        return Result.Success(locationsByPostcode[postcode].orEmpty())
    }
}

class FakeImagePicker(var result: List<String> = emptyList()) : ImagePicker {
    override suspend fun pickImages(): List<String> = result
}

/** Time that only moves when the test says so. */
class FakeTimeProvider(var now: Long = 1_000L) : com.lerchenflo.willhabenupdater.utilities.TimeProvider {
    override fun nowEpochMillis(): Long = now
}

/** Encrypted storage stand-in: a plain map. */
class FakeSecureStorage : com.lerchenflo.willhabenupdater.datasource.preferences.SecureStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }
    override suspend fun delete(key: String) {
        values.remove(key)
    }
}
