package com.lerchenflo.relisto.listings.presentation.list

import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import relisto.shared.generated.resources.activity_title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import org.jetbrains.compose.resources.StringResource
import relisto.shared.generated.resources.listings_filter_expiring
import relisto.shared.generated.resources.listings_empty_expiring
import relisto.shared.generated.resources.push_checked
import relisto.shared.generated.resources.push_checked_running
import relisto.shared.generated.resources.bulk_status_menu
import relisto.shared.generated.resources.bulk_status_running
import relisto.shared.generated.resources.bulk_status_sold_title
import relisto.shared.generated.resources.bulk_status_sold_text
import relisto.shared.generated.resources.status_action_sold
import relisto.shared.generated.resources.status_action_activate
import relisto.shared.generated.resources.status_action_deactivate
import relisto.shared.generated.resources.status_action_reserve
import relisto.shared.generated.resources.status_action_unreserve
import relisto.shared.generated.resources.status_action_delete
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.listings.presentation.editor.ListingEditorRoot
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.ImportButton
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.LoggedInMarketsRow
import com.lerchenflo.relisto.sharedUi.ListingImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.app_name
import relisto.shared.generated.resources.editor_archive
import relisto.shared.generated.resources.editor_delete
import relisto.shared.generated.resources.editor_delete_cancel
import relisto.shared.generated.resources.editor_delete_confirm
import relisto.shared.generated.resources.editor_unarchive
import relisto.shared.generated.resources.listings_checked_count
import relisto.shared.generated.resources.listings_cover_image
import relisto.shared.generated.resources.listings_delete_checked_text
import relisto.shared.generated.resources.listings_delete_checked_text_willhaben
import relisto.shared.generated.resources.listings_delete_everywhere
import relisto.shared.generated.resources.listings_delete_local
import relisto.shared.generated.resources.listings_delete_checked_title
import relisto.shared.generated.resources.listings_select_all_visible
import relisto.shared.generated.resources.listings_empty_active
import relisto.shared.generated.resources.listings_empty_archived
import relisto.shared.generated.resources.listings_empty_search
import relisto.shared.generated.resources.listings_filter_active
import relisto.shared.generated.resources.listings_filter_archived
import relisto.shared.generated.resources.listings_image_count
import relisto.shared.generated.resources.listings_new
import relisto.shared.generated.resources.listings_no_price
import relisto.shared.generated.resources.listings_search
import relisto.shared.generated.resources.listings_select_hint
import relisto.shared.generated.resources.listings_settings
import relisto.shared.generated.resources.listings_untitled
import relisto.shared.generated.resources.listings_updated
import relisto.shared.generated.resources.listings_willhaben_status
import relisto.shared.generated.resources.market_willhaben
import relisto.shared.generated.resources.renew_check_description
import relisto.shared.generated.resources.renew_checked
import relisto.shared.generated.resources.renew_clear
import relisto.shared.generated.resources.renew_running
import relisto.shared.generated.resources.renew_select_all

@Composable
fun ListingsRoot(
    accountViewModel: AccountViewModel,
    snackbarHostState: SnackbarHostState,
    headerActions: @Composable () -> Unit = {},
    viewModel: ListingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountState by accountViewModel.state.collectAsStateWithLifecycle()
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
        marketActions = { ImportButton(state = accountState, onAction = accountViewModel::onAction) },
        loggedInMarkets = { LoggedInMarketsRow(state = accountState, onAction = accountViewModel::onAction) },
        headerActions = headerActions,
        editorContent = { listingId, onBack ->
            ListingEditorRoot(
                listingId = listingId,
                snackbarHostState = snackbarHostState,
                onListingDuplicated = { newId -> viewModel.onAction(ListingsAction.OnListingClick(newId)) },
                onBack = onBack,
            )
        },
    )
}

/**
 * Master-detail: listing list on the left, [editorContent] for the selected listing on the right.
 * Narrow windows (phones) show one of them at a time; the editor gets a back action to draw.
 */
