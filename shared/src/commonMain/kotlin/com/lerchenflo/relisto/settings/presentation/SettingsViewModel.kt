package com.lerchenflo.relisto.settings.presentation

import androidx.lifecycle.ViewModel
import relisto.shared.generated.resources.backup_restore_ready
import relisto.shared.generated.resources.backup_restore_failed
import relisto.shared.generated.resources.backup_failed
import relisto.shared.generated.resources.backup_done
import relisto.shared.generated.resources.Res
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import com.lerchenflo.relisto.utilities.formatIsoDate
import com.lerchenflo.relisto.utilities.UiText
import com.lerchenflo.relisto.utilities.TimeProvider
import com.lerchenflo.relisto.backup.domain.BackupRepository
import com.lerchenflo.relisto.backup.domain.BackupFilePicker
import com.lerchenflo.relisto.app.domain.Result
import androidx.lifecycle.viewModelScope
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.settings.domain.SettingsDataSource
import com.lerchenflo.relisto.utilities.AppDirectories
import com.lerchenflo.relisto.utilities.PriceFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataSource: SettingsDataSource,
    private val navigator: Navigator,
    appDirectories: AppDirectories,
    private val backupRepository: BackupRepository,
    private val backupFilePicker: BackupFilePicker,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(dataFolder = appDirectories.dataDir))
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

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
                    autoRenewExpired = settings.autoRenewExpired,
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnExportBackupClick -> exportBackup()
            SettingsAction.OnRestoreBackupClick -> viewModelScope.launch {
                val source = backupFilePicker.pickBackupFile() ?: return@launch
                _state.update { it.copy(restoreSource = source) }
            }
            SettingsAction.OnRestoreConfirm -> restoreBackup()
            SettingsAction.OnRestoreDismiss -> _state.update { it.copy(restoreSource = null) }
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
            is SettingsAction.OnAutoRenewChange -> {
                _state.update { it.copy(autoRenewExpired = action.enabled) }
                save { it.copy(autoRenewExpired = action.enabled) }
            }
            SettingsAction.OnBackClick -> viewModelScope.launch { navigator.navigateBack() }
        }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsDataSource.updateSettings(transform) }
    }

    private fun exportBackup() {
        viewModelScope.launch {
            val name = "Relisto-Backup-${formatIsoDate(timeProvider.nowEpochMillis())}.zip"
            val destination = backupFilePicker.pickSaveTarget(name) ?: return@launch
            _state.update { it.copy(isBackupRunning = true) }
            val message = when (backupRepository.export(destination)) {
                is Result.Success -> Res.string.backup_done
                is Result.Error -> Res.string.backup_failed
            }
            _state.update { it.copy(isBackupRunning = false) }
            _events.send(SettingsEvent.ShowMessage(UiText.StringResourceText(message)))
        }
    }

    private fun restoreBackup() {
        val source = _state.value.restoreSource ?: return
        _state.update { it.copy(restoreSource = null, isBackupRunning = true) }
        viewModelScope.launch {
            val message = when (backupRepository.prepareRestore(source)) {
                is Result.Success -> Res.string.backup_restore_ready
                is Result.Error -> Res.string.backup_restore_failed
            }
            _state.update { it.copy(isBackupRunning = false) }
            _events.send(SettingsEvent.ShowMessage(UiText.StringResourceText(message)))
        }
    }
}
