package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.AppJson
import com.lerchenflo.willhabenupdater.datasource.network.constructRoute
import com.lerchenflo.willhabenupdater.datasource.network.responseToResult
import com.lerchenflo.willhabenupdater.datasource.network.safeResponse
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAccount
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Session endpoints of the willhaben web backend. */
class KtorWillhabenAccountDataSource(
    private val httpClient: HttpClient,
) {

    /** The logged-in account, or null when the session is not logged in. */
    suspend fun fetchAccount(): Result<WillhabenAccount?, DataError.Network> {
        val response = when (val result = safeResponse { httpClient.get(constructRoute(PROFILE_ROUTE)) }) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        return when (response.status.value) {
            // 204 is what the web app gets without a session
            204, 401, 403 -> Result.Success(null)
            in 200..299 -> try {
                Result.Success(AppJson.decodeFromString<JsonObject>(response.bodyAsText()).toAccount())
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Result.Error(DataError.Network.SERIALIZATION)
            }
            else -> responseToResult<Unit>(response).let { it as? Result.Error ?: Result.Error(DataError.Network.UNKNOWN) }
        }
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

    /** The profile sits in "userData" (seen 2026-10-18); older or other shapes are read from the top level. */
    private fun JsonObject.toAccount(): WillhabenAccount {
        val profile = this["userData"] as? JsonObject ?: this
        fun text(key: String) = (profile[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val fullName = listOfNotNull(text("firstName"), text("lastName")).joinToString(" ").ifBlank { null }
        val loginId = text("loginId")
        val displayName = text("displayName") ?: text("name") ?: fullName ?: text("emailAddress") ?: text("email") ?: loginId ?: WEB_HOST
        return WillhabenAccount(loginId = loginId, displayName = displayName)
    }

    private companion object {
        const val WEB_HOST = "www.willhaben.at"
        const val PROFILE_ROUTE = "/userprofile-service/userprofile/me"
        const val LOGIN_START_ROUTE = "/oauth2/authorization/sso"
        const val LOGOUT_ROUTE = "/logout"
    }
}
