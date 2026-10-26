package com.lerchenflo.relisto.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.AccountSettingsRoot
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.relisto.sharedUi.ConditionSelector
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.editor_price_invalid
import relisto.shared.generated.resources.settings_auto_renew
import relisto.shared.generated.resources.settings_auto_renew_hint
import relisto.shared.generated.resources.settings_back
import relisto.shared.generated.resources.settings_condition
import relisto.shared.generated.resources.settings_data_folder
import relisto.shared.generated.resources.settings_defaults
import relisto.shared.generated.resources.settings_pickup
import relisto.shared.generated.resources.settings_postcode
import relisto.shared.generated.resources.settings_shipping
import relisto.shared.generated.resources.settings_shipping_price
import relisto.shared.generated.resources.settings_title
import relisto.shared.generated.resources.settings_willhaben
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import kotlinx.coroutines.launch
import relisto.shared.generated.resources.backup_export
import relisto.shared.generated.resources.backup_restore
import relisto.shared.generated.resources.backup_restore_confirm
import relisto.shared.generated.resources.backup_restore_confirm_text
import relisto.shared.generated.resources.backup_restore_confirm_title
import relisto.shared.generated.resources.backup_text
import relisto.shared.generated.resources.backup_title
import relisto.shared.generated.resources.editor_delete_cancel

@Composable
fun SettingsRoot(
    accountViewModel: AccountViewModel,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        accountContent = { AccountSettingsRoot(viewModel = accountViewModel) },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    accountContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(SettingsAction.OnBackClick) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.settings_back))
            }
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(modifier = Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.settings_defaults), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.postcode,
                onValueChange = { onAction(SettingsAction.OnPostcodeChange(it)) },
                label = { Text(stringResource(Res.string.settings_postcode)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            ConditionSelector(
                label = stringResource(Res.string.settings_condition),
                selected = state.condition,
                onSelect = { onAction(SettingsAction.OnConditionChange(it)) },
            )
            CheckboxRow(stringResource(Res.string.settings_pickup), state.pickup) { onAction(SettingsAction.OnPickupChange(it)) }
            CheckboxRow(stringResource(Res.string.settings_shipping), state.shipping) { onAction(SettingsAction.OnShippingChange(it)) }
            OutlinedTextField(
                value = state.shippingPriceText,
                onValueChange = { onAction(SettingsAction.OnShippingPriceChange(it)) },
                label = { Text(stringResource(Res.string.settings_shipping_price)) },
                isError = state.isShippingPriceInvalid,
                supportingText = if (state.isShippingPriceInvalid) {
                    { Text(stringResource(Res.string.editor_price_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(220.dp),
            )
            Text(stringResource(Res.string.settings_willhaben), style = MaterialTheme.typography.titleMedium)
            accountContent()
            CheckboxRow(stringResource(Res.string.settings_auto_renew), state.autoRenewExpired) {
                onAction(SettingsAction.OnAutoRenewChange(it))
            }
            Text(
                stringResource(Res.string.settings_auto_renew_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(Res.string.settings_data_folder), style = MaterialTheme.typography.titleMedium)
            Text(state.dataFolder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BackupSection(isRunning = state.isBackupRunning, onAction = onAction)
        }
    }
    if (state.restoreSource != null) {
        RestoreConfirmDialog(onAction = onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupSection(isRunning: Boolean, onAction: (SettingsAction) -> Unit) {
    Text(stringResource(Res.string.backup_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.backup_text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onAction(SettingsAction.OnExportBackupClick) }, enabled = !isRunning) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.backup_export))
        }
        OutlinedButton(onClick = { onAction(SettingsAction.OnRestoreBackupClick) }, enabled = !isRunning) {
            Icon(Icons.Default.Restore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.backup_restore))
        }
        if (isRunning) CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterVertically))
    }
}

@Composable
private fun RestoreConfirmDialog(onAction: (SettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.OnRestoreDismiss) },
        title = { Text(stringResource(Res.string.backup_restore_confirm_title)) },
        text = { Text(stringResource(Res.string.backup_restore_confirm_text)) },
        confirmButton = {
            TextButton(onClick = { onAction(SettingsAction.OnRestoreConfirm) }) {
                Text(stringResource(Res.string.backup_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SettingsAction.OnRestoreDismiss) }) {
                Text(stringResource(Res.string.editor_delete_cancel))
            }
        },
    )
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

@Preview
@Composable
private fun SettingsScreenPreview() {
    RelistoTheme {
        SettingsScreen(
            state = SettingsState(
                postcode = "6850",
                condition = Condition.USED,
                pickup = true,
                shipping = true,
                shippingPriceText = "5,90",
                dataFolder = "/Users/flo/Library/Application Support/Relisto",
            ),
            onAction = {},
            accountContent = {},
        )
    }
}
