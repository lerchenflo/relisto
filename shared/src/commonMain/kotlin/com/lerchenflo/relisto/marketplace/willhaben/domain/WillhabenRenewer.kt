package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.relisto.marketplace.domain.isExpired
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.first

data class RenewSummary(
    val renewed: Int,
    val failed: Int,
    /** Error of the first listing that could not be renewed. */
    val firstError: PublishError?,
)

/**
 * Renews several expired willhaben listings one after another; a failed listing does not stop
 * the others. Afterwards the listings are imported once, so status and expiry are current.
 */
class WillhabenRenewer(
    private val publisher: WillhabenPublisher,
    private val importer: WillhabenImporter,
    private val statuses: PublicationStatusRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun renew(listingIds: List<Long>): RenewSummary {
        var renewed = 0
        var firstError: PublishError? = null
        listingIds.forEach { listingId ->
            when (val result = publisher.republish(listingId)) {
                is Result.Success -> renewed++
                is Result.Error -> if (firstError == null) firstError = result.error
            }
        }
        if (renewed > 0) importer.importMyListings()
        return RenewSummary(renewed = renewed, failed = listingIds.size - renewed, firstError = firstError)
    }

    /** Imports first, so listings that expired since the last import are found too. */
    suspend fun renewAllExpired(): Result<RenewSummary, DataError> {
        (importer.importMyListings() as? Result.Error)?.let { return it }
        val now = timeProvider.nowEpochMillis()
        val expiredIds = statuses.getStatusesAsFlow().first()
            .filter { it.marketplace == Marketplace.WILLHABEN && it.isExpired(now) }
            .map { it.listingId }
        return Result.Success(renew(expiredIds))
    }
}
