package com.lerchenflo.willhabenupdater.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.domain.AvailableUpdate
import com.lerchenflo.willhabenupdater.app.domain.UpdateChecker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Optional "new version available" hint; checked once per app start. */
class AppUpdateViewModel(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()

    private val _events = Channel<AppUpdateEvent>()
    val events = _events.receiveAsFlow()

    private var update: AvailableUpdate? = null

    init {
        viewModelScope.launch {
            update = updateChecker.checkForUpdate()
            _state.value = AppUpdateState(availableVersion = update?.version)
        }
    }

    fun onAction(action: AppUpdateAction) {
        when (action) {
            AppUpdateAction.OnDismissClick -> _state.value = AppUpdateState(availableVersion = null)
            AppUpdateAction.OnDownloadClick -> update?.let { available ->
                viewModelScope.launch { _events.send(AppUpdateEvent.OpenUrl(available.releaseUrl)) }
            }
        }
    }
}
