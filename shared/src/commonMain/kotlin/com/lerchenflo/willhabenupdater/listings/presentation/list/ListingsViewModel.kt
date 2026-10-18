package com.lerchenflo.willhabenupdater.listings.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.domain.onFailure
import com.lerchenflo.willhabenupdater.app.domain.onSuccess
import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.app.navigation.Route
import com.lerchenflo.willhabenupdater.listings.domain.Listing
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.Marketplace
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.isExpired
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.toUiText
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenRenewer
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import com.lerchenflo.willhabenupdater.utilities.UiText
import com.lerchenflo.willhabenupdater.utilities.PriceFormatter
import com.lerchenflo.willhabenupdater.utilities.formatDateTime
import com.lerchenflo.willhabenupdater.utilities.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.renew_summary

class ListingsViewModel(
    private val listingRepository: ListingRepository,
    private val settingsDataSource: SettingsDataSource,
    private val willhabenFields: WillhabenFieldsLocalDataSource,
    private val navigator: Navigator,
    private val publicationStatuses: PublicationStatusRepository,
    private val renewer: WillhabenRenewer,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<ListingsEvent>()
    val events = _events.receiveAsFlow()

    private var allListings: List<Listing> = emptyList()
    private var willhabenStatusByListing: Map<Long, String> = emptyMap()
    private var expiredListingIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            listingRepository.deleteUnusedImageFiles()
        }
        viewModelScope.launch {
            publicationStatuses.getStatusesAsFlow().collect { statuses ->
                val willhaben = statuses.filter { it.marketplace == Marketplace.WILLHABEN }
                willhabenStatusByListing = willhaben
                    .filter { it.statusText != null }
                    .associate { it.listingId to it.statusText!! }
                val now = timeProvider.nowEpochMillis()
                expiredListingIds = willhaben.filter { it.isExpired(now) }.map { it.listingId }.toSet()
                _state.update { it.withVisibleListings() }
            }
        }
        viewModelScope.launch {
            listingRepository.getListingsAsFlow().collect { listings ->
                allListings = listings
                _state.update { state ->
                    val selectedStillExists = listings.any { it.id == state.selectedListingId }
                    state.copy(
                        isLoading = false,
                        selectedListingId = state.selectedListingId.takeIf { selectedStillExists },
                    ).withVisibleListings()
                }
            }
        }
    }

    fun onAction(action: ListingsAction) {
        when (action) {
            is ListingsAction.OnSearchQueryChange -> _state.update {
                it.copy(searchQuery = action.query).withVisibleListings()
            }
            is ListingsAction.OnFilterChange -> _state.update {
                it.copy(filter = action.filter).withVisibleListings()
            }
            is ListingsAction.OnListingClick -> _state.update { it.copy(selectedListingId = action.listingId) }
            ListingsAction.OnNewListingClick -> createListing()
            ListingsAction.OnSettingsClick -> viewModelScope.launch { navigator.navigate(Route.Settings) }
            is ListingsAction.OnListingCheckedChange -> _state.update { state ->
                val checked = when {
                    !action.checked -> state.checkedListingIds - action.listingId
                    action.listingId in renewableIds() -> state.checkedListingIds + action.listingId
                    else -> state.checkedListingIds
                }
                state.copy(checkedListingIds = checked).withVisibleListings()
            }
            ListingsAction.OnSelectAllExpiredClick -> _state.update {
                it.copy(checkedListingIds = renewableIds()).withVisibleListings()
            }
            ListingsAction.OnClearCheckedClick -> _state.update {
                it.copy(checkedListingIds = emptySet()).withVisibleListings()
            }
            ListingsAction.OnRenewCheckedClick -> renewChecked()
        }
    }

    private fun createListing() {
        viewModelScope.launch {
            val settings = settingsDataSource.getSettings()
            listingRepository.createListing(
                ListingContent(postcode = settings.defaultPostcode, condition = settings.defaultCondition)
            ).onSuccess { newId ->
                willhabenFields.saveFields(
                    newId,
                    WillhabenListingFields(
                        delivery = DeliveryOptions(
                            pickup = settings.defaultPickup,
                            shipping = settings.defaultShipping,
                            shippingPriceCents = settings.defaultShippingPriceCents,
                        )
                    )
                )
                // Show the new listing even if a search or the archive view would hide it
                _state.update {
                    it.copy(selectedListingId = newId, searchQuery = "", filter = ListingFilter.ACTIVE).withVisibleListings()
                }
            }.onFailure { error ->
                _events.send(ListingsEvent.ShowMessage(error.toUiText()))
            }
        }
    }

    private fun renewChecked() {
        val ids = allListings.map { it.id }.filter { it in _state.value.checkedListingIds }
        if (ids.isEmpty() || _state.value.isRenewing) return
        _state.update { it.copy(isRenewing = true) }
        viewModelScope.launch {
            val summary = renewer.renew(ids)
            _state.update { it.copy(isRenewing = false, checkedListingIds = emptySet()).withVisibleListings() }
            _events.send(
                ListingsEvent.ShowMessage(
                    UiText.StringResourceText(Res.string.renew_summary, arrayOf(summary.renewed, summary.failed))
                )
            )
            summary.firstError?.let { _events.send(ListingsEvent.ShowMessage(it.toUiText())) }
        }
    }

    /** Expired listings that are not archived; only these can be checked. */
    private fun renewableIds(): Set<Long> =
        allListings.filter { !it.archived && it.id in expiredListingIds }.map { it.id }.toSet()

    private fun ListingsState.withVisibleListings(): ListingsState {
        val query = searchQuery.trim()
        val visible = allListings
            .filter { it.archived == (filter == ListingFilter.ARCHIVED) }
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            .map { it.toListItemUi(checkedListingIds) }
        // Checked listings that are no longer renewable (renewed, archived, deleted) drop out
        val renewable = renewableIds()
        return copy(listings = visible, checkedListingIds = checkedListingIds intersect renewable, renewableCount = renewable.size)
    }

    private fun Listing.toListItemUi(checkedIds: Set<Long>) = ListingListItemUi(
        id = id,
        title = title,
        formattedPrice = priceCents?.let(PriceFormatter::format),
        coverImagePath = images.firstOrNull()?.filePath,
        imageCount = images.size,
        updatedText = formatDateTime(updatedAt),
        willhabenStatus = willhabenStatusByListing[id],
        canRenew = !archived && id in expiredListingIds,
        isChecked = id in checkedIds,
    )
}
