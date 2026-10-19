package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.listings.domain.ListingContent

/** One entry of "my adverts" on willhaben. */
data class RemoteListingSummary(
    val remoteId: String,
    val remoteUuid: String?,
    val adTypeId: Int?,
    val title: String?,
    /** willhaben's own status text, e.g. "Aktiv" or "Abgelaufen". */
    val statusText: String? = null,
    val statusActions: List<WillhabenStatusActionLink> = emptyList(),
)

/** A marketplace listing of the user with its full willhaben JSON (the editable listing). */
data class RemoteListing(
    val summary: RemoteListingSummary,
    val detailJson: String,
    /** All images in their order, full size; empty when the image list could not be loaded. */
    val imageUrls: List<String> = emptyList(),
)

data class MyAdvertsPage(
    val adverts: List<RemoteListingSummary>,
    val total: Int,
)

/** A willhaben listing translated into local content and willhaben fields. */
data class ImportedListing(
    val remoteId: String,
    val remoteUuid: String?,
    val content: ListingContent,
    val fields: WillhabenListingFields,
    val remoteStatus: String?,
    val expiresAt: Long?,
    val imageUrls: List<String>,
    /** The listing JSON as received; base for later edits. Contains contact data, stays local. */
    val snapshotJson: String,
    val statusActions: List<WillhabenStatusActionLink> = emptyList(),
)
