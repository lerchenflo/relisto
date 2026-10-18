package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.datasource.network.PersistentCookiesStorage
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.SessionState
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAccount
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenSessionRepository
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
        return when (val check = accountDataSource.fetchAccount()) {
            is Result.Error -> publish(SessionState.Unknown)
            is Result.Success -> check.data?.let { publish(SessionState.LoggedIn(it)) } ?: renewAndCheck()
        }
    }

    override suspend fun completeLogin(cookies: List<SessionCookie>): Result<WillhabenAccount, LoginError> {
        cookiesStorage.replaceAll(cookies)
        return when (val check = accountDataSource.fetchAccount()) {
            is Result.Error -> {
                publish(SessionState.Unknown)
                Result.Error(LoginError.NETWORK)
            }
            is Result.Success -> {
                val account = check.data
                if (account == null) {
                    cookiesStorage.clear()
                    publish(SessionState.LoggedOut)
                    Result.Error(LoginError.NOT_LOGGED_IN)
                } else {
                    publish(SessionState.LoggedIn(account))
                    Result.Success(account)
                }
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
        return when (val check = accountDataSource.fetchAccount()) {
            is Result.Error -> publish(SessionState.Unknown)
            is Result.Success -> publish(check.data?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut)
        }
    }

    private fun publish(state: SessionState): SessionState {
        _sessionState.value = state
        return state
    }
}
