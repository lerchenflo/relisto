package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.listings.domain.ListingContent

/** Keeps a listing that is already on willhaben up to date with the local content. */
interface WillhabenUpdater {
    /**
     * Sends the local content and, when they changed, the local images to the willhaben listing
     * ("Änderungen übertragen"). Stops with [PublishError.Conflict] when the listing was changed on
     * willhaben since the last sync, unless [overwriteRemote] ("keep local").
     */
    suspend fun pushChanges(listingId: Long, overwriteRemote: Boolean = false): EmptyResult<PublishError>

    /** Replaces the local content, willhaben fields and images with willhaben's current listing ("take willhaben"). */
    suspend fun takeRemote(listingId: Long): EmptyResult<PublishError>

    /** willhaben's version as of the last sync or conflict check, for the comparison; null when unknown. */
    suspend fun remoteContent(listingId: Long): ListingContent?
}
