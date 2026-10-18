package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.RemoteListing

/** Loads all marketplace listings of the user page by page, with the full JSON of each listing. */
class WillhabenListingsFetcher(
    private val dataSource: KtorWillhabenListingsDataSource,
    private val runner: WillhabenRequestRunner,
    private val pageSize: Int = 30,
) : WillhabenListingsSource {

    override suspend fun fetchMyMarketplaceListings(): Result<List<RemoteListing>, DataError.Network> {
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
            summaries.filter { it.adTypeId == MARKETPLACE_AD_TYPE_ID }.map { summary ->
                when (val detail = runner.run { dataSource.fetchListingJson(summary.remoteId) }) {
                    is Result.Error -> return detail
                    is Result.Success -> RemoteListing(summary, detail.data)
                }
            }
        )
    }
}

/** The user's marketplace listings on willhaben with their full JSON. */
interface WillhabenListingsSource {
    suspend fun fetchMyMarketplaceListings(): Result<List<RemoteListing>, DataError.Network>
}
