package com.lerchenflo.relisto.marketplace.willhaben.presentation

import androidx.compose.runtime.Stable
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenVertical
import com.lerchenflo.relisto.utilities.UiText

@Stable
data class WillhabenTabState(
    /** Category, details and delivery options only exist for marketplace listings. */
    val vertical: WillhabenVertical = WillhabenVertical.MARKETPLACE,
    val isTreeLoading: Boolean = true,
    val treeError: UiText? = null,
    /** "Sport / Sportgeräte › Fahrräder"; null when no category is chosen. */
    val categoryPathText: String? = null,
    val suggestions: List<CategoryOptionUi> = emptyList(),
    val isSuggesting: Boolean = false,
    val isCategoryPickerOpen: Boolean = false,
    val categoryQuery: String = "",
    /** Search hits while [categoryQuery] is not blank. */
    val categoryResults: List<CategoryOptionUi> = emptyList(),
    /** Step-by-step selection: the categories opened so far, from the top. */
    val categoryBreadcrumb: List<CategoryNodeUi> = emptyList(),
    /** The categories one level below the last breadcrumb entry (top level when empty). */
    val categoryLevel: List<CategoryNodeUi> = emptyList(),
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
    /** The listing on willhaben; null while it exists only locally. */
    val publication: PublicationUi? = null,
    val isRepublishConfirmOpen: Boolean = false,
    val isRepublishing: Boolean = false,
)

data class PublicationUi(
    /** willhaben's status text, e.g. "Abgelaufen". */
    val statusText: String?,
    val expiresText: String?,
    val syncState: SyncState,
    /** Only expired listings can be published again; a running one would be duplicated. */
    val canRepublish: Boolean,
)

data class CategoryOptionUi(
    val treeId: Int,
    val pathText: String,
)

data class CategoryNodeUi(
    val treeId: Int,
    val label: String,
    /** Categories with children are opened, the others are selected. */
    val hasChildren: Boolean,
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
    data class OnCategoryBrowse(val treeId: Int) : WillhabenTabAction
    /** null goes back to the top level. */
    data class OnCategoryBreadcrumbClick(val treeId: Int?) : WillhabenTabAction
    data class OnAttributeValueToggle(val attributeCode: String, val valueCode: String) : WillhabenTabAction
    data class OnLocationSelect(val locationId: Int) : WillhabenTabAction
    data class OnPickupChange(val enabled: Boolean) : WillhabenTabAction
    data class OnShippingChange(val enabled: Boolean) : WillhabenTabAction
    data class OnShippingPriceChange(val priceText: String) : WillhabenTabAction
    data class OnPayliveryChange(val enabled: Boolean) : WillhabenTabAction
    data object OnRepublishClick : WillhabenTabAction
    data object OnRepublishConfirm : WillhabenTabAction
    data object OnRepublishDismiss : WillhabenTabAction
}

sealed interface WillhabenTabEvent {
    data class ShowMessage(val message: UiText) : WillhabenTabEvent
}
