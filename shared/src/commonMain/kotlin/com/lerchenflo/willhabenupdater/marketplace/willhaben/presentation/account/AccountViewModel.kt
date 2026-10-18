package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.SessionState
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenSessionRepository
import com.lerchenflo.willhabenupdater.utilities.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lerchenflo.willhabenupdater.utilities.toUiText
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.import_done
import willhabenupdater.shared.generated.resources.login_error_browser
import willhabenupdater.shared.generated.resources.login_error_network
import willhabenupdater.shared.generated.resources.login_error_not_logged_in

/** willhaben login state for the top bar; login runs in the login browser window. */
class AccountViewModel(
    private val sessionRepository: WillhabenSessionRepository,
    private val loginBrowser: WillhabenLoginBrowser,
    private val importer: WillhabenImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    private val _events = Channel<AccountEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionRepository.sessionState.collect { session ->
                _state.update {
                    it.copy(
                        isChecking = session is SessionState.Unknown && it.isChecking,
                        isLoggedIn = session is SessionState.LoggedIn,
                        accountName = (session as? SessionState.LoggedIn)?.displayName,
                    )
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.refreshSession()
            _state.update { it.copy(isChecking = false) }
        }
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.OnLoginClick -> login()
            AccountAction.OnImportClick -> importListings()
            AccountAction.OnLogoutClick -> viewModelScope.launch {
                sessionRepository.logout()
                loginBrowser.clearSession()
            }
        }
    }

    private fun login() {
        if (_state.value.isLoggingIn) return
        _state.update { it.copy(isLoggingIn = true) }
        viewModelScope.launch {
            val error = when (val browserResult = loginBrowser.login()) {
                is Result.Error -> browserResult.error
                is Result.Success -> (sessionRepository.completeLogin(browserResult.data) as? Result.Error)?.error
            }
            _state.update { it.copy(isLoggingIn = false) }
            error?.toUiText()?.let { _events.send(AccountEvent.ShowMessage(it)) }
        }
    }

    private fun importListings() {
        if (_state.value.isImporting) return
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val message = when (val result = importer.importMyListings()) {
                is Result.Success -> UiText.StringResourceText(Res.string.import_done, arrayOf(result.data.found))
                is Result.Error -> result.error.toUiText()
            }
            _state.update { it.copy(isImporting = false) }
            _events.send(AccountEvent.ShowMessage(message))
        }
    }

    /** Null for errors that need no message, such as closing the login window. */
    private fun LoginError.toUiText(): UiText? = when (this) {
        LoginError.CANCELLED -> null
        LoginError.BROWSER_UNAVAILABLE -> UiText.StringResourceText(Res.string.login_error_browser)
        LoginError.NOT_LOGGED_IN -> UiText.StringResourceText(Res.string.login_error_not_logged_in)
        LoginError.NETWORK -> UiText.StringResourceText(Res.string.login_error_network)
    }
}
