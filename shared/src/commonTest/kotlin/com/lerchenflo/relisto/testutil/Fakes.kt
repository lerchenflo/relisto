package com.lerchenflo.relisto.testutil

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.AddImagesResult
import com.lerchenflo.relisto.listings.domain.FailedImage
import com.lerchenflo.relisto.listings.domain.ImageImportError
import com.lerchenflo.relisto.listings.domain.ImagePicker
import com.lerchenflo.relisto.listings.domain.Listing
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.ListingImage
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.settings.domain.SettingsDataSource
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

    val addedImageBytes = mutableListOf<Pair<Long, Int>>()

    override suspend fun addImageBytes(listingId: Long, images: List<Pair<ByteArray, String>>): Result<Int, DataError.Local> {
        addedImageBytes += listingId to images.size
        return Result.Success(images.size)
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
class FakeTimeProvider(var now: Long = 1_000L) : com.lerchenflo.relisto.utilities.TimeProvider {
    override fun nowEpochMillis(): Long = now
}

/** Encrypted storage stand-in: a plain map. */
class FakeSecureStorage : com.lerchenflo.relisto.datasource.preferences.SecureStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }
    override suspend fun delete(key: String) {
        values.remove(key)
    }
}

class FakeSessionRepository(
    var refreshResult: com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState =
        com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState.LoggedOut,
) : com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenSessionRepository {
    private val state = MutableStateFlow<com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState>(
        com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState.Unknown
    )
    override val sessionState = state
    var completeLoginResult: EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError> = Result.Success(Unit)
    val completedLogins = mutableListOf<List<com.lerchenflo.relisto.app.domain.SessionCookie>>()
    var logoutCalls = 0

    override suspend fun refreshSession(): com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState {
        state.value = refreshResult
        return refreshResult
    }

    override suspend fun completeLogin(
        cookies: List<com.lerchenflo.relisto.app.domain.SessionCookie>
    ): EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError> {
        completedLogins += cookies
        val result = completeLoginResult
        if (result is Result.Success) {
            state.value = com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState.LoggedIn(displayName = null)
        }
        return result
    }

    override suspend fun logout() {
        logoutCalls++
        state.value = com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState.LoggedOut
    }
}

class FakeLoginBrowser(
    var result: Result<List<com.lerchenflo.relisto.app.domain.SessionCookie>, com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError> =
        Result.Success(listOf(com.lerchenflo.relisto.app.domain.SessionCookie("BBX_JSESSIONID", "s", "www.willhaben.at", hostOnly = true))),
) : com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLoginBrowser {
    var loginCalls = 0
    var clearCalls = 0
    /** When set, login() waits until the test completes it. */
    var pending: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun login(): Result<List<com.lerchenflo.relisto.app.domain.SessionCookie>, com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError> {
        loginCalls++
        pending?.await()
        return result
    }

    override suspend fun clearSession() {
        clearCalls++
    }
}

class FakeImporter : com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenImporter {
    var result: Result<com.lerchenflo.relisto.marketplace.willhaben.domain.ImportSummary, DataError> =
        Result.Success(com.lerchenflo.relisto.marketplace.willhaben.domain.ImportSummary(found = 0))
    var calls = 0
    var pending: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun importMyListings(): Result<com.lerchenflo.relisto.marketplace.willhaben.domain.ImportSummary, DataError> {
        calls++
        pending?.await()
        return result
    }
}

/** Image store in memory: stored bytes are the "file", the hash is the text of the bytes. */
class InMemoryImageStorage : com.lerchenflo.relisto.listings.domain.ImageStorage {
    val files = mutableSetOf<String>()

    override suspend fun importImage(sourcePath: String) =
        importImageBytes(sourcePath.encodeToByteArray(), "jpg")

    override suspend fun importImageBytes(
        bytes: ByteArray,
        extension: String
    ): Result<com.lerchenflo.relisto.listings.domain.StoredImage, com.lerchenflo.relisto.listings.domain.ImageImportError> {
        val hash = "h" + bytes.decodeToString().hashCode().toUInt().toString(16)
        files += "$hash.$extension"
        return Result.Success(com.lerchenflo.relisto.listings.domain.StoredImage("$hash.$extension", hash, 100, 100))
    }

    override suspend fun readBytes(absolutePath: String): ByteArray? =
        if (absolutePath.removePrefix("/images/") in files) "image:$absolutePath".encodeToByteArray() else null

    override suspend fun deleteFile(fileName: String) {
        files -= fileName
    }

    override suspend fun listFileNames(): List<String> = files.toList()
    override fun absolutePath(fileName: String): String = "/images/$fileName"
}

class FakeImageDownloader : com.lerchenflo.relisto.listings.domain.ImageDownloader {
    val downloaded = mutableListOf<String>()

    override suspend fun download(url: String): Result<Pair<ByteArray, String>, DataError.Network> {
        downloaded += url
        return if ("broken" in url) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(url.encodeToByteArray() to "jpg")
    }
}

class FakePublicationStatusRepository : com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository {
    val statuses = MutableStateFlow<List<com.lerchenflo.relisto.marketplace.domain.PublicationStatus>>(emptyList())
    override fun getStatusesAsFlow(): Flow<List<com.lerchenflo.relisto.marketplace.domain.PublicationStatus>> = statuses
}

class FakePublisher : com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenPublisher {
    var result: com.lerchenflo.relisto.app.domain.EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError> =
        Result.Success(Unit)
    val republished = mutableListOf<Long>()
    var pending: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun republish(listingId: Long): com.lerchenflo.relisto.app.domain.EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError> {
        republished += listingId
        pending?.await()
        return result
    }
}

class FakeStatusActions : com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActions {
    var result: com.lerchenflo.relisto.app.domain.EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError> =
        Result.Success(Unit)
    val performed = mutableListOf<Pair<Long, com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction>>()

    override suspend fun perform(
        listingId: Long,
        action: com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction,
    ): com.lerchenflo.relisto.app.domain.EmptyResult<com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError> {
        performed += listingId to action
        return result
    }

    /** Listing ids for which [performAll] fails. */
    var failingIds: Set<Long> = emptySet()

    override suspend fun performAll(
        listingIds: List<Long>,
        action: com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction,
    ): com.lerchenflo.relisto.marketplace.willhaben.domain.BulkActionResult {
        listingIds.forEach { performed += it to action }
        return com.lerchenflo.relisto.marketplace.willhaben.domain.BulkActionResult(
            succeeded = listingIds.filter { it !in failingIds }.toSet(),
            firstError = if (listingIds.any { it in failingIds }) com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError.NotAvailable else null,
        )
    }
}
