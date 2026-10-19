package com.lerchenflo.relisto.app.presentation

data class AppUpdateState(
    /** Newer release version; null hides the hint and the update button. */
    val availableVersion: String? = null,
    val isBannerDismissed: Boolean = false,
) {
    val isBannerVisible: Boolean get() = availableVersion != null && !isBannerDismissed
}

sealed interface AppUpdateAction {
    data object OnDownloadClick : AppUpdateAction
    data object OnDismissClick : AppUpdateAction
}

sealed interface AppUpdateEvent {
    data class OpenUrl(val url: String) : AppUpdateEvent
}
