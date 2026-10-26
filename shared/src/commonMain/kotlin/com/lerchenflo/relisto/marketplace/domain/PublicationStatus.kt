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
    /** A draft that replaced an expired listing during a renew that did not finish (e.g. activation failed). */
    val isRenewDraft: Boolean = false,
    /** Names of the status actions the marketplace offers right now (willhaben: WillhabenStatusAction). */
    val availableActions: Set<String> = emptySet(),
    /** How long the listing runs on the marketplace; null when it depends on a package (Auto & Motor). */
    val runtimeMillis: Long? = null,
    /** The listing's page on the marketplace; null while it exists only locally. */
    val remoteUrl: String? = null,
) {
    /** In editing on the marketplace ("Bearbeitung"): finished, not copied, to publish it. */
    val isDraft: Boolean get() = statusText == WILLHABEN_DRAFT_STATUS
}

interface PublicationStatusRepository {
    fun getStatusesAsFlow(): Flow<List<PublicationStatus>>
}

/** willhaben's status name of an expired listing. */
private const val EXPIRED_STATUS = "Abgelaufen"

/** willhaben's status name of a draft, e.g. the copy a renew makes before it is activated. */
const val WILLHABEN_DRAFT_STATUS = "Bearbeitung"

/** Status names of running listings; only these (or an unknown status) can run out by their end date. */
private val RUNNING_STATUSES = setOf("Online", "Aktiv")

/**
 * Expired on the marketplace: its own status says so, or a running listing's end date has passed.
 * Sold, reserved or deactivated listings are never expired, so they are never renewed.
 */
fun PublicationStatus.isExpired(nowMillis: Long): Boolean =
    statusText == EXPIRED_STATUS ||
        ((statusText == null || statusText in RUNNING_STATUSES) && expiresAt != null && expiresAt <= nowMillis)

/**
 * Running and past half its runtime (marketplace: the last 22.5 of 45 days), so it should be renewed soon.
 * Listings without a known runtime (Auto & Motor) are never expiring soon.
 */
fun PublicationStatus.isExpiringSoon(nowMillis: Long): Boolean {
    val runtime = runtimeMillis ?: return false
    val end = expiresAt ?: return false
    return (statusText == null || statusText in RUNNING_STATUSES) && end > nowMillis && end - nowMillis <= runtime / 2
}

/**
 * Renewed in bulk and automatically: expired listings, and drafts an unfinished renew left behind.
 * Deactivated listings are activated, not renewed; drafts the user started are finished on request only.
 */
fun PublicationStatus.canRenew(nowMillis: Long): Boolean = isExpired(nowMillis) || isRenewDraft
