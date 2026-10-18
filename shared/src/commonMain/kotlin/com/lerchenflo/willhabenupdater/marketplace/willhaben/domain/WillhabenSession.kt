package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

import com.lerchenflo.willhabenupdater.app.domain.Error
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import kotlinx.coroutines.flow.StateFlow

data class WillhabenAccount(
    val loginId: String?,
    val displayName: String,
)

sealed interface SessionState {
    /** Not checked yet, or willhaben could not be reached. */
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val account: WillhabenAccount) : SessionState
}

enum class LoginError : Error {
    CANCELLED,
    BROWSER_UNAVAILABLE,
    /** The Java runtime brings its own, incompatible Chromium (JetBrains Runtime with JCEF). */
    UNSUPPORTED_RUNTIME,
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

    suspend fun completeLogin(cookies: List<SessionCookie>): Result<WillhabenAccount, LoginError>

    suspend fun logout()
}
