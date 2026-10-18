package com.lerchenflo.willhabenupdater.listings.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.domain.onFailure
import com.lerchenflo.willhabenupdater.app.domain.onSuccess
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.Listing
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.utilities.PriceFormatter
import com.lerchenflo.willhabenupdater.utilities.UiText
import com.lerchenflo.willhabenupdater.utilities.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.editor_images_failed

/**
 * Editor of one listing. Every change is saved right away; the text fields are filled from the
 * database only once, so later database updates never overwrite what the user is typing.
 */
class ListingEditorViewModel(
    private val listingId: Long,
    private val listingRepository: ListingRepository,
    private val imagePicker: ImagePicker,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingEditorState())
    val state = _state.asStateFlow()

    private val _events = Channel<ListingEditorEvent>()
    val events = _events.receiveAsFlow()

    /** Last valid price; kept while the price field holds text that is not a price. */
    private var savedPriceCents: Long? = null

    /** Latest content to write; a StateFlow so fast typing only writes the newest value. */
    private val pendingContent = MutableStateFlow<ListingContent?>(null)

    init {
        viewModelScope.launch {
            listingRepository.getListingByIdAsFlow(listingId).collect { listing ->
                if (listing == null) {
                    _state.update { it.copy(isLoading = false, notFound = true) }
                } else {
                    applyFromDatabase(listing)
                }
            }
        }
        viewModelScope.launch {
            pendingContent.filterNotNull().collect { content ->
                listingRepository.updateContent(listingId, content).onFailure { error ->
                    _events.send(ListingEditorEvent.ShowMessage(error.toUiText()))
                }
            }
        }
    }

    fun onAction(action: ListingEditorAction) {
        when (action) {
            is ListingEditorAction.OnTitleChange -> editContent { it.copy(title = action.title) }
            is ListingEditorAction.OnDescriptionChange -> editContent { it.copy(description = action.description) }
            is ListingEditorAction.OnPriceChange -> onPriceChange(action.priceText)
            is ListingEditorAction.OnConditionChange -> editContent { it.copy(condition = action.condition) }
            is ListingEditorAction.OnPostcodeChange -> editContent {
                it.copy(postcode = action.postcode.filter(Char::isDigit).take(4))
            }
            ListingEditorAction.OnAddImagesClick -> viewModelScope.launch {
                addImages(imagePicker.pickImages())
            }
            is ListingEditorAction.OnFilesDropped -> viewModelScope.launch { addImages(action.paths) }
            is ListingEditorAction.OnRemoveImageClick -> viewModelScope.launch {
                listingRepository.removeImage(action.imageId).onFailure { showError(it.toUiText()) }
            }
            is ListingEditorAction.OnMoveImage -> moveImage(action.fromIndex, action.toIndex)
            ListingEditorAction.OnArchiveToggleClick -> toggleArchived()
            ListingEditorAction.OnDuplicateClick -> viewModelScope.launch {
                listingRepository.duplicateListing(listingId)
                    .onSuccess { _events.send(ListingEditorEvent.ListingDuplicated(it)) }
                    .onFailure { showError(it.toUiText()) }
            }
            ListingEditorAction.OnDeleteClick -> _state.update { it.copy(showDeleteDialog = true) }
            ListingEditorAction.OnDeleteDismiss -> _state.update { it.copy(showDeleteDialog = false) }
            ListingEditorAction.OnDeleteConfirm -> {
                _state.update { it.copy(showDeleteDialog = false) }
                viewModelScope.launch {
                    listingRepository.deleteListing(listingId).onFailure { showError(it.toUiText()) }
                }
            }
        }
    }

    private fun applyFromDatabase(listing: Listing) {
        val firstLoad = _state.value.isLoading
        if (firstLoad) savedPriceCents = listing.priceCents
        _state.update { state ->
            val withImages = state.copy(
                isLoading = false,
                notFound = false,
                archived = listing.archived,
                images = listing.images.map { ListingImageUi(id = it.id, filePath = it.filePath) },
            )
            if (!firstLoad) {
                withImages
            } else {
                withImages.copy(
                    title = listing.title,
                    description = listing.description,
                    priceText = listing.priceCents?.let(PriceFormatter::formatAmount).orEmpty(),
                    condition = listing.condition,
                    postcode = listing.postcode,
                )
            }
        }
    }

    private fun onPriceChange(priceText: String) {
        val parsed = PriceFormatter.parse(priceText)
        val invalid = priceText.isNotBlank() && parsed == null
        if (!invalid) savedPriceCents = parsed
        _state.update { it.copy(priceText = priceText, isPriceInvalid = invalid) }
        queueSave()
    }

    private fun editContent(transform: (ListingEditorState) -> ListingEditorState) {
        _state.update(transform)
        queueSave()
    }

    private fun queueSave() {
        val state = _state.value
        pendingContent.value = ListingContent(
            title = state.title,
            description = state.description,
            priceCents = savedPriceCents,
            condition = state.condition,
            postcode = state.postcode,
        )
    }

    private suspend fun addImages(paths: List<String>) {
        if (paths.isEmpty()) return
        listingRepository.addImages(listingId, paths)
            .onSuccess { result ->
                if (result.failedFiles.isNotEmpty()) {
                    val names = result.failedFiles.joinToString { it.path.substringAfterLast('/').substringAfterLast('\\') }
                    showError(UiText.StringResourceText(Res.string.editor_images_failed, arrayOf(names)))
                }
            }
            .onFailure { showError(it.toUiText()) }
    }

    private fun moveImage(fromIndex: Int, toIndex: Int) {
        val images = _state.value.images.toMutableList()
        if (fromIndex !in images.indices || toIndex !in images.indices) return
        images.add(toIndex, images.removeAt(fromIndex))
        _state.update { it.copy(images = images) }
        viewModelScope.launch {
            listingRepository.reorderImages(listingId, images.map { it.id }).onFailure { showError(it.toUiText()) }
        }
    }

    private fun toggleArchived() {
        val archived = !_state.value.archived
        _state.update { it.copy(archived = archived) }
        viewModelScope.launch {
            listingRepository.setArchived(listingId, archived).onFailure { showError(it.toUiText()) }
        }
    }

    private suspend fun showError(message: UiText) {
        _events.send(ListingEditorEvent.ShowMessage(message))
    }
}
