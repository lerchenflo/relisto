package com.lerchenflo.relisto.marketplace.domain

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

/** Status names of running listings; only these (or an unknown status) can run out by their end date. */
private val RUNNING_STATUSES = setOf("Online", "Aktiv")

/**
 * Expired on the marketplace: its own status says so, or a running listing's end date has passed.
 * Sold, reserved or deactivated listings are never expired, so they are never renewed.
 */
fun PublicationStatus.isExpired(nowMillis: Long): Boolean =
    statusText == EXPIRED_STATUS ||
        ((statusText == null || statusText in RUNNING_STATUSES) && expiresAt != null && expiresAt <= nowMillis)
