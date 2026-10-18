package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

/** One entry of "my adverts" on willhaben. */
data class RemoteListingSummary(
    val remoteId: String,
    val remoteUuid: String?,
    val adTypeId: Int?,
    val title: String?,
)

/** A marketplace listing of the user with its full willhaben JSON (the editable listing). */
data class RemoteListing(
    val summary: RemoteListingSummary,
    val detailJson: String,
)

data class MyAdvertsPage(
    val adverts: List<RemoteListingSummary>,
    val total: Int,
)
