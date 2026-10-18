package com.lerchenflo.willhabenupdater.marketplace.domain

import kotlinx.coroutines.flow.Flow

/** What one marketplace currently says about one listing. */
data class PublicationStatus(
    val listingId: Long,
    val marketplace: Marketplace,
    /** The marketplace's own status text, e.g. "Online" or "Abgelaufen"; null when never published. */
    val statusText: String?,
    val expiresAt: Long?,
    val syncState: SyncState,
)

interface PublicationStatusRepository {
    fun getStatusesAsFlow(): Flow<List<PublicationStatus>>
}

/** willhaben's status name of an expired listing. */
private const val EXPIRED_STATUS = "Abgelaufen"

/** Expired on the marketplace: its own status says so, or the end date has passed. */
fun PublicationStatus.isExpired(nowMillis: Long): Boolean =
    statusText == EXPIRED_STATUS || (expiresAt != null && expiresAt <= nowMillis)
