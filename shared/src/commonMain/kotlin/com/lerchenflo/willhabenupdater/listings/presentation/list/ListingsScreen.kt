package com.lerchenflo.willhabenupdater.listings.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.willhabenupdater.app.navigation.ObserveAsEvents
import com.lerchenflo.willhabenupdater.app.theme.WillhabenUpdaterTheme
import com.lerchenflo.willhabenupdater.listings.presentation.editor.ListingEditorRoot
import com.lerchenflo.willhabenupdater.sharedUi.ListingImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.app_name
import willhabenupdater.shared.generated.resources.listings_cover_image
import willhabenupdater.shared.generated.resources.listings_empty_active
import willhabenupdater.shared.generated.resources.listings_empty_archived
import willhabenupdater.shared.generated.resources.listings_empty_search
import willhabenupdater.shared.generated.resources.listings_filter_active
import willhabenupdater.shared.generated.resources.listings_filter_archived
import willhabenupdater.shared.generated.resources.listings_image_count
import willhabenupdater.shared.generated.resources.listings_new
import willhabenupdater.shared.generated.resources.listings_no_price
import willhabenupdater.shared.generated.resources.listings_search
import willhabenupdater.shared.generated.resources.listings_select_hint
import willhabenupdater.shared.generated.resources.listings_settings
import willhabenupdater.shared.generated.resources.listings_untitled
import willhabenupdater.shared.generated.resources.listings_updated

@Composable
fun ListingsRoot(
    viewModel: ListingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ListingsEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    ListingsScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        editorContent = { listingId ->
            ListingEditorRoot(
                listingId = listingId,
                snackbarHostState = snackbarHostState,
                onListingDuplicated = { newId -> viewModel.onAction(ListingsAction.OnListingClick(newId)) },
            )
        },
    )
}

/** Master-detail: listing list on the left, [editorContent] for the selected listing on the right. */
@Composable
fun ListingsScreen(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    editorContent: @Composable (listingId: Long) -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListingListPane(
                state = state,
                onAction = onAction,
                modifier = Modifier.width(380.dp).fillMaxHeight(),
            )
            VerticalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val selectedId = state.selectedListingId
                if (selectedId == null) {
                    Text(
                        text = stringResource(Res.string.listings_select_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                } else {
                    key(selectedId) { editorContent(selectedId) }
                }
            }
        }
    }
}

@Composable
private fun ListingListPane(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(ListingsAction.OnSettingsClick) }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.listings_settings))
            }
        }
        Button(onClick = { onAction(ListingsAction.OnNewListingClick) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.listings_new))
        }
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onAction(ListingsAction.OnSearchQueryChange(it)) },
            placeholder = { Text(stringResource(Res.string.listings_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filter == ListingFilter.ACTIVE,
                onClick = { onAction(ListingsAction.OnFilterChange(ListingFilter.ACTIVE)) },
                label = { Text(stringResource(Res.string.listings_filter_active)) },
            )
            FilterChip(
                selected = state.filter == ListingFilter.ARCHIVED,
                onClick = { onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED)) },
                label = { Text(stringResource(Res.string.listings_filter_archived)) },
            )
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.listings.isEmpty() -> Text(
                text = stringResource(
                    when {
                        state.searchQuery.isNotBlank() -> Res.string.listings_empty_search
                        state.filter == ListingFilter.ARCHIVED -> Res.string.listings_empty_archived
                        else -> Res.string.listings_empty_active
                    }
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = state.listings, key = { it.id }) { item ->
                    ListingListItem(
                        item = item,
                        selected = item.id == state.selectedListingId,
                        onClick = { onAction(ListingsAction.OnListingClick(item.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListingListItem(
    item: ListingListItemUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = item.title.ifBlank { stringResource(Res.string.listings_untitled) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ListingImage(
                filePath = item.coverImagePath,
                contentDescription = stringResource(Res.string.listings_cover_image, title),
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = item.formattedPrice ?: stringResource(Res.string.listings_no_price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.listings_image_count, item.imageCount) + " · " +
                        stringResource(Res.string.listings_updated, item.updatedText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ListingsScreenPreview() {
    WillhabenUpdaterTheme {
        ListingsScreen(
            state = ListingsState(
                listings = listOf(
                    ListingListItemUi(1, "Kinderfahrrad 20 Zoll", "45 €", null, 4, "18.10.2026 19:05"),
                    ListingListItemUi(2, "Esstisch Eiche massiv", "120 €", null, 2, "17.10.2026 08:30"),
                    ListingListItemUi(3, "", null, null, 0, "16.10.2026 21:12"),
                ),
                selectedListingId = 1,
                isLoading = false,
            ),
            onAction = {},
            snackbarHostState = SnackbarHostState(),
            editorContent = {},
        )
    }
}
