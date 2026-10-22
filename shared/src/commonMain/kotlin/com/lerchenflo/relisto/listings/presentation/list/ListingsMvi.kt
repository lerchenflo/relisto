package com.lerchenflo.relisto.listings.presentation.list

import androidx.compose.runtime.Stable
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.utilities.UiText

@Stable
data class ListingsState(
    val listings: List<ListingListItemUi> = emptyList(),
    val selectedListingId: Long? = null,
    val searchQuery: String = "",
    val filter: ListingFilter = ListingFilter.ACTIVE,
    val isLoading: Boolean = true,
    /** Listings checked for a bulk action (renew, archive, delete); only listings of the current filter. */
    val checkedListingIds: Set<Long> = emptySet(),
    /** Number of expired listings that can be renewed. */
    val renewableCount: Int = 0,
    /** Expired listings among the checked ones; only these are renewed. */
    val checkedRenewableCount: Int = 0,
    val isRenewing: Boolean = false,
    val isDeleteConfirmOpen: Boolean = false,
    /** Some checked listings are on willhaben and can be deleted there too. */
    val canDeleteCheckedOnWillhaben: Boolean = false,
    val isDeleting: Boolean = false,
    /** Checked listings with local changes that can be pushed to willhaben. */
    val checkedPushableCount: Int = 0,
    val isPushing: Boolean = false,
    /** willhaben status changes at least one checked listing offers (delete has its own button). */
    val checkedStatusActions: List<WillhabenStatusAction> = emptyList(),
    /** Sold is confirmed first. */
    val confirmBulkStatusAction: WillhabenStatusAction? = null,
    val runningBulkStatusAction: WillhabenStatusAction? = null,
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
    /** Shown as willhaben logo plus a state icon; null for listings that are only local. */
    val willhabenState: WillhabenStateUi? = null,
    /** Expired on willhaben, so it can be renewed. */
    val canRenew: Boolean = false,
    val isChecked: Boolean = false,
)

enum class WillhabenStateUi {
    ONLINE,
    EXPIRED,
    DRAFT,
    IN_REVIEW,
    SOLD,
    DEACTIVATED,
    RESERVED,
    /** No longer on willhaben. */
    GONE,
    OTHER
}

enum class ListingFilter {
    ACTIVE,
    /** Not archived, expired or past half its runtime on the marketplace: due for a renew. */
    EXPIRING,
    ARCHIVED
}

sealed interface ListingsAction {
    data class OnSearchQueryChange(val query: String) : ListingsAction
    data class OnFilterChange(val filter: ListingFilter) : ListingsAction
    data class OnListingClick(val listingId: Long) : ListingsAction
    /** Phones show the editor as its own screen; this goes back to the list. */
    data object OnCloseListingClick : ListingsAction
    data object OnNewListingClick : ListingsAction
    data object OnSettingsClick : ListingsAction
    data object OnActivityLogClick : ListingsAction
    data class OnListingCheckedChange(val listingId: Long, val checked: Boolean) : ListingsAction
    data object OnSelectAllExpiredClick : ListingsAction
    data object OnSelectAllVisibleClick : ListingsAction
    data object OnClearCheckedClick : ListingsAction
    data object OnRenewCheckedClick : ListingsAction
    data object OnArchiveCheckedClick : ListingsAction
    data object OnUnarchiveCheckedClick : ListingsAction
    data object OnDeleteCheckedClick : ListingsAction
    data object OnDeleteCheckedConfirm : ListingsAction
    data object OnDeleteCheckedEverywhereConfirm : ListingsAction
    data object OnDeleteCheckedDismiss : ListingsAction
    data object OnPushCheckedClick : ListingsAction
    data class OnBulkStatusActionClick(val action: WillhabenStatusAction) : ListingsAction
    data object OnBulkStatusActionConfirm : ListingsAction
    data object OnBulkStatusActionDismiss : ListingsAction
}

sealed interface ListingsEvent {
    data class ShowMessage(val message: UiText) : ListingsEvent
}
