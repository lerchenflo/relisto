package com.lerchenflo.willhabenupdater.listings.presentation.list

import androidx.compose.runtime.Stable
import com.lerchenflo.willhabenupdater.utilities.UiText

@Stable
data class ListingsState(
    val listings: List<ListingListItemUi> = emptyList(),
    val selectedListingId: Long? = null,
    val searchQuery: String = "",
    val filter: ListingFilter = ListingFilter.ACTIVE,
    val isLoading: Boolean = true,
)

data class ListingListItemUi(
    val id: Long,
    val title: String,
    val formattedPrice: String?,
    val coverImagePath: String?,
    val imageCount: Int,
    val updatedText: String,
)

enum class ListingFilter {
    ACTIVE,
    ARCHIVED
}

sealed interface ListingsAction {
    data class OnSearchQueryChange(val query: String) : ListingsAction
    data class OnFilterChange(val filter: ListingFilter) : ListingsAction
    data class OnListingClick(val listingId: Long) : ListingsAction
    data object OnNewListingClick : ListingsAction
    data object OnSettingsClick : ListingsAction
}

sealed interface ListingsEvent {
    data class ShowMessage(val message: UiText) : ListingsEvent
}
