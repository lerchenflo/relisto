package com.lerchenflo.relisto.marketplace.willhaben.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenVertical
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.editor_delete_cancel
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
        state.publication?.let { publication ->
            PublicationSection(publication = publication, isRepublishing = state.isRepublishing, onAction = onAction)
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
    if (state.isRepublishConfirmOpen) {
        RepublishConfirmDialog(onAction = onAction)
    }
}

@Composable
private fun PublicationSection(publication: PublicationUi, isRepublishing: Boolean, onAction: (WillhabenTabAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.willhaben_listing_title), style = MaterialTheme.typography.titleSmall)
        publication.statusText?.let { Text(stringResource(Res.string.willhaben_listing_status, it)) }
        publication.expiresText?.let { Text(stringResource(Res.string.willhaben_listing_expires, it)) }
        Text(
            stringResource(Res.string.willhaben_listing_sync, stringResource(publication.syncState.label())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isRepublishing) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.republish_running))
            }
        } else if (publication.canRepublish) {
            Button(onClick = { onAction(WillhabenTabAction.OnRepublishClick) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.republish_button))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RepublishConfirmDialog(onAction: (WillhabenTabAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(WillhabenTabAction.OnRepublishDismiss) },
        title = { Text(stringResource(Res.string.republish_confirm_title)) },
        text = { Text(stringResource(Res.string.republish_confirm_text)) },
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
                    canRepublish = true,
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
