package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.AppJson
import com.lerchenflo.willhabenupdater.datasource.network.constructRoute
import com.lerchenflo.willhabenupdater.datasource.network.responseToResult
import com.lerchenflo.willhabenupdater.datasource.network.safeResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Session endpoints of the willhaben web backend. */
class KtorWillhabenAccountDataSource(
    private val httpClient: HttpClient,
) {

    /**
     * Login state from the profile endpoint. Only the display name is read from the profile, to show
     * it in the app; it is never logged and the endpoint is never captured.
     */
    suspend fun checkLogin(): Result<LoginCheck, DataError.Network> {
        val response = when (val result = safeResponse { httpClient.get(constructRoute(PROFILE_ROUTE)) }) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        return when (response.status.value) {
            // 204 is what the web app gets without a session
            204, 401, 403 -> Result.Success(LoginCheck.LoggedOut)
            in 200..299 -> Result.Success(LoginCheck.LoggedIn(displayName = response.displayNameOrNull()))
            else -> responseToResult<Unit>(response).let { it as? Result.Error ?: Result.Error(DataError.Network.UNKNOWN) }
        }
    }

    private suspend fun HttpResponse.displayNameOrNull(): String? {
        val profile = runCatching { AppJson.parseToJsonElement(bodyAsText()) }.getOrNull() as? JsonObject ?: return null
        val userData = profile["userData"] as? JsonObject ?: return null
        fun text(key: String) = (userData[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return listOfNotNull(text("firstName"), text("lastName")).joinToString(" ").ifBlank { null }
    }

    /**
     * Silent re-login: the OAuth start URL redirects through the willhaben login server back to
     * www.willhaben.at when the stored login server cookies are still valid ("stay logged in").
     * Otherwise the chain stops at the login form.
     */
    suspend fun renewSession(): Boolean {
        val response = (safeResponse { httpClient.get(constructRoute(LOGIN_START_ROUTE)) } as? Result.Success)?.data
            ?: return false
        return response.status.value in 200..299 && response.call.request.url.host == WEB_HOST
    }

    suspend fun logout() {
        safeResponse {
            httpClient.get(constructRoute(LOGOUT_ROUTE)) {
                parameter("redirect_uri", "https://$WEB_HOST/iad/logout")
            }
        }
    }

    private companion object {
        const val WEB_HOST = "www.willhaben.at"
        const val PROFILE_ROUTE = "/userprofile-service/userprofile/me"
        const val LOGIN_START_ROUTE = "/oauth2/authorization/sso"
        const val LOGOUT_ROUTE = "/logout"
    }
}

sealed interface LoginCheck {
    data object LoggedOut : LoginCheck
    data class LoggedIn(val displayName: String?) : LoginCheck
}
