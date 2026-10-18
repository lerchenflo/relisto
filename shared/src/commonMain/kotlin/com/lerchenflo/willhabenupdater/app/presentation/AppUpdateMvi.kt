package com.lerchenflo.willhabenupdater.app.presentation

data class AppUpdateState(
    /** Version to show in the hint; null hides the hint. */
    val availableVersion: String? = null,
)

sealed interface AppUpdateAction {
    data object OnDownloadClick : AppUpdateAction
    data object OnDismissClick : AppUpdateAction
}

sealed interface AppUpdateEvent {
    data class OpenUrl(val url: String) : AppUpdateEvent
}
