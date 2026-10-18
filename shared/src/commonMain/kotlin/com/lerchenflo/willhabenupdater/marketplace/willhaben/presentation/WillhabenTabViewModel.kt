package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.onFailure
import com.lerchenflo.willhabenupdater.app.domain.onSuccess
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.Marketplace
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatus
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.isExpired
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenPublisher
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import com.lerchenflo.willhabenupdater.utilities.UiText
import com.lerchenflo.willhabenupdater.utilities.formatDateTime
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttributeCodes
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategory
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.willhabenupdater.utilities.PriceFormatter
import com.lerchenflo.willhabenupdater.utilities.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.republish_done

/**
 * willhaben tab of the editor: category, category attributes, location and delivery options.
 * Every change is saved immediately as the listing's willhaben fields.
 */
class WillhabenTabViewModel(
    private val listingId: Long,
    private val catalogRepository: WillhabenCatalogRepository,
    private val fieldsDataSource: WillhabenFieldsLocalDataSource,
    private val listingRepository: ListingRepository,
    private val publicationStatuses: PublicationStatusRepository,
    private val publisher: WillhabenPublisher,
    private val importer: WillhabenImporter,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(WillhabenTabState())
    val state = _state.asStateFlow()

    private val _events = Channel<WillhabenTabEvent>()
    val events = _events.receiveAsFlow()

    private var tree: WillhabenCategoryTree? = null
    private var fields = WillhabenListingFields()
    private var locations: List<WillhabenLocation> = emptyList()

    init {
        viewModelScope.launch {
            fields = fieldsDataSource.getFields(listingId) ?: WillhabenListingFields()
            _state.update {
                it.copy(
                    shippingPriceText = fields.delivery.shippingPriceCents?.let(PriceFormatter::formatAmount).orEmpty()
                ).withFields()
            }
            loadTree()
        }
        viewModelScope.launch {
            publicationStatuses.getStatusesAsFlow()
                .map { statuses -> statuses.firstOrNull { it.listingId == listingId && it.marketplace == Marketplace.WILLHABEN } }
                .distinctUntilChanged()
                .collect { status -> _state.update { it.copy(publication = status?.toUi()) } }
        }
        viewModelScope.launch {
            listingRepository.getListingByIdAsFlow(listingId)
                .filterNotNull()
                .map { it.postcode }
                .distinctUntilChanged()
                .collect { postcode -> onPostcodeChanged(postcode) }
        }
    }

    fun onAction(action: WillhabenTabAction) {
        when (action) {
            WillhabenTabAction.OnRetryLoadTreeClick -> viewModelScope.launch { loadTree() }
            WillhabenTabAction.OnSuggestCategoriesClick -> suggestCategories()
            WillhabenTabAction.OnOpenCategoryPickerClick -> _state.update {
                it.copy(isCategoryPickerOpen = true, categoryQuery = "", categoryResults = searchCategories(""))
            }
            WillhabenTabAction.OnCloseCategoryPicker -> _state.update { it.copy(isCategoryPickerOpen = false) }
            is WillhabenTabAction.OnCategoryQueryChange -> _state.update {
                it.copy(categoryQuery = action.query, categoryResults = searchCategories(action.query))
            }
            is WillhabenTabAction.OnCategorySelect -> selectCategory(action.treeId)
            is WillhabenTabAction.OnAttributeValueToggle -> toggleAttribute(action.attributeCode, action.valueCode)
            is WillhabenTabAction.OnLocationSelect -> {
                val location = locations.firstOrNull { it.id == action.locationId } ?: return
                saveFields(fields.copy(locationId = location.id, locationLabel = location.label))
            }
            is WillhabenTabAction.OnPickupChange -> saveFields(fields.copy(delivery = fields.delivery.copy(pickup = action.enabled)))
            is WillhabenTabAction.OnShippingChange -> saveFields(fields.copy(delivery = fields.delivery.copy(shipping = action.enabled)))
            is WillhabenTabAction.OnPayliveryChange -> saveFields(fields.copy(delivery = fields.delivery.copy(paylivery = action.enabled)))
            is WillhabenTabAction.OnShippingPriceChange -> onShippingPriceChange(action.priceText)
            WillhabenTabAction.OnRepublishClick -> _state.update { it.copy(isRepublishConfirmOpen = true) }
            WillhabenTabAction.OnRepublishDismiss -> _state.update { it.copy(isRepublishConfirmOpen = false) }
            WillhabenTabAction.OnRepublishConfirm -> republish()
        }
    }

    private fun republish() {
        _state.update { it.copy(isRepublishConfirmOpen = false, isRepublishing = true) }
        viewModelScope.launch {
            publisher.republish(listingId)
                .onSuccess {
                    // Reads the new listing's status and expiry from willhaben
                    importer.importMyListings()
                    _state.update { it.copy(isRepublishing = false) }
                    _events.send(WillhabenTabEvent.ShowMessage(UiText.StringResourceText(Res.string.republish_done)))
                }
                .onFailure { error ->
                    _state.update { it.copy(isRepublishing = false) }
                    _events.send(WillhabenTabEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun PublicationStatus.toUi(): PublicationUi? {
        if (statusText == null && expiresAt == null) return null
        return PublicationUi(
            statusText = statusText,
            expiresText = expiresAt?.let(::formatDateTime),
            syncState = syncState,
            canRepublish = isExpired(timeProvider.nowEpochMillis()),
        )
    }

    private suspend fun loadTree() {
        _state.update { it.copy(isTreeLoading = true, treeError = null) }
        when (val result = catalogRepository.getCategoryTree()) {
            is Result.Success -> {
                tree = result.data
                _state.update { it.copy(isTreeLoading = false).withFields() }
            }
            is Result.Error -> _state.update { it.copy(isTreeLoading = false, treeError = result.error.toUiText()) }
        }
    }

    private fun suggestCategories() {
        viewModelScope.launch {
            val title = listingRepository.getListingByIdAsFlow(listingId).first()?.title.orEmpty()
            _state.update { it.copy(isSuggesting = true) }
            catalogRepository.suggestCategories(title)
                .onSuccess { ids ->
                    val suggestions = ids.mapNotNull { id -> tree?.pathOf(id)?.toOption() }
                    _state.update { it.copy(isSuggesting = false, suggestions = suggestions) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isSuggesting = false) }
                    _events.send(WillhabenTabEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun searchCategories(query: String): List<CategoryOptionUi> {
        val leaves = tree?.leaves() ?: return emptyList()
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        return leaves
            .filter { path -> words.all { word -> path.any { it.label.lowercase().contains(word) } } }
            .take(MAX_CATEGORY_RESULTS)
            .map { it.toOption() }
    }

    private fun selectCategory(treeId: Int) {
        val path = tree?.pathOf(treeId) ?: return
        val allowedCodes = path.last().attributeReferences.map { it.code }.toSet()
        _state.update { it.copy(isCategoryPickerOpen = false, suggestions = emptyList()) }
        saveFields(
            fields.copy(
                categoryPath = path.map { WillhabenCategoryRef(treeId = it.treeId, code = it.code, label = it.label) },
                attributes = fields.attributes.filterKeys { it in allowedCodes },
            )
        )
    }

    private fun toggleAttribute(attributeCode: String, valueCode: String) {
        val category = selectedCategory() ?: return
        val reference = category.attributeReferences.firstOrNull { it.code == attributeCode } ?: return
        val current = fields.attributes[attributeCode].orEmpty()
        val updated = when {
            valueCode in current -> current - valueCode
            reference.multiSelect -> current + valueCode
            else -> listOf(valueCode)
        }
        val attributes = if (updated.isEmpty()) fields.attributes - attributeCode else fields.attributes + (attributeCode to updated)
        saveFields(fields.copy(attributes = attributes))
    }

    private suspend fun onPostcodeChanged(postcode: String) {
        _state.update { it.copy(postcode = postcode) }
        if (!POSTCODE.matches(postcode)) {
            locations = emptyList()
            _state.update { it.copy(locations = emptyList()) }
            return
        }
        _state.update { it.copy(isLoadingLocations = true) }
        catalogRepository.findLocations(postcode)
            .onSuccess { found ->
                locations = found
                _state.update { state ->
                    state.copy(isLoadingLocations = false, locations = found.map { LocationOptionUi(it.id, it.label) })
                }
                val selectedStillValid = found.any { it.id == fields.locationId }
                when {
                    selectedStillValid -> Unit
                    found.size == 1 -> saveFields(fields.copy(locationId = found.single().id, locationLabel = found.single().label))
                    fields.locationId != null -> saveFields(fields.copy(locationId = null, locationLabel = null))
                }
            }
            .onFailure { error ->
                _state.update { it.copy(isLoadingLocations = false) }
                _events.send(WillhabenTabEvent.ShowMessage(error.toUiText()))
            }
    }

    private fun onShippingPriceChange(priceText: String) {
        val parsed = PriceFormatter.parse(priceText)
        val invalid = priceText.isNotBlank() && parsed == null
        _state.update { it.copy(shippingPriceText = priceText, isShippingPriceInvalid = invalid) }
        if (!invalid) saveFields(fields.copy(delivery = fields.delivery.copy(shippingPriceCents = parsed)))
    }

    private fun saveFields(updated: WillhabenListingFields) {
        fields = updated
        _state.update { it.withFields() }
        viewModelScope.launch {
            fieldsDataSource.saveFields(listingId, updated).onFailure { error ->
                _events.send(WillhabenTabEvent.ShowMessage(error.toUiText()))
            }
        }
    }

    private fun selectedCategory(): WillhabenCategory? {
        val leafId = fields.categoryPath.lastOrNull()?.treeId ?: return null
        return tree?.pathOf(leafId)?.last()
    }

    /** Copies the stored fields into the state; the shipping price text is left alone while typing. */
    private fun WillhabenTabState.withFields(): WillhabenTabState = copy(
        categoryPathText = fields.categoryPath.takeIf { it.isNotEmpty() }?.joinToString(PATH_SEPARATOR) { it.label },
        attributeFields = attributeFields(),
        selectedLocationId = fields.locationId,
        selectedLocationLabel = fields.locationLabel,
        pickup = fields.delivery.pickup,
        shipping = fields.delivery.shipping,
        paylivery = fields.delivery.paylivery,
    )

    private fun attributeFields(): List<AttributeFieldUi> {
        val category = selectedCategory() ?: return emptyList()
        val definitions = tree?.attributesByCode ?: return emptyList()
        return category.attributeReferences
            .filter { it.code != WillhabenAttributeCodes.CONDITION && it.code != WillhabenAttributeCodes.HANDOVER }
            .mapNotNull { reference ->
                val attribute = definitions[reference.code] ?: return@mapNotNull null
                AttributeFieldUi(
                    code = reference.code,
                    label = attribute.label,
                    required = reference.required,
                    multiSelect = reference.multiSelect,
                    options = attribute.values.map { AttributeOptionUi(code = it.code, label = it.label) },
                    selectedCodes = fields.attributes[reference.code].orEmpty(),
                )
            }
    }

    private fun List<WillhabenCategory>.toOption() = CategoryOptionUi(
        treeId = last().treeId,
        pathText = joinToString(PATH_SEPARATOR) { it.label },
    )

    private companion object {
        const val PATH_SEPARATOR = " › "
        const val MAX_CATEGORY_RESULTS = 50
        val POSTCODE = Regex("""\d{4}""")
    }
}
