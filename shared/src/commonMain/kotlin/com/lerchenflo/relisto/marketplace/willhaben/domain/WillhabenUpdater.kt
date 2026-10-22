package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.EmptyResult

/** Keeps a listing that is already on willhaben up to date with the local content. */
interface WillhabenUpdater {
    /**
     * Sends the local content and, when they changed, the local images to the willhaben listing
     * ("Änderungen übertragen"). Stops with [PublishError.Conflict] when the listing was changed on
     * willhaben since the last sync, unless [overwriteRemote] ("keep local").
     */
    suspend fun pushChanges(listingId: Long, overwriteRemote: Boolean = false): EmptyResult<PublishError>
}