@Composable
fun ListingsScreen(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    marketActions: @Composable () -> Unit,
    loggedInMarkets: @Composable () -> Unit,
    editorContent: @Composable (listingId: Long, onBack: (() -> Unit)?) -> Unit,
    headerActions: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < COMPACT_WIDTH) {
            CompactListingsLayout(state, onAction, marketActions, loggedInMarkets, headerActions, editorContent)
        } else {
            WideListingsLayout(state, onAction, marketActions, loggedInMarkets, headerActions, editorContent)
        }
    }
}

/** Phone: the list, or the selected listing's editor as its own screen. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompactListingsLayout(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    marketActions: @Composable () -> Unit,
    loggedInMarkets: @Composable () -> Unit,
    headerActions: @Composable () -> Unit,
    editorContent: @Composable (listingId: Long, onBack: (() -> Unit)?) -> Unit,
) {
    val selectedId = state.selectedListingId
    if (selectedId == null) {
        ListingListPane(
            state = state,
            onAction = onAction,
            marketActions = marketActions,
            loggedInMarkets = loggedInMarkets,
            headerActions = headerActions,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    BackHandler { onAction(ListingsAction.OnCloseListingClick) }
    key(selectedId) { editorContent(selectedId) { onAction(ListingsAction.OnCloseListingClick) } }
}

@Composable
private fun WideListingsLayout(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    marketActions: @Composable () -> Unit,
    loggedInMarkets: @Composable () -> Unit,
    headerActions: @Composable () -> Unit,
    editorContent: @Composable (listingId: Long, onBack: (() -> Unit)?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        ListingListPane(
            state = state,
            onAction = onAction,
            marketActions = marketActions,
            loggedInMarkets = loggedInMarkets,
            headerActions = headerActions,
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
                key(selectedId) { editorContent(selectedId, null) }
            }
        }
    }
}

@Composable
private fun ListingListPane(
    state: ListingsState,
    onAction: (ListingsAction) -> Unit,
    marketActions: @Composable () -> Unit,
    loggedInMarkets: @Composable () -> Unit,
    headerActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            marketActions()
            headerActions()
            IconButton(onClick = { onAction(ListingsAction.OnActivityLogClick) }) {
                Icon(Icons.Default.History, contentDescription = stringResource(Res.string.activity_title))
            }
            IconButton(onClick = { onAction(ListingsAction.OnSettingsClick) }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.listings_settings))
            }
        }
        loggedInMarkets()
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
                selected = state.filter == ListingFilter.EXPIRING,
                onClick = { onAction(ListingsAction.OnFilterChange(ListingFilter.EXPIRING)) },
                label = { Text(stringResource(Res.string.listings_filter_expiring)) },
            )
            FilterChip(
                selected = state.filter == ListingFilter.ARCHIVED,
                onClick = { onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED)) },
                label = { Text(stringResource(Res.string.listings_filter_archived)) },
            )
        }
        if (state.listings.isNotEmpty() || state.isRenewing) {
            SelectionBar(state = state, onAction = onAction)
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
                        state.filter == ListingFilter.EXPIRING -> Res.string.listings_empty_expiring
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
                        onCheckedChange = { checked -> onAction(ListingsAction.OnListingCheckedChange(item.id, checked)) },
                    )
                }
            }
        }
    }
}

/** Selecting listings and acting on all of them at once: renew the expired ones, archive, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(state: ListingsState, onAction: (ListingsAction) -> Unit) {
    val runningText = when {
        state.isRenewing -> stringResource(Res.string.renew_running)
        state.isPushing -> stringResource(Res.string.push_checked_running)
        state.runningBulkStatusAction != null -> stringResource(Res.string.bulk_status_running)
        else -> null
    }
    if (runningText != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(runningText)
        }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.checkedListingIds.isEmpty()) {
            TextButton(onClick = { onAction(ListingsAction.OnSelectAllVisibleClick) }) {
                Text(stringResource(Res.string.listings_select_all_visible))
            }
            if (state.renewableCount > 0) {
                OutlinedButton(onClick = { onAction(ListingsAction.OnSelectAllExpiredClick) }) {
                    Text(stringResource(Res.string.renew_select_all, state.renewableCount))
                }
            }
        } else {
            Text(
                stringResource(Res.string.listings_checked_count, state.checkedListingIds.size),
                style = MaterialTheme.typography.labelLarge,
            )
            if (state.checkedRenewableCount > 0) {
                Button(onClick = { onAction(ListingsAction.OnRenewCheckedClick) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.renew_checked, state.checkedRenewableCount))
                }
            }
            if (state.checkedPushableCount > 0) {
                OutlinedButton(onClick = { onAction(ListingsAction.OnPushCheckedClick) }) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.push_checked, state.checkedPushableCount))
                }
            }
            if (state.checkedStatusActions.isNotEmpty()) {
                BulkStatusMenu(actions = state.checkedStatusActions, onAction = onAction)
            }
            if (state.filter != ListingFilter.ARCHIVED) {
                OutlinedButton(onClick = { onAction(ListingsAction.OnArchiveCheckedClick) }) {
                    Text(stringResource(Res.string.editor_archive))
                }
            } else {
                OutlinedButton(onClick = { onAction(ListingsAction.OnUnarchiveCheckedClick) }) {
                    Text(stringResource(Res.string.editor_unarchive))
                }
            }
            OutlinedButton(onClick = { onAction(ListingsAction.OnDeleteCheckedClick) }) {
                Text(stringResource(Res.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { onAction(ListingsAction.OnClearCheckedClick) }) {
                Text(stringResource(Res.string.renew_clear))
            }
        }
    }
    if (state.confirmBulkStatusAction == WillhabenStatusAction.SOLD) {
        AlertDialog(
            onDismissRequest = { onAction(ListingsAction.OnBulkStatusActionDismiss) },
            title = { Text(stringResource(Res.string.bulk_status_sold_title, state.checkedListingIds.size)) },
            text = { Text(stringResource(Res.string.bulk_status_sold_text)) },
            confirmButton = {
                TextButton(onClick = { onAction(ListingsAction.OnBulkStatusActionConfirm) }) {
                    Text(stringResource(Res.string.status_action_sold))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ListingsAction.OnBulkStatusActionDismiss) }) {
                    Text(stringResource(Res.string.editor_delete_cancel))
                }
            },
        )
    }
    if (state.isDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { onAction(ListingsAction.OnDeleteCheckedDismiss) },
            title = { Text(stringResource(Res.string.listings_delete_checked_title, state.checkedListingIds.size)) },
            text = {
                Text(
                    stringResource(
                        if (state.canDeleteCheckedOnWillhaben) Res.string.listings_delete_checked_text_willhaben
                        else Res.string.listings_delete_checked_text
                    )
                )
            },
            confirmButton = {
                // Asked each time (decision 2026-10-19): only here, or here and on willhaben
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onAction(ListingsAction.OnDeleteCheckedConfirm) }) {
                        Text(
                            stringResource(if (state.canDeleteCheckedOnWillhaben) Res.string.listings_delete_local else Res.string.editor_delete_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.canDeleteCheckedOnWillhaben) {
                        TextButton(onClick = { onAction(ListingsAction.OnDeleteCheckedEverywhereConfirm) }) {
                            Text(stringResource(Res.string.listings_delete_everywhere), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ListingsAction.OnDeleteCheckedDismiss) }) {
                    Text(stringResource(Res.string.editor_delete_cancel))
                }
            },
        )
    }
}

/** Status changes on willhaben for the checked listings; each runs on the listings that offer it. */
@Composable
private fun BulkStatusMenu(actions: List<WillhabenStatusAction>, onAction: (ListingsAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(Res.string.bulk_status_menu))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.bulkLabel())) },
                    onClick = {
                        expanded = false
                        onAction(ListingsAction.OnBulkStatusActionClick(action))
                    },
                )
            }
        }
    }
}

