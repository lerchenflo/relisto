package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.map
import com.lerchenflo.willhabenupdater.datasource.network.AppJson
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.dto.CategoryTreeResponseDto
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * willhaben category tree (about 2.6 MB of JSON), cached as the raw response in the cache folder
 * for a week and parsed once per app run. When the download fails, an old cache is still used.
 */
class CachedWillhabenCatalogRepository(
    private val remote: KtorWillhabenCatalogDataSource,
    appDirectories: AppDirectories,
    private val timeProvider: TimeProvider,
) : WillhabenCatalogRepository {

    private val cacheFile = Path(appDirectories.cacheDir, CATEGORY_TREE_FILE)
    private val timestampFile = Path(appDirectories.cacheDir, "$CATEGORY_TREE_FILE.timestamp")
    private val treeMutex = Mutex()
    private var parsedTree: WillhabenCategoryTree? = null

    override suspend fun getCategoryTree(forceRefresh: Boolean): Result<WillhabenCategoryTree, DataError> =
        treeMutex.withLock {
            if (!forceRefresh) parsedTree?.let { return@withLock Result.Success(it) }

            val freshCache = if (forceRefresh) null else readFreshCache()
            val json = freshCache ?: when (val download = remote.fetchCategoryTreeJson()) {
                is Result.Success -> download.data.also { writeCache(it) }
                is Result.Error -> readCache() ?: return@withLock Result.Error(download.error)
            }
            parse(json).also { if (it is Result.Success) parsedTree = it.data }
        }

    override suspend fun suggestCategories(title: String): Result<List<Int>, DataError.Network> {
        if (title.isBlank()) return Result.Success(emptyList())
        return remote.fetchCategorySuggestions(title.trim()).map { response ->
            response.categories.map { it.leafId() }.distinct()
        }
    }

    override suspend fun findLocations(postcode: String): Result<List<WillhabenLocation>, DataError.Network> {
        return remote.fetchLocations(postcode.trim()).map { response ->
            response.locations.map { it.toLocation() }
        }
    }

    private suspend fun parse(json: String): Result<WillhabenCategoryTree, DataError> =
        withContext(Dispatchers.Default) {
            try {
                Result.Success(AppJson.decodeFromString<CategoryTreeResponseDto>(json).toCategoryTree())
            } catch (e: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException
                e.printStackTrace()
                Result.Error(DataError.Network.SERIALIZATION)
            }
        }

    private suspend fun readFreshCache(): String? {
        val savedAt = readText(timestampFile)?.trim()?.toLongOrNull() ?: return null
        if (timeProvider.nowEpochMillis() - savedAt > CACHE_MAX_AGE_MILLIS) return null
        return readCache()
    }

    private suspend fun readCache(): String? = readText(cacheFile)

    private suspend fun writeCache(json: String) {
        writeText(cacheFile, json)
        writeText(timestampFile, timeProvider.nowEpochMillis().toString())
    }

    private suspend fun readText(path: Path): String? = withContext(Dispatchers.IO) {
        try {
            if (!SystemFileSystem.exists(path)) return@withContext null
            SystemFileSystem.source(path).buffered().use { it.readString() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            null
        }
    }

    private suspend fun writeText(path: Path, text: String) = withContext(Dispatchers.IO) {
        try {
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A missing cache only costs one more download
            e.printStackTrace()
        }
    }

    private companion object {
        const val CATEGORY_TREE_FILE = "willhaben_categorytree.json"
        const val CACHE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
