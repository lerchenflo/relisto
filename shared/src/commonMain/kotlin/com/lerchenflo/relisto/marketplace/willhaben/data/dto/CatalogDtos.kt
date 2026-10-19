package com.lerchenflo.relisto.marketplace.willhaben.data.dto

import kotlinx.serialization.Serializable

/** Response of `GET /iad/categorytree/withattributes/67`. */
@Serializable
data class CategoryTreeResponseDto(
    val categoryNode: CategoryNodeDto,
    val attributes: List<AttributeDto> = emptyList(),
)

@Serializable
data class CategoryNodeDto(
    val treeId: Int,
    val code: String,
    val label: String,
    val children: List<CategoryNodeDto> = emptyList(),
    val attributeReferences: List<AttributeReferenceDto> = emptyList(),
)

@Serializable
data class AttributeReferenceDto(
    val code: String,
    val required: Boolean = false,
    val selectionType: String = "SINGLE_SELECT",
)

@Serializable
data class AttributeDto(
    val treeAttributeElement: AttributeElementDto,
    val values: List<AttributeElementDto> = emptyList(),
)

@Serializable
data class AttributeElementDto(
    val treeId: Int,
    val code: String,
    val label: String,
)

/** Response of `GET /iad/bap/categorysuggestion/attributetree`. Each top entry is one suggested path. */
@Serializable
data class CategorySuggestionResponseDto(
    val categories: List<SuggestedCategoryDto> = emptyList(),
)

@Serializable
data class SuggestedCategoryDto(
    val id: Int,
    val code: String? = null,
    val name: String? = null,
    val categories: List<SuggestedCategoryDto> = emptyList(),
)

/** Response of `GET /iad/locations/{postcode}?isoCountryCode=AT`. */
@Serializable
data class LocationsResponseDto(
    val locations: List<LocationDto> = emptyList(),
)

@Serializable
data class LocationDto(
    val id: Int,
    val description: String,
)
