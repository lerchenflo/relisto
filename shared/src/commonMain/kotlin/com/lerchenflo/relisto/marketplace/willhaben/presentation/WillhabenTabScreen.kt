package com.lerchenflo.relisto.marketplace.willhaben.presentation

import androidx.compose.material.icons.filled.ErrorOutline
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import relisto.shared.generated.resources.publish_button
import relisto.shared.generated.resources.publish_continue_button
import relisto.shared.generated.resources.publish_hint
import relisto.shared.generated.resources.publish_interrupted_hint
import relisto.shared.generated.resources.publish_confirm_title
import relisto.shared.generated.resources.publish_confirm_text
import relisto.shared.generated.resources.publish_confirm
import relisto.shared.generated.resources.publish_progress_title
import relisto.shared.generated.resources.publish_progress_close
import relisto.shared.generated.resources.publish_step_product
import relisto.shared.generated.resources.publish_step_template
import relisto.shared.generated.resources.publish_step_presave
import relisto.shared.generated.resources.publish_step_images
import relisto.shared.generated.resources.publish_step_finish
import relisto.shared.generated.resources.publish_step_reload
import relisto.shared.generated.resources.publish_step_activate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import relisto.shared.generated.resources.activity_open
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenVertical
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.conflict_button
import relisto.shared.generated.resources.conflict_keep_local
import relisto.shared.generated.resources.conflict_local
import relisto.shared.generated.resources.conflict_no_price
import relisto.shared.generated.resources.conflict_remote
import relisto.shared.generated.resources.conflict_running
import relisto.shared.generated.resources.conflict_take_remote
import relisto.shared.generated.resources.conflict_text
import relisto.shared.generated.resources.conflict_title
import relisto.shared.generated.resources.editor_delete_cancel
import relisto.shared.generated.resources.push_button
import relisto.shared.generated.resources.push_running
import relisto.shared.generated.resources.editor_price_invalid
import relisto.shared.generated.resources.republish_button
import relisto.shared.generated.resources.republish_confirm
import relisto.shared.generated.resources.republish_confirm_text
import relisto.shared.generated.resources.republish_confirm_title
import relisto.shared.generated.resources.republish_running
import relisto.shared.generated.resources.sync_state_conflict
import relisto.shared.generated.resources.sync_state_error
import relisto.shared.generated.resources.sync_state_local_changes
import relisto.shared.generated.resources.sync_state_local_only
import relisto.shared.generated.resources.sync_state_publishing
import relisto.shared.generated.resources.sync_state_remote_changed
import relisto.shared.generated.resources.sync_state_synced
import relisto.shared.generated.resources.willhaben_attribute_choose
import relisto.shared.generated.resources.willhaben_attribute_required
import relisto.shared.generated.resources.willhaben_attributes
import relisto.shared.generated.resources.willhaben_attributes_hint
import relisto.shared.generated.resources.finish_draft_button
import relisto.shared.generated.resources.finish_draft_confirm_text
import relisto.shared.generated.resources.finish_draft_confirm_title
import relisto.shared.generated.resources.finish_draft_running
import relisto.shared.generated.resources.status_action_activate
import relisto.shared.generated.resources.status_action_deactivate
import relisto.shared.generated.resources.status_action_delete
import relisto.shared.generated.resources.status_action_delete_text
import relisto.shared.generated.resources.status_action_delete_title
import relisto.shared.generated.resources.status_action_reserve
import relisto.shared.generated.resources.status_action_sold
import relisto.shared.generated.resources.status_action_sold_text
import relisto.shared.generated.resources.status_action_sold_title
import relisto.shared.generated.resources.status_action_unreserve
import relisto.shared.generated.resources.sync_state_remote_gone
import relisto.shared.generated.resources.willhaben_category
import relisto.shared.generated.resources.willhaben_vertical
import relisto.shared.generated.resources.willhaben_vertical_hint
import relisto.shared.generated.resources.willhaben_vertical_marketplace
import relisto.shared.generated.resources.willhaben_vertical_motor
import relisto.shared.generated.resources.willhaben_vertical_real_estate
import relisto.shared.generated.resources.willhaben_category_all
import relisto.shared.generated.resources.willhaben_category_choose
import relisto.shared.generated.resources.willhaben_category_close
import relisto.shared.generated.resources.willhaben_category_no_results
import relisto.shared.generated.resources.willhaben_category_none
import relisto.shared.generated.resources.willhaben_category_search
import relisto.shared.generated.resources.willhaben_category_suggest
import relisto.shared.generated.resources.willhaben_category_suggestions
import relisto.shared.generated.resources.willhaben_delivery
import relisto.shared.generated.resources.willhaben_listing_expires
import relisto.shared.generated.resources.willhaben_listing_status
import relisto.shared.generated.resources.willhaben_listing_sync
import relisto.shared.generated.resources.willhaben_listing_title
import relisto.shared.generated.resources.willhaben_location
import relisto.shared.generated.resources.willhaben_location_need_postcode
import relisto.shared.generated.resources.willhaben_location_none
import relisto.shared.generated.resources.willhaben_paylivery
import relisto.shared.generated.resources.willhaben_pickup
import relisto.shared.generated.resources.willhaben_shipping
import relisto.shared.generated.resources.willhaben_shipping_price
import relisto.shared.generated.resources.willhaben_tree_loading
import relisto.shared.generated.resources.willhaben_tree_retry

