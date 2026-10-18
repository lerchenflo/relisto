package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.willhabenupdater.app.navigation.ObserveAsEvents
import com.lerchenflo.willhabenupdater.app.theme.WillhabenUpdaterTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.account_checking
import willhabenupdater.shared.generated.resources.account_logged_in_as
import willhabenupdater.shared.generated.resources.account_logging_in
import willhabenupdater.shared.generated.resources.account_login
import willhabenupdater.shared.generated.resources.account_logout

@Composable
fun AccountRoot(
    snackbarHostState: SnackbarHostState,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AccountEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message.resolve()) }
        }
    }

    AccountBar(state = state, onAction = viewModel::onAction)
}

/** willhaben login state: login button, or the account name with a logout button. */
@Composable
fun AccountBar(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when {
            state.isChecking -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.account_checking), style = MaterialTheme.typography.bodySmall)
            }
            state.isLoggedIn -> {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.account_logged_in_as, state.accountName.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(AccountAction.OnLogoutClick) }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(Res.string.account_logout))
                }
            }
            else -> FilledTonalButton(
                onClick = { onAction(AccountAction.OnLoginClick) },
                enabled = !state.isLoggingIn,
                modifier = Modifier.fillMaxWidth(),
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
private fun AccountBarPreview() {
    WillhabenUpdaterTheme {
        AccountBar(
            state = AccountState(isChecking = false, isLoggedIn = true, accountName = "Flo"),
            onAction = {},
        )
    }
}
