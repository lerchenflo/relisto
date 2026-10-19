package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Error
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.SessionCookie
import kotlinx.coroutines.flow.StateFlow

sealed interface SessionState {
    /** Not checked yet, or willhaben could not be reached. */
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    /** [displayName] is only shown in the app, never logged or captured. */
    data class LoggedIn(val displayName: String?) : SessionState
}

enum class LoginError : Error {
    CANCELLED,
    BROWSER_UNAVAILABLE,
    NOT_LOGGED_IN,
    NETWORK
}

/** Shows the real willhaben login page and returns the session cookies after a successful login. */
interface WillhabenLoginBrowser {
    suspend fun login(): Result<List<SessionCookie>, LoginError>

    /** Forgets the browser's own login ("stay logged in"). */
    suspend fun clearSession()
}

interface WillhabenSessionRepository {
    val sessionState: StateFlow<SessionState>

    /** Checks the stored session and renews it silently when possible. */
    suspend fun refreshSession(): SessionState

    suspend fun completeLogin(cookies: List<SessionCookie>): EmptyResult<LoginError>

    suspend fun logout()
}