/** Option lists up to this size are shown as chips, longer ones as a dropdown. */
private const val MAX_CHIP_OPTIONS = 12

@Composable
fun WillhabenTabRoot(
    listingId: Long,
    snackbarHostState: SnackbarHostState,
    viewModel: WillhabenTabViewModel = koinViewModel(key = "willhaben-$listingId") { parametersOf(listingId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is WillhabenTabEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    WillhabenTabScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun WillhabenTabScreen(
    state: WillhabenTabState,
    onAction: (WillhabenTabAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
        // Only marketplace listings are created by the app (decision 2026-10-22)
        if (state.publishState != null && state.vertical == WillhabenVertical.MARKETPLACE) {
            PublishSection(publishState = state.publishState, onAction = onAction)
        }
        state.publication?.let { publication ->
            PublicationSection(publication = publication, state = state, onAction = onAction) {
                StatusActionButtons(actions = state.statusActions, running = state.runningStatusAction, onAction = onAction)
            }
        }
        if (state.vertical == WillhabenVertical.MARKETPLACE) {
            CategorySection(state = state, onAction = onAction)
            if (state.attributeFields.isNotEmpty()) {
                AttributesSection(state = state, onAction = onAction)
            }
            LocationSection(state = state, onAction = onAction)
            DeliverySection(state = state, onAction = onAction)
        } else {
            VerticalSection(vertical = state.vertical)
            LocationSection(state = state, onAction = onAction)
        }
    }

    if (state.isCategoryPickerOpen) {
        CategoryPickerDialog(state = state, onAction = onAction)
    }
    state.confirmStatusAction?.let { action -> StatusActionConfirmDialog(action = action, onAction = onAction) }
    if (state.isRepublishConfirmOpen) {
        RepublishConfirmDialog(action = state.publication?.renewAction ?: RenewAction.REPUBLISH, onAction = onAction)
    }
    state.conflict?.let { conflict -> ConflictDialog(conflict = conflict, onAction = onAction) }
    if (state.isPublishConfirmOpen) PublishConfirmDialog(onAction = onAction)
    state.publishProgress?.let { progress -> PublishProgressDialog(progress = progress, onAction = onAction) }
}

@Composable
private fun PublishSection(publishState: PublishState, onAction: (WillhabenTabAction) -> Unit) {
    val interrupted = publishState == PublishState.INTERRUPTED
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.willhaben_listing_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(if (interrupted) Res.string.publish_interrupted_hint else Res.string.publish_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onAction(WillhabenTabAction.OnPublishClick) }) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (interrupted) Res.string.publish_continue_button else Res.string.publish_button))
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PublishConfirmDialog(onAction: (WillhabenTabAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnPublishDismiss) },
        title = { Text(stringResource(Res.string.publish_confirm_title)) },
        text = { Text(stringResource(Res.string.publish_confirm_text)) },
        confirmButton = {
            Button(onClick = { onAction(WillhabenTabAction.OnPublishConfirm) }) {
                Text(stringResource(Res.string.publish_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(WillhabenTabAction.OnPublishDismiss) }) {
                Text(stringResource(Res.string.editor_delete_cancel))
            }
        },
    )
}

