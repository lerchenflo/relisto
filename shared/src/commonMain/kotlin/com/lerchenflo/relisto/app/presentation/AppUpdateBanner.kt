package com.lerchenflo.relisto.app.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.theme.RelistoTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.update_available
import relisto.shared.generated.resources.update_dismiss
import relisto.shared.generated.resources.update_download

@Composable
fun AppUpdateRoot(
    viewModel: AppUpdateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AppUpdateEvent.OpenUrl -> uriHandler.openUri(event.url)
        }
    }

    AppUpdateBanner(state = state, onAction = viewModel::onAction)
}

/** Icon in the home header while a newer release exists; opens the release page. */
@Composable
fun AppUpdateButton(
    state: AppUpdateState,
    onAction: (AppUpdateAction) -> Unit,
) {
    val version = state.availableVersion ?: return
    IconButton(onClick = { onAction(AppUpdateAction.OnDownloadClick) }) {
        Icon(
            Icons.Default.NewReleases,
            contentDescription = stringResource(Res.string.update_available, version),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun AppUpdateButtonRoot(viewModel: AppUpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppUpdateButton(state = state, onAction = viewModel::onAction)
}

/** Thin hint above the app; shown only when a newer release exists and until dismissed. */
@Composable
fun AppUpdateBanner(
    state: AppUpdateState,
    onAction: (AppUpdateAction) -> Unit,
) {
    val version = state.availableVersion ?: return
    if (!state.isBannerVisible) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                text = stringResource(Res.string.update_available, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = { onAction(AppUpdateAction.OnDownloadClick) }) {
                Text(stringResource(Res.string.update_download))
            }
            IconButton(onClick = { onAction(AppUpdateAction.OnDismissClick) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.update_dismiss))
            }
        }
    }
}

@Preview
@Composable
private fun AppUpdateBannerPreview() {
    RelistoTheme {
        AppUpdateBanner(state = AppUpdateState(availableVersion = "1.0.12"), onAction = {})
    }
}
