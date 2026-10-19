package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.RemoteListing

/** Loads the user's listings of all verticals page by page, with the full JSON of each listing. */
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
                    is Result.Success -> RemoteListing(summary, detail.data)
                }
            }
        )
    }
}

/** The user's listings on willhaben with their full JSON. */
interface WillhabenListingsSource {
    suspend fun fetchMyListings(): Result<List<RemoteListing>, DataError.Network>
}
