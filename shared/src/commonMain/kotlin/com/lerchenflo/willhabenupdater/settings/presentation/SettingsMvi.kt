package com.lerchenflo.willhabenupdater.settings.presentation

import com.lerchenflo.willhabenupdater.listings.domain.Condition

data class SettingsState(
    val postcode: String = "",
    val condition: Condition? = null,
    val pickup: Boolean = true,
    val shipping: Boolean = false,
    val shippingPriceText: String = "",
    val isShippingPriceInvalid: Boolean = false,
    val dataFolder: String = "",
)

sealed interface SettingsAction {
    data class OnPostcodeChange(val postcode: String) : SettingsAction
    data class OnConditionChange(val condition: Condition?) : SettingsAction
    data class OnPickupChange(val enabled: Boolean) : SettingsAction
    data class OnShippingChange(val enabled: Boolean) : SettingsAction
    data class OnShippingPriceChange(val priceText: String) : SettingsAction
    data object OnBackClick : SettingsAction
}
