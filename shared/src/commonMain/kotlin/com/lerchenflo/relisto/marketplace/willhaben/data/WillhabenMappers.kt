package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.marketplace.willhaben.data.dto.CategoryNodeDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.CategoryRefDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.CategoryTreeResponseDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.DeliveryOptionsDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.LocationDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.SuggestedCategoryDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.WillhabenFieldsDto
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttribute
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeReference
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeValue
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategory
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLocation

fun CategoryTreeResponseDto.toCategoryTree(): WillhabenCategoryTree = WillhabenCategoryTree(
    root = categoryNode.toCategory(),
    attributesByCode = attributes.associate { attribute ->
        val element = attribute.treeAttributeElement
        element.code to WillhabenAttribute(
            code = element.code,
            label = element.label,
            values = attribute.values.map { WillhabenAttributeValue(code = it.code, label = it.label) },
        )
    },
)

private fun CategoryNodeDto.toCategory(): WillhabenCategory = WillhabenCategory(
    treeId = treeId,
    code = code,
    label = label,
    children = children.map { it.toCategory() },
    attributeReferences = attributeReferences.map {
        WillhabenAttributeReference(
            code = it.code,
            required = it.required,
            multiSelect = it.selectionType == "MULTI_SELECT",
        )
    },
)

/** Id of the deepest category of one suggested path. */
fun SuggestedCategoryDto.leafId(): Int = categories.firstOrNull()?.leafId() ?: id

fun LocationDto.toLocation(): WillhabenLocation = WillhabenLocation(id = id, label = description)

fun WillhabenFieldsDto.toFields(): WillhabenListingFields = WillhabenListingFields(
    categoryPath = categoryPath.map { WillhabenCategoryRef(treeId = it.treeId, code = it.code, label = it.label) },
    attributes = attributes,
    locationId = locationId,
    locationLabel = locationLabel,
    delivery = DeliveryOptions(
        pickup = delivery.pickup,
        shipping = delivery.shipping,
        shippingPriceCents = delivery.shippingPriceCents,
        paylivery = delivery.paylivery,
    ),
    adTypeId = adTypeId,
)

fun WillhabenListingFields.toDto(): WillhabenFieldsDto = WillhabenFieldsDto(
    categoryPath = categoryPath.map { CategoryRefDto(treeId = it.treeId, code = it.code, label = it.label) },
    attributes = attributes,
    locationId = locationId,
    locationLabel = locationLabel,
    delivery = DeliveryOptionsDto(
        pickup = delivery.pickup,
        shipping = delivery.shipping,
        shippingPriceCents = delivery.shippingPriceCents,
        paylivery = delivery.paylivery,
    ),
    adTypeId = adTypeId,
)
