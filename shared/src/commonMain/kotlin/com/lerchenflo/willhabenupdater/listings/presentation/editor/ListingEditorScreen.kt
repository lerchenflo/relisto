package com.lerchenflo.willhabenupdater.listings.presentation.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.willhabenupdater.app.navigation.ObserveAsEvents
import com.lerchenflo.willhabenupdater.app.theme.WillhabenUpdaterTheme
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.WillhabenTabRoot
import com.lerchenflo.willhabenupdater.sharedUi.ConditionSelector
import com.lerchenflo.willhabenupdater.sharedUi.ImageDropArea
import com.lerchenflo.willhabenupdater.sharedUi.ListingImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.editor_archive
import willhabenupdater.shared.generated.resources.editor_archived_badge
import willhabenupdater.shared.generated.resources.editor_condition
import willhabenupdater.shared.generated.resources.editor_delete
import willhabenupdater.shared.generated.resources.editor_delete_cancel
import willhabenupdater.shared.generated.resources.editor_delete_confirm
import willhabenupdater.shared.generated.resources.editor_delete_text
import willhabenupdater.shared.generated.resources.editor_delete_title
import willhabenupdater.shared.generated.resources.editor_description
import willhabenupdater.shared.generated.resources.editor_duplicate
import willhabenupdater.shared.generated.resources.editor_image
import willhabenupdater.shared.generated.resources.editor_image_cover
import willhabenupdater.shared.generated.resources.editor_image_remove
import willhabenupdater.shared.generated.resources.editor_images
import willhabenupdater.shared.generated.resources.editor_images_add
import willhabenupdater.shared.generated.resources.editor_images_hint
import willhabenupdater.shared.generated.resources.editor_marketplaces
import willhabenupdater.shared.generated.resources.editor_not_found
import willhabenupdater.shared.generated.resources.editor_postcode
import willhabenupdater.shared.generated.resources.editor_price
import willhabenupdater.shared.generated.resources.editor_price_invalid
import willhabenupdater.shared.generated.resources.editor_title
import willhabenupdater.shared.generated.resources.editor_title_counter
import willhabenupdater.shared.generated.resources.editor_unarchive
import willhabenupdater.shared.generated.resources.willhaben_tab

@Composable
fun ListingEditorRoot(
    listingId: Long,
    snackbarHostState: SnackbarHostState,
    onListingDuplicated: (Long) -> Unit,
    viewModel: ListingEditorViewModel = koinViewModel(key = "editor-$listingId") { parametersOf(listingId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ListingEditorEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
            is ListingEditorEvent.ListingDuplicated -> onListingDuplicated(event.newListingId)
        }
    }

    ListingEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        marketplaceContent = {
            WillhabenTabRoot(listingId = listingId, snackbarHostState = snackbarHostState)
        },
    )
}