private fun WillhabenStatusAction.bulkLabel(): StringResource = when (this) {
    WillhabenStatusAction.ACTIVATE -> Res.string.status_action_activate
    WillhabenStatusAction.DEACTIVATE -> Res.string.status_action_deactivate
    WillhabenStatusAction.RESERVE -> Res.string.status_action_reserve
    WillhabenStatusAction.UNRESERVE -> Res.string.status_action_unreserve
    WillhabenStatusAction.SOLD -> Res.string.status_action_sold
    WillhabenStatusAction.DELETE -> Res.string.status_action_delete
}

/** willhaben logo plus an icon for the listing's state there; the status text is read out for accessibility. */
@Composable
private fun WillhabenStateBadge(state: WillhabenStateUi, statusText: String?) {
    val description = stringResource(Res.string.listings_willhaben_status, statusText ?: state.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Image(
            painter = painterResource(Res.drawable.market_willhaben),
            contentDescription = null,
            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = state.icon(),
            contentDescription = null,
            tint = state.tint(),
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun WillhabenStateUi.icon(): ImageVector = when (this) {
    WillhabenStateUi.ONLINE -> Icons.Default.CheckCircle
    WillhabenStateUi.EXPIRED -> Icons.Default.Timer
    WillhabenStateUi.DRAFT -> Icons.Default.Edit
    WillhabenStateUi.IN_REVIEW -> Icons.Default.HourglassTop
    WillhabenStateUi.SOLD -> Icons.Default.Sell
    WillhabenStateUi.DEACTIVATED -> Icons.Default.PauseCircle
    WillhabenStateUi.RESERVED -> Icons.Default.Bookmark
    WillhabenStateUi.GONE -> Icons.Default.CloudOff
    WillhabenStateUi.OTHER -> Icons.Default.Info
}

@Composable
private fun WillhabenStateUi.tint(): Color = when (this) {
    WillhabenStateUi.ONLINE -> MaterialTheme.colorScheme.primary
    WillhabenStateUi.EXPIRED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ListingListItem(
    item: ListingListItemUi,
    selected: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val title = item.title.ifBlank { stringResource(Res.string.listings_untitled) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val checkDescription = stringResource(Res.string.renew_check_description)
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = checkDescription },
            )
            ListingImage(
                filePath = item.coverImagePath,
                contentDescription = stringResource(Res.string.listings_cover_image, title),
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Takes the free space, so the marketplace state sits at the right edge
                        modifier = Modifier.weight(1f),
                    )
                    item.willhabenState?.let { state ->
                        Spacer(Modifier.width(8.dp))
                        WillhabenStateBadge(state = state, statusText = item.willhabenStatus)
                    }
                }
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
    RelistoTheme {
        ListingsScreen(
            state = ListingsState(
                listings = listOf(
                    ListingListItemUi(1, "Kinderfahrrad 20 Zoll", "45 €", null, 4, "18.10.2026 19:05", willhabenStatus = "Abgelaufen", willhabenState = WillhabenStateUi.EXPIRED, canRenew = true, isChecked = true),
                    ListingListItemUi(2, "Esstisch Eiche massiv", "120 €", null, 2, "17.10.2026 08:30"),
                    ListingListItemUi(3, "", null, null, 0, "16.10.2026 21:12"),
                ),
                selectedListingId = 1,
                isLoading = false,
                checkedListingIds = setOf(1),
                renewableCount = 1,
            ),
            onAction = {},
            marketActions = {},
            loggedInMarkets = {},
            editorContent = { _, _ -> },
        )
    }
}

/** Below this width (phones) list and editor are shown one at a time. */
private val COMPACT_WIDTH = 720.dp
