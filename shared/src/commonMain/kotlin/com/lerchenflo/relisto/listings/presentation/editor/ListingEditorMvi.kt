package com.lerchenflo.relisto.listings.presentation.editor

import androidx.compose.runtime.Stable
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.utilities.UiText

@Stable
data class ListingEditorState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val description: String = "",
    val priceText: String = "",
    val isPriceInvalid: Boolean = false,
    val condition: Condition? = null,
    val postcode: String = "",
    val images: List<ListingImageUi> = emptyList(),
    val archived: Boolean = false,
    val showDeleteDialog: Boolean = false,
)

data class ListingImageUi(
    val id: Long,
    val filePath: String,
)

sealed interface ListingEditorAction {
    data class OnTitleChange(val title: String) : ListingEditorAction
    data class OnDescriptionChange(val description: String) : ListingEditorAction
    data class OnPriceChange(val priceText: String) : ListingEditorAction
    data class OnConditionChange(val condition: Condition?) : ListingEditorAction
    data class OnPostcodeChange(val postcode: String) : ListingEditorAction
    data object OnAddImagesClick : ListingEditorAction
    data class OnFilesDropped(val paths: List<String>) : ListingEditorAction
    data class OnRemoveImageClick(val imageId: Long) : ListingEditorAction
    data class OnMoveImage(val fromIndex: Int, val toIndex: Int) : ListingEditorAction
    data object OnArchiveToggleClick : ListingEditorAction
    data object OnDuplicateClick : ListingEditorAction
    data object OnDeleteClick : ListingEditorAction
    data object OnDeleteConfirm : ListingEditorAction
    data object OnDeleteDismiss : ListingEditorAction
}

sealed interface ListingEditorEvent {
    data class ShowMessage(val message: UiText) : ListingEditorEvent
    data class ListingDuplicated(val newListingId: Long) : ListingEditorEvent
}