/** The steps of the publish: done ones checked, the running one with a spinner, a failed one in red. */
@Composable
private fun PublishProgressDialog(progress: PublishProgressUi, onAction: (WillhabenTabAction) -> Unit) {
    val steps = buildList {
        add(PublishStep.PRODUCT)
        add(PublishStep.TEMPLATE)
        add(PublishStep.PRESAVE)
        if (progress.hasImages) add(PublishStep.IMAGES)
        add(PublishStep.FINISH)
        add(PublishStep.RELOAD)
        add(PublishStep.ACTIVATE)
    }
    val currentIndex = progress.current?.let(steps::indexOf) ?: -1
    AlertDialog(
        // Not dismissable while running: the publish goes on anyway
        onDismissRequest = { if (progress.error != null) onAction(WillhabenTabAction.OnPublishProgressDismiss) },
        title = { Text(stringResource(Res.string.publish_progress_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            when {
                                index < currentIndex -> Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                index == currentIndex && progress.error != null ->
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                index == currentIndex -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(step.label()),
                            color = if (index > currentIndex) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        )
                    }
                }
                progress.error?.let { error ->
                    Text(error.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (progress.error != null) {
                TextButton(onClick = { onAction(WillhabenTabAction.OnPublishProgressDismiss) }) {
                    Text(stringResource(Res.string.publish_progress_close))
                }
            }
        },
    )
}

private fun PublishStep.label(): StringResource = when (this) {
    PublishStep.PRODUCT -> Res.string.publish_step_product
    PublishStep.TEMPLATE -> Res.string.publish_step_template
    PublishStep.PRESAVE, PublishStep.COPY -> Res.string.publish_step_presave
    PublishStep.IMAGES -> Res.string.publish_step_images
    PublishStep.FINISH, PublishStep.EDIT -> Res.string.publish_step_finish
    PublishStep.RELOAD, PublishStep.PACKAGE -> Res.string.publish_step_reload
    PublishStep.ACTIVATE -> Res.string.publish_step_activate
}

@Composable
private fun PublicationSection(
    publication: PublicationUi,
    state: WillhabenTabState,
    onAction: (WillhabenTabAction) -> Unit,
    statusActions: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.willhaben_listing_title), style = MaterialTheme.typography.titleSmall)
        publication.statusText?.let { Text(stringResource(Res.string.willhaben_listing_status, it)) }
        publication.expiresText?.let { Text(stringResource(Res.string.willhaben_listing_expires, it)) }
        Text(
            stringResource(Res.string.willhaben_listing_sync, stringResource(publication.syncState.label())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val action = publication.renewAction
        when {
            state.isRepublishing -> RunningRow(
                stringResource(if (action == RenewAction.FINISH_DRAFT) Res.string.finish_draft_running else Res.string.republish_running)
            )
            state.isPushing -> RunningRow(stringResource(Res.string.push_running))
            state.isResolvingConflict -> RunningRow(stringResource(Res.string.conflict_running))
            publication.hasConflict -> Button(onClick = { onAction(WillhabenTabAction.OnResolveConflictClick) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.CompareArrows, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.conflict_button))
            }
            publication.canPush -> Button(onClick = { onAction(WillhabenTabAction.OnPushChangesClick) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.push_button))
            }
        }
        if (!state.isRepublishing && action != null) {
            Button(onClick = { onAction(WillhabenTabAction.OnRepublishClick) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(if (action == RenewAction.FINISH_DRAFT) Icons.Default.Edit else Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (action == RenewAction.FINISH_DRAFT) Res.string.finish_draft_button else Res.string.republish_button))
            }
        }
        statusActions()
        TextButton(onClick = { onAction(WillhabenTabAction.OnShowActivityClick) }) {
            Icon(Icons.Default.History, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.activity_open))
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RunningRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** Both versions side by side (stacked on phones); the confirming choice keeps the local version. */
@Composable
private fun ConflictDialog(conflict: ConflictUi, onAction: (WillhabenTabAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnConflictDismiss) },
        title = { Text(stringResource(Res.string.conflict_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(Res.string.conflict_text))
                BoxWithConstraints {
                    if (maxWidth < 480.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            VersionCard(stringResource(Res.string.conflict_local), conflict.local, Modifier.fillMaxWidth())
                            VersionCard(stringResource(Res.string.conflict_remote), conflict.remote, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VersionCard(stringResource(Res.string.conflict_local), conflict.local, Modifier.weight(1f))
                            VersionCard(stringResource(Res.string.conflict_remote), conflict.remote, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAction(WillhabenTabAction.OnKeepLocalClick) }) {
                Text(stringResource(Res.string.conflict_keep_local))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAction(WillhabenTabAction.OnConflictDismiss) }) {
                    Text(stringResource(Res.string.editor_delete_cancel))
                }
                TextButton(onClick = { onAction(WillhabenTabAction.OnTakeRemoteClick) }) {
                    Text(stringResource(Res.string.conflict_take_remote))
                }
            }
        },
    )
}

