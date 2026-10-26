package com.lerchenflo.relisto.settings.presentation

import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.utilities.UiText

data class SettingsState(
    val postcode: String = "",
    val condition: Condition? = null,
    val pickup: Boolean = true,
    val shipping: Boolean = false,
    val shippingPriceText: String = "",
    val isShippingPriceInvalid: Boolean = false,
    val dataFolder: String = "",
    val autoRenewExpired: Boolean = false,
    val isBackupRunning: Boolean = false,
    /** The chosen backup while the restore confirmation is open. */
    val restoreSource: String? = null,
)

sealed interface SettingsAction {
    data class OnPostcodeChange(val postcode: String) : SettingsAction
    data class OnConditionChange(val condition: Condition?) : SettingsAction
    data class OnPickupChange(val enabled: Boolean) : SettingsAction
    data class OnShippingChange(val enabled: Boolean) : SettingsAction
    data class OnShippingPriceChange(val priceText: String) : SettingsAction
    data class OnAutoRenewChange(val enabled: Boolean) : SettingsAction
    data object OnBackClick : SettingsAction
    data object OnExportBackupClick : SettingsAction
    data object OnRestoreBackupClick : SettingsAction
    data object OnRestoreConfirm : SettingsAction
    data object OnRestoreDismiss : SettingsAction
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: UiText) : SettingsEvent
}
