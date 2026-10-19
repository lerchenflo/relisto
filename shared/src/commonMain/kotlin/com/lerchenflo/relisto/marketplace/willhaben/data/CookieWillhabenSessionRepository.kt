package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.SessionCookie
import com.lerchenflo.relisto.datasource.network.PersistentCookiesStorage
import com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError
import com.lerchenflo.relisto.marketplace.willhaben.domain.SessionState
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** willhaben session = the stored browser cookies, checked against the profile endpoint. */
class CookieWillhabenSessionRepository(
    private val accountDataSource: KtorWillhabenAccountDataSource,
    private val cookiesStorage: PersistentCookiesStorage,
) : WillhabenSessionRepository {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override suspend fun refreshSession(): SessionState {
        if (!cookiesStorage.hasCookies()) return publish(SessionState.LoggedOut)
        return when (val check = accountDataSource.checkLogin()) {
            is Result.Error -> publish(SessionState.Unknown)
            is Result.Success -> when (val login = check.data) {
                is LoginCheck.LoggedIn -> publish(SessionState.LoggedIn(login.displayName))
                LoginCheck.LoggedOut -> renewAndCheck()
            }
        }
    }

    override suspend fun completeLogin(cookies: List<SessionCookie>): EmptyResult<LoginError> {
        cookiesStorage.replaceAll(cookies)
        return when (val check = accountDataSource.checkLogin()) {
            is Result.Error -> {
                publish(SessionState.Unknown)
                Result.Error(LoginError.NETWORK)
            }
            is Result.Success -> if (check.data is LoginCheck.LoggedIn) {
                publish(SessionState.LoggedIn((check.data).displayName))
                Result.Success(Unit)
            } else {
                cookiesStorage.clear()
                publish(SessionState.LoggedOut)
                Result.Error(LoginError.NOT_LOGGED_IN)
            }
        }
    }

    override suspend fun logout() {
        accountDataSource.logout()
        cookiesStorage.clear()
        publish(SessionState.LoggedOut)
    }

    private suspend fun renewAndCheck(): SessionState {
        if (!accountDataSource.renewSession()) {
            // The "stay logged in" session is gone too; a new login is needed
            cookiesStorage.clear()
            return publish(SessionState.LoggedOut)
        }
        return when (val check = accountDataSource.checkLogin()) {
            is Result.Error -> publish(SessionState.Unknown)
            is Result.Success -> publish((check.data as? LoginCheck.LoggedIn)?.let { SessionState.LoggedIn(it.displayName) } ?: SessionState.LoggedOut)
        }
    }

    private fun publish(state: SessionState): SessionState {
        _sessionState.value = state
        return state
    }
}