@Composable
private fun VersionCard(label: String, version: ListingVersionUi, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(version.title, style = MaterialTheme.typography.titleSmall)
            Text(version.priceText.ifEmpty { stringResource(Res.string.conflict_no_price) }, style = MaterialTheme.typography.bodyMedium)
            Text(
                version.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** willhaben's status changes for this listing, as offered at the last import. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusActionButtons(
    actions: List<WillhabenStatusAction>,
    running: WillhabenStatusAction?,
    onAction: (WillhabenTabAction) -> Unit,
) {
    if (actions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        actions.forEach { action ->
            OutlinedButton(
                onClick = { onAction(WillhabenTabAction.OnStatusActionClick(action)) },
                enabled = running == null,
            ) {
                if (running == action) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(action.label()),
                    color = if (action == WillhabenStatusAction.DELETE) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
private fun StatusActionConfirmDialog(action: WillhabenStatusAction, onAction: (WillhabenTabAction) -> Unit) {
    val isDelete = action == WillhabenStatusAction.DELETE
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnStatusActionDismiss) },
        title = { Text(stringResource(if (isDelete) Res.string.status_action_delete_title else Res.string.status_action_sold_title)) },
        text = { Text(stringResource(if (isDelete) Res.string.status_action_delete_text else Res.string.status_action_sold_text)) },
        confirmButton = {
            TextButton(onClick = { onAction(WillhabenTabAction.OnStatusActionConfirm) }) {
                Text(stringResource(action.label()), color = if (isDelete) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(WillhabenTabAction.OnStatusActionDismiss) }) {
                Text(stringResource(Res.string.editor_delete_cancel))
            }
        },
    )
}

private fun WillhabenStatusAction.label(): StringResource = when (this) {
    WillhabenStatusAction.ACTIVATE -> Res.string.status_action_activate
    WillhabenStatusAction.DEACTIVATE -> Res.string.status_action_deactivate
    WillhabenStatusAction.RESERVE -> Res.string.status_action_reserve
    WillhabenStatusAction.UNRESERVE -> Res.string.status_action_unreserve
    WillhabenStatusAction.SOLD -> Res.string.status_action_sold
    WillhabenStatusAction.DELETE -> Res.string.status_action_delete
}

@Composable
private fun RepublishConfirmDialog(action: RenewAction, onAction: (WillhabenTabAction) -> Unit) {
    val finish = action == RenewAction.FINISH_DRAFT
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnRepublishDismiss) },
        title = { Text(stringResource(if (finish) Res.string.finish_draft_confirm_title else Res.string.republish_confirm_title)) },
        text = { Text(stringResource(if (finish) Res.string.finish_draft_confirm_text else Res.string.republish_confirm_text)) },
        confirmButton = {
            Button(onClick = { onAction(WillhabenTabAction.OnRepublishConfirm) }) {
                Text(stringResource(Res.string.republish_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(WillhabenTabAction.OnRepublishDismiss) }) {
                Text(stringResource(Res.string.editor_delete_cancel))
            }
        },
    )
}

