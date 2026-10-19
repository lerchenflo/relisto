package com.lerchenflo.relisto.marketplace.willhaben.data.dto

import kotlinx.serialization.Serializable

/** JSON stored in `listing_publications.marketplaceFieldsJson` for willhaben. */
@Serializable
data class WillhabenFieldsDto(
    val categoryPath: List<CategoryRefDto> = emptyList(),
    val attributes: Map<String, List<String>> = emptyMap(),
    val locationId: Int? = null,
    val locationLabel: String? = null,
    val delivery: DeliveryOptionsDto = DeliveryOptionsDto(),
    val adTypeId: Int? = null,
    val pendingDraftId: String? = null,
)

@Serializable
data class CategoryRefDto(
    val treeId: Int,
    val code: String,
    val label: String,
)

@Serializable
data class DeliveryOptionsDto(
    val pickup: Boolean = true,
    val shipping: Boolean = false,
    val shippingPriceCents: Long? = null,
    val paylivery: Boolean = false,
)
