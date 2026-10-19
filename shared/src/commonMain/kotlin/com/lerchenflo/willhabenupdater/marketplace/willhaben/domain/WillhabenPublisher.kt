package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.EmptyResult
import com.lerchenflo.willhabenupdater.app.domain.Error

/** Steps of publishing a listing on willhaben, in this order. */
enum class PublishStep {
    PRODUCT,
    COPY,
    PRESAVE,
    IMAGES,
    FINISH,
    RELOAD,
    ACTIVATE
}

sealed interface PublishError : Error {
    /** The listing has no willhaben listing to renew. */
    data object NotPublished : PublishError

    /** willhaben wants money for this listing (or has no free product for its ad type); paid products are out of scope. */
    data object PaidProduct : PublishError

    /** An expected link is missing in willhaben's answer - the unofficial API has changed. */
    data class MissingLink(val linkId: String) : PublishError

    data class Failed(val step: PublishStep, val error: DataError) : PublishError
}

interface WillhabenPublisher {
    /** Publishes an expired willhaben listing again ("Neu veröffentlichen") with the local content. */
    suspend fun republish(listingId: Long): EmptyResult<PublishError>
}
