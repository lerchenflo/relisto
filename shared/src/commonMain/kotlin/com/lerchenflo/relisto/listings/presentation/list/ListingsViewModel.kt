package com.lerchenflo.relisto.listings.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.relisto.app.domain.onFailure
import com.lerchenflo.relisto.app.domain.onSuccess
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.app.navigation.Route
import com.lerchenflo.relisto.listings.domain.Listing
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.domain.canRenew
import com.lerchenflo.relisto.marketplace.domain.isExpired
import com.lerchenflo.relisto.marketplace.willhaben.presentation.toUiText
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenRenewer
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActions
import com.lerchenflo.relisto.settings.domain.SettingsDataSource
import com.lerchenflo.relisto.utilities.TimeProvider
import com.lerchenflo.relisto.utilities.UiText
import com.lerchenflo.relisto.utilities.PriceFormatter
import com.lerchenflo.relisto.utilities.formatDateTime
import com.lerchenflo.relisto.utilities.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.renew_summary

class ListingsViewModel(
    private val listingRepository: ListingRepository,
    private val settingsDataSource: SettingsDataSource,
    private val willhabenFields: WillhabenFieldsLocalDataSource,
    private val navigator: Navigator,
    private val publicationStatuses: PublicationStatusRepository,
    private val renewer: WillhabenRenewer,
    private val statusActions: WillhabenStatusActions,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<ListingsEvent>()
    val events = _events.receiveAsFlow()

    private var allListings: List<Listing> = emptyList()
    private var willhabenStatusByListing: Map<Long, String> = emptyMap()
    private var willhabenStateByListing: Map<Long, WillhabenStateUi> = emptyMap()
    private var expiredListingIds: Set<Long> = emptySet()
    private var deletableOnWillhabenIds: Set<Long> = emptySet()

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
                deletableOnWillhabenIds = willhaben
                    .filter { WillhabenStatusAction.DELETE.name in it.availableActions }
                    .map { it.listingId }.toSet()
                willhabenStateByListing = willhaben.mapNotNull { status -> status.toStateUi(now)?.let { status.listingId to it } }.toMap()
                expiredListingIds = willhaben.filter { it.canRenew(now) }.map { it.listingId }.toSet()
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
                it.copy(filter = action.filter, checkedListingIds = emptySet()).withVisibleListings()
            }
            is ListingsAction.OnListingClick -> _state.update { it.copy(selectedListingId = action.listingId) }
            ListingsAction.OnCloseListingClick -> _state.update { it.copy(selectedListingId = null) }
            ListingsAction.OnNewListingClick -> createListing()
            ListingsAction.OnSettingsClick -> viewModelScope.launch { navigator.navigate(Route.Settings) }
            is ListingsAction.OnListingCheckedChange -> _state.update { state ->
                val checked = if (action.checked) state.checkedListingIds + action.listingId else state.checkedListingIds - action.listingId
                state.copy(checkedListingIds = checked).withVisibleListings()
            }
            ListingsAction.OnSelectAllExpiredClick -> _state.update {
                it.copy(checkedListingIds = renewableIds()).withVisibleListings()
            }
            ListingsAction.OnSelectAllVisibleClick -> _state.update { state ->
                state.copy(checkedListingIds = state.listings.map { it.id }.toSet()).withVisibleListings()
            }
            ListingsAction.OnClearCheckedClick -> _state.update {
                it.copy(checkedListingIds = emptySet()).withVisibleListings()
            }
            ListingsAction.OnRenewCheckedClick -> renewChecked()
            ListingsAction.OnArchiveCheckedClick -> setCheckedArchived(true)
            ListingsAction.OnUnarchiveCheckedClick -> setCheckedArchived(false)
            ListingsAction.OnDeleteCheckedClick -> _state.update {
                it.copy(isDeleteConfirmOpen = it.checkedListingIds.isNotEmpty())
            }
            ListingsAction.OnDeleteCheckedDismiss -> _state.update { it.copy(isDeleteConfirmOpen = false) }
            ListingsAction.OnDeleteCheckedConfirm -> deleteChecked(alsoOnWillhaben = false)
            ListingsAction.OnDeleteCheckedEverywhereConfirm -> deleteChecked(alsoOnWillhaben = true)
        }
    }

    private fun setCheckedArchived(archived: Boolean) {
        val ids = _state.value.checkedListingIds
        _state.update { it.copy(checkedListingIds = emptySet()).withVisibleListings() }
        viewModelScope.launch {
            ids.forEach { id ->
                listingRepository.setArchived(id, archived).onFailure { error ->
                    _events.send(ListingsEvent.ShowMessage(error.toUiText()))
                    return@launch
                }
            }
        }
    }

    /**
     * Deletes the checked listings locally, and first on willhaben when asked. A listing whose
     * willhaben deletion failed stays locally, so nothing is lost without notice.
     */
    private fun deleteChecked(alsoOnWillhaben: Boolean) {
        val checked = _state.value.checkedListingIds
        _state.update { it.copy(isDeleteConfirmOpen = false, checkedListingIds = emptySet(), isDeleting = true).withVisibleListings() }
        viewModelScope.launch {
            val onWillhaben = if (alsoOnWillhaben) checked.filter { it in deletableOnWillhabenIds } else emptyList()
            val result = if (onWillhaben.isEmpty()) null else statusActions.performAll(onWillhaben, WillhabenStatusAction.DELETE)
            val failedOnWillhaben = onWillhaben.toSet() - result?.succeeded.orEmpty()
            _state.update { it.copy(isDeleting = false) }
            (checked - failedOnWillhaben).forEach { id ->
                listingRepository.deleteListing(id).onFailure { error ->
                    _events.send(ListingsEvent.ShowMessage(error.toUiText()))
                    return@launch
                }
            }
            // Reported after the local deletion: sending waits until the screen reads it
            result?.firstError?.let { _events.send(ListingsEvent.ShowMessage(it.toUiText())) }
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
        val renewable = renewableIds()
        val ids = allListings.map { it.id }.filter { it in _state.value.checkedListingIds && it in renewable }
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

    /** Expired listings that are not archived; only these can be renewed. */
    private fun renewableIds(): Set<Long> =
        allListings.filter { !it.archived && it.id in expiredListingIds }.map { it.id }.toSet()

    private fun ListingsState.withVisibleListings(): ListingsState {
        val query = searchQuery.trim()
        val inFilter = allListings.filter { it.archived == (filter == ListingFilter.ARCHIVED) }
        // Checked listings that left the current filter (archived, deleted) drop out; a search only hides them
        val checked = checkedListingIds intersect inFilter.map { it.id }.toSet()
        val visible = inFilter
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            .map { it.toListItemUi(checked) }
        val renewable = renewableIds()
        return copy(
            listings = visible,
            checkedListingIds = checked,
            renewableCount = renewable.size,
            checkedRenewableCount = (checked intersect renewable).size,
            canDeleteCheckedOnWillhaben = checked.any { it in deletableOnWillhabenIds },
        )
    }

    private fun PublicationStatus.toStateUi(now: Long): WillhabenStateUi? = when {
        syncState == SyncState.REMOTE_GONE -> WillhabenStateUi.GONE
        statusText == null && expiresAt == null -> null
        isDraft -> WillhabenStateUi.DRAFT
        isExpired(now) -> WillhabenStateUi.EXPIRED
        else -> when (statusText) {
            null, "Online", "Aktiv" -> WillhabenStateUi.ONLINE
            "in Warteschlange" -> WillhabenStateUi.IN_REVIEW
            "Verkauft" -> WillhabenStateUi.SOLD
            "Inaktiv", "Deaktiviert" -> WillhabenStateUi.DEACTIVATED
            "Reserviert" -> WillhabenStateUi.RESERVED
            else -> WillhabenStateUi.OTHER
        }
    }

    private fun Listing.toListItemUi(checkedIds: Set<Long>) = ListingListItemUi(
        id = id,
        title = title,
        formattedPrice = priceCents?.let(PriceFormatter::format),
        coverImagePath = images.firstOrNull()?.filePath,
        imageCount = images.size,
        updatedText = formatDateTime(updatedAt),
        willhabenStatus = willhabenStatusByListing[id],
        willhabenState = willhabenStateByListing[id],
        canRenew = !archived && id in expiredListingIds,
        isChecked = id in checkedIds,
    )
}
