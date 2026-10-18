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
    /** Expired listings checked for renewing them together. */
    val checkedListingIds: Set<Long> = emptySet(),
    /** Number of expired listings that can be renewed. */
    val renewableCount: Int = 0,
    val isRenewing: Boolean = false,
)

data class ListingListItemUi(
    val id: Long,
    val title: String,
    val formattedPrice: String?,
    val coverImagePath: String?,
    val imageCount: Int,
    val updatedText: String,
    /** willhaben's status text for published listings, e.g. "Abgelaufen". */
    val willhabenStatus: String? = null,
    /** Expired on willhaben, so it can be renewed. */
    val canRenew: Boolean = false,
    val isChecked: Boolean = false,
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
    data class OnListingCheckedChange(val listingId: Long, val checked: Boolean) : ListingsAction
    data object OnSelectAllExpiredClick : ListingsAction
    data object OnClearCheckedClick : ListingsAction
    data object OnRenewCheckedClick : ListingsAction
}

sealed interface ListingsEvent {
    data class ShowMessage(val message: UiText) : ListingsEvent
}
