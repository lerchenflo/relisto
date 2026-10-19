package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import kotlinx.serialization.json.JsonObject

/** Loads the user's listings of all verticals page by page, with the full JSON and the images of each listing. */
class WillhabenListingsFetcher(
    private val dataSource: KtorWillhabenListingsDataSource,
    private val runner: WillhabenRequestRunner,
    private val pageSize: Int = 30,
) : WillhabenListingsSource {

    override suspend fun fetchMyListings(): Result<List<RemoteListing>, DataError.Network> {
        val summaries = buildList {
            var page = 1
            while (true) {
                val result = runner.run { dataSource.fetchMyAdverts(page = page, rows = pageSize) }
                val pageData = (result as? Result.Success)?.data ?: return result as Result.Error
                addAll(pageData.adverts)
                if (pageData.adverts.isEmpty() || size >= pageData.total) break
                page++
            }
        }
        return Result.Success(
            summaries.mapNotNull { summary ->
                // Ad types without a known edit form (jobs) are skipped
                val route = WillhabenAdTypes.detailRoute(summary.remoteId, summary.adTypeId) ?: return@mapNotNull null
                when (val detail = runner.run { dataSource.fetchListingJson(route) }) {
                    is Result.Error -> return detail
                    is Result.Success -> RemoteListing(summary, detail.data, imageUrls = imageUrls(detail.data))
                }
            }
        )
    }

    /**
     * The listing JSON only links to its images. A failed image list gives no images at all:
     * with only some of them, a renew would replace the others on willhaben.
     */
    private suspend fun imageUrls(detailJson: String): List<String> {
        val detail = runCatching { AppJson.parseToJsonElement(detailJson) as? JsonObject }.getOrNull() ?: return emptyList()
        val imageList = ContextLinks.find(detail, "imageList") ?: return emptyList()
        return (runner.run { dataSource.fetchImageUrls(imageList.route) } as? Result.Success)?.data.orEmpty()
    }
}

/** The user's listings on willhaben with their full JSON. */
interface WillhabenListingsSource {
    suspend fun fetchMyListings(): Result<List<RemoteListing>, DataError.Network>
}
