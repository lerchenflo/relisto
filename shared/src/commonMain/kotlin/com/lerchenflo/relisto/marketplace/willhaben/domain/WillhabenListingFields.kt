package com.lerchenflo.relisto.marketplace.willhaben.domain

/** willhaben-specific fields of one listing, stored in its willhaben publication row. */
data class WillhabenListingFields(
    /** Path from the top category down to the selected leaf category. */
    val categoryPath: List<WillhabenCategoryRef> = emptyList(),
    /** Selected attribute values: attribute code -> value codes. */
    val attributes: Map<String, List<String>> = emptyMap(),
    val locationId: Int? = null,
    val locationLabel: String? = null,
    val delivery: DeliveryOptions = DeliveryOptions(),
    /** willhaben ad type, e.g. 67 marketplace or 21 motorbike; null for listings stored before it was kept (marketplace). */
    val adTypeId: Int? = null,
)

data class WillhabenCategoryRef(
    val treeId: Int,
    val code: String,
    val label: String,
)

data class DeliveryOptions(
    val pickup: Boolean = true,
    val shipping: Boolean = false,
    val shippingPriceCents: Long? = null,
    val paylivery: Boolean = false,
)
