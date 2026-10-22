package com.lerchenflo.relisto.activity.presentation

import androidx.compose.runtime.Stable
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.marketplace.domain.Marketplace

@Stable
data class ActivityLogState(
    val isLoading: Boolean = true,
    val entries: List<ActivityEntryUi> = emptyList(),
    /** Title of the listing the log is filtered to; null shows every listing. */
    val listingTitle: String? = null,
    val onlyFailed: Boolean = false,
    /** Marketplaces in the log; the filter is shown when there is more than one. */
    val marketplaces: List<Marketplace> = emptyList(),
    val selectedMarketplace: Marketplace? = null,
)

data class ActivityEntryUi(
    val id: Long,
    val timeText: String,
    val listingTitle: String?,
    val marketplace: Marketplace,
    val action: ActivityAction,
    val success: Boolean,
    /** Technical error, e.g. "Failed(step=EDIT, error=SERVER_ERROR)"; null on success. */
    val message: String?,
)

sealed interface ActivityLogAction {
    data object OnBackClick : ActivityLogAction
    data object OnClearListingFilterClick : ActivityLogAction
    data class OnOnlyFailedChange(val onlyFailed: Boolean) : ActivityLogAction
    /** null shows all marketplaces. */
    data class OnMarketplaceSelect(val marketplace: Marketplace?) : ActivityLogAction
}
