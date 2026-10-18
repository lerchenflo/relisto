package com.lerchenflo.willhabenupdater.settings.presentation

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
import com.lerchenflo.willhabenupdater.app.theme.WillhabenUpdaterTheme
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.sharedUi.ConditionSelector
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.editor_price_invalid
import willhabenupdater.shared.generated.resources.settings_back
import willhabenupdater.shared.generated.resources.settings_condition
import willhabenupdater.shared.generated.resources.settings_data_folder
import willhabenupdater.shared.generated.resources.settings_defaults
import willhabenupdater.shared.generated.resources.settings_pickup
import willhabenupdater.shared.generated.resources.settings_postcode
import willhabenupdater.shared.generated.resources.settings_shipping
import willhabenupdater.shared.generated.resources.settings_shipping_price
import willhabenupdater.shared.generated.resources.settings_title

@Composable
fun SettingsRoot(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
            Text(stringResource(Res.string.settings_data_folder), style = MaterialTheme.typography.titleMedium)
            Text(state.dataFolder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Preview
@Composable
private fun SettingsScreenPreview() {
    WillhabenUpdaterTheme {
        SettingsScreen(
            state = SettingsState(
                postcode = "6850",
                condition = Condition.USED,
                pickup = true,
                shipping = true,
                shippingPriceText = "5,90",
                dataFolder = "/Users/flo/Library/Application Support/WillhabenUpdater",
            ),
            onAction = {},
        )
    }
}
