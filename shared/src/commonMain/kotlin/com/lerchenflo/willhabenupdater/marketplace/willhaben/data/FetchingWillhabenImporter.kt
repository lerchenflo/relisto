package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.ImportSummary
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenImporter

/**
 * First import step: loads all marketplace listings from willhaben and reports how many were
 * found, without changing the local database yet (the mapping follows from recorded samples).
 */
class FetchingWillhabenImporter(
    private val fetcher: WillhabenListingsFetcher,
) : WillhabenImporter {
    override suspend fun importMyListings(): Result<ImportSummary, DataError> {
        return when (val fetched = fetcher.fetchMyMarketplaceListings()) {
            is Result.Error -> fetched
            is Result.Success -> Result.Success(ImportSummary(found = fetched.data.size))
        }
    }
}
