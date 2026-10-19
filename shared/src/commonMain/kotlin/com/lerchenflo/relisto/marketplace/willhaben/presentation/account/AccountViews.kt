package com.lerchenflo.relisto.marketplace.willhaben.presentation.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.theme.RelistoTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.account_checking
import relisto.shared.generated.resources.account_logged_in
import relisto.shared.generated.resources.account_logged_in_as
import relisto.shared.generated.resources.account_logging_in
import relisto.shared.generated.resources.account_login
import relisto.shared.generated.resources.account_logout
import relisto.shared.generated.resources.account_status_logged_in
import relisto.shared.generated.resources.import_button
import relisto.shared.generated.resources.import_running
import relisto.shared.generated.resources.market_willhaben

/** Shows the account messages on [snackbarHostState]; only the visible screen collects them. */
@Composable
fun ObserveAccountEvents(viewModel: AccountViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AccountEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message.resolve()) }
        }
    }
}

/** Header button: import from willhaben, only while logged in. */
@Composable
fun ImportButton(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
) {
    if (!state.isLoggedIn) return
    IconButton(onClick = { onAction(AccountAction.OnImportClick) }, enabled = !state.isImporting) {
        if (state.isImporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.CloudDownload, contentDescription = stringResource(Res.string.import_button))
        }
    }
}

/** One logo per marketplace the user is logged in to; login and logout are in the settings. */
@Composable
fun LoggedInMarketsRow(
    state: AccountState,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoggedIn) return
    val description = stringResource(Res.string.account_status_logged_in)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Image(
            painter = painterResource(Res.drawable.market_willhaben),
            contentDescription = null,
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text("willhaben", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun AccountSettingsRoot(
    viewModel: AccountViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAccountEvents(viewModel, snackbarHostState)
    AccountSection(state = state, onAction = viewModel::onAction)
}

/** willhaben login in the settings: login button, or the account name with logout. */
@Composable
fun AccountSection(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when {
            state.isChecking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.account_checking), style = MaterialTheme.typography.bodySmall)
            }
            state.isLoggedIn -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.accountName?.let { stringResource(Res.string.account_logged_in_as, it) }
                        ?: stringResource(Res.string.account_logged_in),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = { onAction(AccountAction.OnLogoutClick) }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.account_logout))
                }
            }
            else -> FilledTonalButton(
                onClick = { onAction(AccountAction.OnLoginClick) },
                enabled = !state.isLoggingIn,
            ) {
                if (state.isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.isLoggingIn) Res.string.account_logging_in else Res.string.account_login))
            }
        }
    }
}

@Preview
@Composable
private fun LoggedInMarketsRowPreview() {
    RelistoTheme {
        Row {
            ImportButton(state = AccountState(isChecking = false, isLoggedIn = true), onAction = {})
            LoggedInMarketsRow(state = AccountState(isChecking = false, isLoggedIn = true))
        }
    }
}

@Preview
@Composable
private fun AccountSectionPreview() {
    RelistoTheme {
        AccountSection(
            state = AccountState(isChecking = false, isLoggedIn = true, accountName = "Flo"),
            onAction = {},
        )
    }
}
