package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result

data class ImportSummary(
    val found: Int,
    val created: Int = 0,
    val updated: Int = 0,
)

/** Brings the user's willhaben marketplace listings into the local database. */
interface WillhabenImporter {
    suspend fun importMyListings(): Result<ImportSummary, DataError>
}