@Composable
fun ListingEditorScreen(
    state: ListingEditorState,
    onAction: (ListingEditorAction) -> Unit,
    marketplaceContent: @Composable () -> Unit,
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.notFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.editor_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorActions(state = state, onAction = onAction)
            ContentFields(state = state, onAction = onAction)
            ImagesSection(state = state, onAction = onAction)
            HorizontalDivider()
            Text(stringResource(Res.string.editor_marketplaces), style = MaterialTheme.typography.titleMedium)
            PrimaryTabRow(selectedTabIndex = 0, modifier = Modifier.widthIn(max = 400.dp)) {
                Tab(selected = true, onClick = {}, text = { Text(stringResource(Res.string.willhaben_tab)) })
            }
            marketplaceContent()
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onAction(ListingEditorAction.OnDeleteDismiss) },
            title = { Text(stringResource(Res.string.editor_delete_title)) },
            text = { Text(stringResource(Res.string.editor_delete_text)) },
            dismissButton = {
                TextButton(onClick = { onAction(ListingEditorAction.OnDeleteDismiss) }) {
                    Text(stringResource(Res.string.editor_delete_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(ListingEditorAction.OnDeleteConfirm) }) {
                    Text(stringResource(Res.string.editor_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun EditorActions(state: ListingEditorState, onAction: (ListingEditorAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.archived) {
            AssistChip(onClick = {}, label = { Text(stringResource(Res.string.editor_archived_badge)) })
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { onAction(ListingEditorAction.OnDuplicateClick) }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.editor_duplicate))
        }
        OutlinedButton(onClick = { onAction(ListingEditorAction.OnArchiveToggleClick) }) {
            Icon(if (state.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (state.archived) Res.string.editor_unarchive else Res.string.editor_archive))
        }
        OutlinedButton(onClick = { onAction(ListingEditorAction.OnDeleteClick) }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.editor_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ContentFields(state: ListingEditorState, onAction: (ListingEditorAction) -> Unit) {
    OutlinedTextField(
        value = state.title,
        onValueChange = { onAction(ListingEditorAction.OnTitleChange(it)) },
        label = { Text(stringResource(Res.string.editor_title)) },
        supportingText = { Text(stringResource(Res.string.editor_title_counter, state.title.length)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.priceText,
            onValueChange = { onAction(ListingEditorAction.OnPriceChange(it)) },
            label = { Text(stringResource(Res.string.editor_price)) },
            isError = state.isPriceInvalid,
            supportingText = if (state.isPriceInvalid) {
                { Text(stringResource(Res.string.editor_price_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(200.dp),
        )
        OutlinedTextField(
            value = state.postcode,
            onValueChange = { onAction(ListingEditorAction.OnPostcodeChange(it)) },
            label = { Text(stringResource(Res.string.editor_postcode)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(160.dp),
        )
    }
    ConditionSelector(
        label = stringResource(Res.string.editor_condition),
        selected = state.condition,
        onSelect = { onAction(ListingEditorAction.OnConditionChange(it)) },
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = { onAction(ListingEditorAction.OnDescriptionChange(it)) },
        label = { Text(stringResource(Res.string.editor_description)) },
        minLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ImagesSection(state: ListingEditorState, onAction: (ListingEditorAction) -> Unit) {
    ImageDropArea(
        onFilesDropped = { onAction(ListingEditorAction.OnFilesDropped(it)) },
        modifier = Modifier.fillMaxWidth(),
    ) { isDraggingOver ->
        val borderColor = if (isDraggingOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(if (isDraggingOver) 2.dp else 1.dp, borderColor), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.editor_images), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.editor_images_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { onAction(ListingEditorAction.OnAddImagesClick) }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.editor_images_add))
                }
            }
            if (state.images.isNotEmpty()) {
                ReorderableImageRow(images = state.images, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ReorderableImageRow(images: List<ListingImageUi>, onAction: (ListingEditorAction) -> Unit) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onAction(ListingEditorAction.OnMoveImage(fromIndex = from.index, toIndex = to.index))
    }
    LazyRow(state = lazyListState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(items = images, key = { _, image -> image.id }) { index, image ->
            ReorderableItem(reorderState, key = image.id) { isDragging ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = if (isDragging) 8.dp else 0.dp,
                    modifier = Modifier.draggableHandle(),
                ) {
                    Box(modifier = Modifier.size(140.dp)) {
                        ListingImage(
                            filePath = image.filePath,
                            contentDescription = stringResource(Res.string.editor_image, index + 1),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        )
                        FilledTonalIconButton(
                            onClick = { onAction(ListingEditorAction.OnRemoveImageClick(image.id)) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.editor_image_remove))
                        }
                        if (index == 0) {
                            Text(
                                text = stringResource(Res.string.editor_image_cover),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Preview
@Composable
private fun ListingEditorScreenPreview() {
    WillhabenUpdaterTheme {
        ListingEditorScreen(
            state = ListingEditorState(
                isLoading = false,
                title = "Kinderfahrrad 20 Zoll",
                description = "Gut erhaltenes Kinderfahrrad, 7 Gänge, neue Reifen.",
                priceText = "45",
                condition = Condition.USED,
                postcode = "6850",
                images = listOf(ListingImageUi(1, "/tmp/1.jpg"), ListingImageUi(2, "/tmp/2.jpg")),
            ),
            onAction = {},
            marketplaceContent = {},
        )
    }
}
