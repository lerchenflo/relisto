package com.lerchenflo.willhabenupdater.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.settings.domain.AppSettings
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import com.lerchenflo.willhabenupdater.utilities.PriceFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataSource: SettingsDataSource,
    private val navigator: Navigator,
    appDirectories: AppDirectories,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(dataFolder = appDirectories.dataDir))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataSource.getSettings()
            _state.update {
                it.copy(
                    postcode = settings.defaultPostcode,
                    condition = settings.defaultCondition,
                    pickup = settings.defaultPickup,
                    shipping = settings.defaultShipping,
                    shippingPriceText = settings.defaultShippingPriceCents?.let(PriceFormatter::formatAmount).orEmpty(),
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnPostcodeChange -> {
                val postcode = action.postcode.filter(Char::isDigit).take(4)
                _state.update { it.copy(postcode = postcode) }
                save { it.copy(defaultPostcode = postcode) }
            }
            is SettingsAction.OnConditionChange -> {
                _state.update { it.copy(condition = action.condition) }
                save { it.copy(defaultCondition = action.condition) }
            }
            is SettingsAction.OnPickupChange -> {
                _state.update { it.copy(pickup = action.enabled) }
                save { it.copy(defaultPickup = action.enabled) }
            }
            is SettingsAction.OnShippingChange -> {
                _state.update { it.copy(shipping = action.enabled) }
                save { it.copy(defaultShipping = action.enabled) }
            }
            is SettingsAction.OnShippingPriceChange -> {
                val parsed = PriceFormatter.parse(action.priceText)
                val invalid = action.priceText.isNotBlank() && parsed == null
                _state.update { it.copy(shippingPriceText = action.priceText, isShippingPriceInvalid = invalid) }
                if (!invalid) save { it.copy(defaultShippingPriceCents = parsed) }
            }
            SettingsAction.OnBackClick -> viewModelScope.launch { navigator.navigateBack() }
        }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsDataSource.updateSettings(transform) }
    }
}