private fun SyncState.label(): StringResource = when (this) {
    SyncState.LOCAL_ONLY -> Res.string.sync_state_local_only
    SyncState.SYNCED -> Res.string.sync_state_synced
    SyncState.LOCAL_CHANGES -> Res.string.sync_state_local_changes
    SyncState.REMOTE_CHANGED -> Res.string.sync_state_remote_changed
    SyncState.CONFLICT -> Res.string.sync_state_conflict
    SyncState.PUBLISHING -> Res.string.sync_state_publishing
    SyncState.ERROR -> Res.string.sync_state_error
    SyncState.REMOTE_GONE -> Res.string.sync_state_remote_gone
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(state: WillhabenTabState, onAction: (WillhabenTabAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.willhaben_category), style = MaterialTheme.typography.titleSmall)
        when {
            state.isTreeLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.willhaben_tree_loading))
            }
            state.treeError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.treeError.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(WillhabenTabAction.OnRetryLoadTreeClick) }) {
                    Text(stringResource(Res.string.willhaben_tree_retry))
                }
            }
            else -> {
                Text(
                    text = state.categoryPathText ?: stringResource(Res.string.willhaben_category_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.categoryPathText == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAction(WillhabenTabAction.OnOpenCategoryPickerClick) }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.willhaben_category_choose))
                    }
                    OutlinedButton(
                        onClick = { onAction(WillhabenTabAction.OnSuggestCategoriesClick) },
                        enabled = !state.isSuggesting,
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.willhaben_category_suggest))
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    Text(stringResource(Res.string.willhaben_category_suggestions), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { onAction(WillhabenTabAction.OnCategorySelect(suggestion.treeId)) },
                                label = { Text(suggestion.pathText) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributesSection(state: WillhabenTabState, onAction: (WillhabenTabAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.willhaben_attributes), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.willhaben_attributes_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.attributeFields.forEach { field ->
            AttributeField(field = field, onToggle = { value -> onAction(WillhabenTabAction.OnAttributeValueToggle(field.code, value)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeField(field: AttributeFieldUi, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (field.required) stringResource(Res.string.willhaben_attribute_required, field.label) else field.label,
            style = MaterialTheme.typography.labelLarge,
        )
        if (field.options.size <= MAX_CHIP_OPTIONS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = option.code in field.selectedCodes,
                        onClick = { onToggle(option.code) },
                        label = { Text(option.label) },
                    )
                }
            }
        } else {
            AttributeDropdown(field = field, onToggle = onToggle)
        }
    }
}

@Composable
private fun AttributeDropdown(field: AttributeFieldUi, onToggle: (String) -> Unit) {
    // Open/closed is pure UI chrome; the selection itself lives in the ViewModel
    var expanded by remember { mutableStateOf(false) }
    val selectedLabels = field.options.filter { it.code in field.selectedCodes }.joinToString { it.label }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedLabels.ifEmpty { stringResource(Res.string.willhaben_attribute_choose) })
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 400.dp)) {
            field.options.forEach { option ->
                val selected = option.code in field.selectedCodes
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onToggle(option.code)
                        if (!field.multiSelect) expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationSection(state: WillhabenTabState, onAction: (WillhabenTabAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.willhaben_location), style = MaterialTheme.typography.titleSmall)
        when {
            state.postcode.length != 4 -> Text(
                stringResource(Res.string.willhaben_location_need_postcode),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.isLoadingLocations -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            state.locations.isEmpty() -> Text(
                stringResource(Res.string.willhaben_location_none, state.postcode),
                color = MaterialTheme.colorScheme.error,
            )
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.locations.forEach { location ->
                    FilterChip(
                        selected = location.id == state.selectedLocationId,
                        onClick = { onAction(WillhabenTabAction.OnLocationSelect(location.id)) },
                        label = { Text(location.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliverySection(state: WillhabenTabState, onAction: (WillhabenTabAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.willhaben_delivery), style = MaterialTheme.typography.titleSmall)
        CheckboxRow(
            label = stringResource(Res.string.willhaben_pickup),
            checked = state.pickup,
            onCheckedChange = { onAction(WillhabenTabAction.OnPickupChange(it)) },
        )
        CheckboxRow(
            label = stringResource(Res.string.willhaben_shipping),
            checked = state.shipping,
            onCheckedChange = { onAction(WillhabenTabAction.OnShippingChange(it)) },
        )
        if (state.shipping) {
            OutlinedTextField(
                value = state.shippingPriceText,
                onValueChange = { onAction(WillhabenTabAction.OnShippingPriceChange(it)) },
                label = { Text(stringResource(Res.string.willhaben_shipping_price)) },
                isError = state.isShippingPriceInvalid,
                supportingText = if (state.isShippingPriceInvalid) {
                    { Text(stringResource(Res.string.editor_price_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(start = 48.dp).width(220.dp),
            )
            CheckboxRow(
                label = stringResource(Res.string.willhaben_paylivery),
                checked = state.paylivery,
                onCheckedChange = { onAction(WillhabenTabAction.OnPayliveryChange(it)) },
            )
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCheckedChange(!checked) }.padding(end = 8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

/** Step-by-step selection from the top level down; typing switches to a search over all categories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(state: WillhabenTabState, onAction: (WillhabenTabAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnCloseCategoryPicker) },
        title = { Text(stringResource(Res.string.willhaben_category_choose)) },
        text = {
            // Fixed height, so the dialog does not jump while browsing or typing
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(480.dp)) {
                OutlinedTextField(
                    value = state.categoryQuery,
                    onValueChange = { onAction(WillhabenTabAction.OnCategoryQueryChange(it)) },
                    placeholder = { Text(stringResource(Res.string.willhaben_category_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.categoryQuery.isNotBlank() && state.categoryResults.isEmpty() ->
                        Text(stringResource(Res.string.willhaben_category_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.categoryQuery.isNotBlank() -> LazyColumn {
                        items(items = state.categoryResults, key = { it.treeId }) { option ->
                            CategoryRow(
                                text = option.pathText,
                                hasChildren = false,
                                onClick = { onAction(WillhabenTabAction.OnCategorySelect(option.treeId)) },
                            )
                        }
                    }
                    else -> {
                        FlowRow(verticalArrangement = Arrangement.Center) {
                            CategoryBreadcrumbItem(
                                label = stringResource(Res.string.willhaben_category_all),
                                isCurrent = state.categoryBreadcrumb.isEmpty(),
                                onClick = { onAction(WillhabenTabAction.OnCategoryBreadcrumbClick(null)) },
                            )
                            state.categoryBreadcrumb.forEachIndexed { index, node ->
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                                CategoryBreadcrumbItem(
                                    label = node.label,
                                    isCurrent = index == state.categoryBreadcrumb.lastIndex,
                                    onClick = { onAction(WillhabenTabAction.OnCategoryBreadcrumbClick(node.treeId)) },
                                )
                            }
                        }
                        HorizontalDivider()
                        LazyColumn {
                            items(items = state.categoryLevel, key = { it.treeId }) { node ->
                                CategoryRow(
                                    text = node.label,
                                    hasChildren = node.hasChildren,
                                    onClick = {
                                        if (node.hasChildren) {
                                            onAction(WillhabenTabAction.OnCategoryBrowse(node.treeId))
                                        } else {
                                            onAction(WillhabenTabAction.OnCategorySelect(node.treeId))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(WillhabenTabAction.OnCloseCategoryPicker) }) {
                Text(stringResource(Res.string.willhaben_category_close))
            }
        },
    )
}

/** Listings outside the marketplace (e.g. motorbikes) have no marketplace category or delivery options. */
@Composable
private fun VerticalSection(vertical: WillhabenVertical) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.willhaben_vertical, stringResource(vertical.label())), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.willhaben_vertical_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun WillhabenVertical.label(): StringResource = when (this) {
    WillhabenVertical.MARKETPLACE -> Res.string.willhaben_vertical_marketplace
    WillhabenVertical.MOTOR -> Res.string.willhaben_vertical_motor
    WillhabenVertical.REAL_ESTATE -> Res.string.willhaben_vertical_real_estate
}

@Composable
private fun CategoryBreadcrumbItem(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !isCurrent) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CategoryRow(text: String, hasChildren: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(text = text, modifier = Modifier.weight(1f))
        if (hasChildren) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    HorizontalDivider()
}

@Preview
@Composable
private fun WillhabenTabScreenPreview() {
    RelistoTheme {
        WillhabenTabScreen(
            state = WillhabenTabState(
                isTreeLoading = false,
                publication = PublicationUi(
                    statusText = "Abgelaufen",
                    expiresText = "02.12.2026 10:00",
                    syncState = SyncState.SYNCED,
                    renewAction = RenewAction.REPUBLISH,
                ),
                categoryPathText = "Sport / Sportgeräte › Fahrräder › Kinderfahrräder",
                attributeFields = listOf(
                    AttributeFieldUi(
                        code = "COLOR",
                        label = "Farbe",
                        required = true,
                        multiSelect = true,
                        options = listOf(AttributeOptionUi("RED", "Rot"), AttributeOptionUi("BLUE", "Blau")),
                        selectedCodes = listOf("RED"),
                    )
                ),
                postcode = "6850",
                locations = listOf(LocationOptionUi(1, "Dornbirn")),
                selectedLocationId = 1,
                pickup = true,
                shipping = true,
                shippingPriceText = "5,90",
            ),
            onAction = {},
        )
    }
}
