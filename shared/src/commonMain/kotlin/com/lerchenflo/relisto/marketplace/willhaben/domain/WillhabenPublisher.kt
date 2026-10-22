package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Error

/** Steps of publishing a listing on willhaben, in this order. */
enum class PublishStep {
    PRODUCT,
    COPY,
    PRESAVE,
    IMAGES,
    FINISH,
    RELOAD,
    PACKAGE,
    ACTIVATE
}

/** A package willhaben sells for a listing, e.g. "Basic - 14 Tage" for 4,99 €. */
data class PaidPackage(val name: String, val priceCents: Long)

sealed interface PublishError : Error {
    /** The listing has no willhaben listing to renew. */
    data object NotPublished : PublishError

    /**
     * willhaben wants money for this listing (or has no free product for its ad type); paid products are out of scope.
     * [cheapestPackage] is the cheapest package willhaben offered, when it offered packages. Nothing was booked.
     */
    data class PaidProduct(val cheapestPackage: PaidPackage? = null) : PublishError

    /** An expected link is missing in willhaben's answer - the unofficial API has changed. */
    data class MissingLink(val linkId: String) : PublishError

    data class Failed(val step: PublishStep, val error: DataError) : PublishError
}

interface WillhabenPublisher {
    /** Publishes an expired willhaben listing again ("Neu veröffentlichen") with the local content. */
    suspend fun republish(listingId: Long): EmptyResult<PublishError>
}
