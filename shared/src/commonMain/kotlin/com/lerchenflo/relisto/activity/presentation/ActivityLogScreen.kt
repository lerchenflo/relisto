package com.lerchenflo.relisto.activity.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.activity_action_activate
import relisto.shared.generated.resources.activity_action_deactivate
import relisto.shared.generated.resources.activity_action_delete
import relisto.shared.generated.resources.activity_action_publish
import relisto.shared.generated.resources.activity_action_push
import relisto.shared.generated.resources.activity_action_republish
import relisto.shared.generated.resources.activity_action_reserve
import relisto.shared.generated.resources.activity_action_sold
import relisto.shared.generated.resources.activity_action_take_remote
import relisto.shared.generated.resources.activity_action_unreserve
import relisto.shared.generated.resources.activity_all_marketplaces
import relisto.shared.generated.resources.activity_back
import relisto.shared.generated.resources.activity_deleted_listing
import relisto.shared.generated.resources.activity_empty
import relisto.shared.generated.resources.activity_failed
import relisto.shared.generated.resources.activity_for_listing
import relisto.shared.generated.resources.activity_all_listings
import relisto.shared.generated.resources.activity_only_failed
import relisto.shared.generated.resources.activity_succeeded
import relisto.shared.generated.resources.activity_title

@Composable
fun ActivityLogRoot(
    listingId: Long?,
    viewModel: ActivityLogViewModel = koinViewModel(key = "activity-$listingId") { parametersOf(listingId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActivityLogScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun ActivityLogScreen(
    state: ActivityLogState,
    onAction: (ActivityLogAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(ActivityLogAction.OnBackClick) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.activity_back))
            }
            Text(stringResource(Res.string.activity_title), style = MaterialTheme.typography.titleLarge)
        }
        Filters(state = state, onAction = onAction)
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            state.entries.isEmpty() -> Text(
                stringResource(Res.string.activity_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(modifier = Modifier.widthIn(max = 900.dp)) {
                items(items = state.entries, key = { it.id }) { entry ->
                    EntryRow(entry = entry, showMarketplace = state.marketplaces.size > 1)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Filters(state: ActivityLogState, onAction: (ActivityLogAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        state.listingTitle?.let { title ->
            InputChip(
                selected = true,
                onClick = { onAction(ActivityLogAction.OnClearListingFilterClick) },
                label = { Text(stringResource(Res.string.activity_for_listing, title)) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.activity_all_listings)) },
            )
        }
        FilterChip(
            selected = state.onlyFailed,
            onClick = { onAction(ActivityLogAction.OnOnlyFailedChange(!state.onlyFailed)) },
            label = { Text(stringResource(Res.string.activity_only_failed)) },
        )
        if (state.marketplaces.size > 1) {
            FilterChip(
                selected = state.selectedMarketplace == null,
                onClick = { onAction(ActivityLogAction.OnMarketplaceSelect(null)) },
                label = { Text(stringResource(Res.string.activity_all_marketplaces)) },
            )
            state.marketplaces.forEach { marketplace ->
                FilterChip(
                    selected = state.selectedMarketplace == marketplace,
                    onClick = { onAction(ActivityLogAction.OnMarketplaceSelect(marketplace)) },
                    label = { Text(marketplace.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ActivityEntryUi, showMarketplace: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Icon(
            if (entry.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = stringResource(if (entry.success) Res.string.activity_succeeded else Res.string.activity_failed),
            tint = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.listingTitle ?: stringResource(Res.string.activity_deleted_listing), style = MaterialTheme.typography.bodyLarge)
            val action = stringResource(entry.action.label())
            Text(
                if (showMarketplace) "${entry.marketplace.displayName()} · $action" else action,
                style = MaterialTheme.typography.bodyMedium,
            )
            entry.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(entry.timeText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Marketplace.displayName(): String = when (this) {
    Marketplace.WILLHABEN -> "willhaben"
}

private fun ActivityAction.label(): StringResource = when (this) {
    ActivityAction.PUSH_CHANGES -> Res.string.activity_action_push
    ActivityAction.TAKE_REMOTE -> Res.string.activity_action_take_remote
    ActivityAction.REPUBLISH -> Res.string.activity_action_republish
    ActivityAction.PUBLISH -> Res.string.activity_action_publish
    ActivityAction.ACTIVATE -> Res.string.activity_action_activate
    ActivityAction.DEACTIVATE -> Res.string.activity_action_deactivate
    ActivityAction.MARK_RESERVED -> Res.string.activity_action_reserve
    ActivityAction.REMOVE_RESERVED -> Res.string.activity_action_unreserve
    ActivityAction.MARK_SOLD -> Res.string.activity_action_sold
    ActivityAction.DELETE -> Res.string.activity_action_delete
}

@Preview
@Composable
private fun ActivityLogScreenPreview() {
    RelistoTheme {
        ActivityLogScreen(
            state = ActivityLogState(
                isLoading = false,
                entries = listOf(
                    ActivityEntryUi(2, "22.10.2026 10:15", "Rasentraktor Kart", Marketplace.WILLHABEN, ActivityAction.DEACTIVATE, true, null),
                    ActivityEntryUi(1, "22.10.2026 10:12", "Rasentraktor Kart", Marketplace.WILLHABEN, ActivityAction.PUSH_CHANGES, false, "Conflict"),
                ),
                marketplaces = listOf(Marketplace.WILLHABEN),
            ),
            onAction = {},
        )
    }
}
