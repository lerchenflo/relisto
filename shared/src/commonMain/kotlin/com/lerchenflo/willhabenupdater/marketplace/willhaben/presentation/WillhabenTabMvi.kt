package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation

import androidx.compose.runtime.Stable
import com.lerchenflo.willhabenupdater.utilities.UiText

@Stable
data class WillhabenTabState(
    val isTreeLoading: Boolean = true,
    val treeError: UiText? = null,
    /** "Sport / Sportgeräte › Fahrräder"; null when no category is chosen. */
    val categoryPathText: String? = null,
    val suggestions: List<CategoryOptionUi> = emptyList(),
    val isSuggesting: Boolean = false,
    val isCategoryPickerOpen: Boolean = false,
    val categoryQuery: String = "",
    val categoryResults: List<CategoryOptionUi> = emptyList(),
    /** Attributes of the chosen category, without condition and handover (they come from other fields). */
    val attributeFields: List<AttributeFieldUi> = emptyList(),
    val postcode: String = "",
    val locations: List<LocationOptionUi> = emptyList(),
    val selectedLocationId: Int? = null,
    val selectedLocationLabel: String? = null,
    val isLoadingLocations: Boolean = false,
    val pickup: Boolean = true,
    val shipping: Boolean = false,
    val shippingPriceText: String = "",
    val isShippingPriceInvalid: Boolean = false,
    val paylivery: Boolean = false,
)

data class CategoryOptionUi(
    val treeId: Int,
    val pathText: String,
)

data class AttributeFieldUi(
    val code: String,
    val label: String,
    val required: Boolean,
    val multiSelect: Boolean,
    val options: List<AttributeOptionUi>,
    val selectedCodes: List<String>,
)

data class AttributeOptionUi(
    val code: String,
    val label: String,
)

data class LocationOptionUi(
    val id: Int,
    val label: String,
)

sealed interface WillhabenTabAction {
    data object OnRetryLoadTreeClick : WillhabenTabAction
    data object OnSuggestCategoriesClick : WillhabenTabAction
    data object OnOpenCategoryPickerClick : WillhabenTabAction
    data object OnCloseCategoryPicker : WillhabenTabAction
    data class OnCategoryQueryChange(val query: String) : WillhabenTabAction
    data class OnCategorySelect(val treeId: Int) : WillhabenTabAction
    data class OnAttributeValueToggle(val attributeCode: String, val valueCode: String) : WillhabenTabAction
    data class OnLocationSelect(val locationId: Int) : WillhabenTabAction
    data class OnPickupChange(val enabled: Boolean) : WillhabenTabAction
    data class OnShippingChange(val enabled: Boolean) : WillhabenTabAction
    data class OnShippingPriceChange(val priceText: String) : WillhabenTabAction
    data class OnPayliveryChange(val enabled: Boolean) : WillhabenTabAction
}

sealed interface WillhabenTabEvent {
    data class ShowMessage(val message: UiText) : WillhabenTabEvent
}
