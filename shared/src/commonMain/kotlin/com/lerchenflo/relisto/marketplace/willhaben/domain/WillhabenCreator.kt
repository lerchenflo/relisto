package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.EmptyResult

/** What a new willhaben listing needs before it can be published. */
enum class MissingField {
    TITLE,
    CATEGORY,
    LOCATION
}

interface WillhabenCreator {
    /**
     * Creates a new free private marketplace listing from the local listing and publishes it.
     * An interrupted publish continues with its draft. [onStep] reports each step for the progress dialog.
     */
    suspend fun publish(listingId: Long, onStep: (PublishStep) -> Unit = {}): EmptyResult<PublishError>
}
