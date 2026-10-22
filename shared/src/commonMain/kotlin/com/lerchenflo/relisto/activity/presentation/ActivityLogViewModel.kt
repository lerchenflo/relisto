package com.lerchenflo.relisto.activity.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.relisto.activity.domain.ActivityEntry
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.utilities.formatDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the app sent to the marketplaces; opened for one listing ([listingId]) or for all. */
class ActivityLogViewModel(
    listingId: Long?,
    private val activityLog: ActivityLog,
    private val navigator: Navigator,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityLogState())
    val state = _state.asStateFlow()

    private var listingFilter: Long? = listingId
    private var allEntries: List<ActivityEntry> = emptyList()

    init {
        viewModelScope.launch {
            activityLog.getEntriesAsFlow().collect { entries ->
                allEntries = entries
                _state.update { it.copy(isLoading = false).withEntries() }
            }
        }
    }

    fun onAction(action: ActivityLogAction) {
        when (action) {
            ActivityLogAction.OnBackClick -> viewModelScope.launch { navigator.navigateBack() }
            ActivityLogAction.OnClearListingFilterClick -> {
                listingFilter = null
                _state.update { it.withEntries() }
            }
            is ActivityLogAction.OnOnlyFailedChange -> _state.update { it.copy(onlyFailed = action.onlyFailed).withEntries() }
            is ActivityLogAction.OnMarketplaceSelect -> _state.update { it.copy(selectedMarketplace = action.marketplace).withEntries() }
        }
    }

    private fun ActivityLogState.withEntries(): ActivityLogState {
        val filter = listingFilter
        val forListing = if (filter == null) allEntries else allEntries.filter { it.listingId == filter }
        return copy(
            listingTitle = filter?.let { id -> forListing.firstOrNull { it.listingId == id }?.listingTitle },
            marketplaces = allEntries.map { it.marketplace }.distinct(),
            entries = forListing
                .filter { !onlyFailed || !it.success }
                .filter { selectedMarketplace == null || it.marketplace == selectedMarketplace }
                .map { it.toUi() },
        )
    }

    private fun ActivityEntry.toUi() = ActivityEntryUi(
        id = id,
        timeText = formatDateTime(timestamp),
        listingTitle = listingTitle,
        marketplace = marketplace,
        action = action,
        success = success,
        message = message,
    )
}
