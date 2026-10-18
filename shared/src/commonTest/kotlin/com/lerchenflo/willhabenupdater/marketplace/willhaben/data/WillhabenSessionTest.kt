package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.datasource.network.PersistentCookiesStorage
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.SessionState
import com.lerchenflo.willhabenupdater.testutil.FakeSecureStorage
import com.lerchenflo.willhabenupdater.testutil.FakeTimeProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WillhabenSessionTest {

    private val cookies = PersistentCookiesStorage(FakeSecureStorage(), FakeTimeProvider(now = 1_000L))
    private val requests = mutableListOf<String>()

    /** Profile response per call; the last entry repeats. */
    private var profileResponses = mutableListOf<MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()
    private var ssoSessionValid = true

    private val engine = MockEngine { request ->
        requests += request.url.toString()
        val path = request.url.encodedPath
        when {
            path.endsWith("/userprofile-service/userprofile/me") -> {
                val handler = if (profileResponses.size > 1) profileResponses.removeAt(0) else profileResponses.single()
                handler(request)
            }
            path.endsWith("/webapi/oauth2/authorization/sso") -> redirect("https://sso.willhaben.at/auth/realms/willhaben/protocol/openid-connect/auth?client_id=bbx-bff")
            request.url.host == "sso.willhaben.at" ->
                if (ssoSessionValid) redirect("https://www.willhaben.at/webapi/oauth2/code/sso?code=abc")
                else respond("<html>login form</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            path.endsWith("/webapi/oauth2/code/sso") -> respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.Location to listOf("https://www.willhaben.at/iad/myprofile/myadverts"),
                    HttpHeaders.SetCookie to listOf("BBX_JSESSIONID=renewed; Path=/; Secure; HttpOnly"),
                ),
            )
            else -> respond("<html>ok</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
    }

    private fun MockRequestHandleScope.redirect(location: String) =
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))

    private fun profile(json: String): MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }

    private fun noContent(): MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond("", HttpStatusCode.NoContent) }

    private val dataSource = KtorWillhabenAccountDataSource(HttpClientFactory.createWillhabenClient(engine, cookies))
    private val repository = CookieWillhabenSessionRepository(dataSource, cookies)

    private val loginCookies = listOf(SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true))

    // --- KtorWillhabenAccountDataSource (name only for display; never logged or captured) ---

    @Test
    fun profileAnswerMeansLoggedIn() = runTest {
        profileResponses = mutableListOf(profile("""{"anything":"else"}"""))

        assertThat(dataSource.checkLogin()).isEqualTo(Result.Success(LoginCheck.LoggedIn(displayName = null)))
    }

    @Test
    fun displayNameComesFromUserData() = runTest {
        // Shape of the profile response seen on 2026-10-18 (values made up)
        profileResponses = mutableListOf(profile("""{"userData":{"firstName":"Flo","lastName":"Example","loginId":1}}"""))

        assertThat(dataSource.checkLogin()).isEqualTo(Result.Success(LoginCheck.LoggedIn(displayName = "Flo Example")))
    }

    @Test
    fun noContentMeansLoggedOut() = runTest {
        profileResponses = mutableListOf(noContent())

        assertThat(dataSource.checkLogin()).isEqualTo(Result.Success(LoginCheck.LoggedOut))
    }

    @Test
    fun unauthorizedMeansLoggedOut() = runTest {
        profileResponses = mutableListOf({ respondError(HttpStatusCode.Unauthorized) })

        assertThat(dataSource.checkLogin()).isEqualTo(Result.Success(LoginCheck.LoggedOut))
    }

    @Test
    fun serverErrorIsReported() = runTest {
        profileResponses = mutableListOf({ respondError(HttpStatusCode.InternalServerError) })

        assertThat(dataSource.checkLogin()).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }

    @Test
    fun silentReloginFollowsRedirectsAndStoresNewSession() = runTest {
        assertThat(dataSource.renewSession()).isTrue()

        assertThat(cookies.valueOf("BBX_JSESSIONID", io.ktor.http.Url("https://www.willhaben.at/webapi/x"))).isEqualTo("renewed")
    }

    @Test
    fun silentReloginFailsWhenLoginFormIsShown() = runTest {
        ssoSessionValid = false

        assertThat(dataSource.renewSession()).isFalse()
    }

    // --- CookieWillhabenSessionRepository ---

    @Test
    fun loginWithValidCookiesGivesLoggedInState() = runTest {
        profileResponses = mutableListOf(profile("""{"userData":{"firstName":"Flo"}}"""))

        val result = repository.completeLogin(loginCookies)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(repository.sessionState.value).isEqualTo(SessionState.LoggedIn(displayName = "Flo"))
    }

    @Test
    fun loginWhoseCookiesAreNotAcceptedIsRejectedAndForgotten() = runTest {
        profileResponses = mutableListOf(noContent())

        val result = repository.completeLogin(loginCookies)

        assertThat(result).isEqualTo(Result.Error(LoginError.NOT_LOGGED_IN))
        assertThat(repository.sessionState.value).isEqualTo(SessionState.LoggedOut)
        assertThat(cookies.hasCookies()).isFalse()
    }

    @Test
    fun refreshWithoutCookiesIsLoggedOutWithoutRequest() = runTest {
        val state = repository.refreshSession()

        assertThat(state).isEqualTo(SessionState.LoggedOut)
        assertThat(requests.isEmpty()).isTrue()
    }

    @Test
    fun refreshWithExpiredSessionRenewsSilently() = runTest {
        cookies.replaceAll(loginCookies)
        profileResponses = mutableListOf(noContent(), profile("{}"))

        val state = repository.refreshSession()

        assertThat(state).isEqualTo(SessionState.LoggedIn(displayName = null))
        assertThat(requests.any { it.contains("/webapi/oauth2/authorization/sso") }).isTrue()
    }

    @Test
    fun refreshWithDeadSsoSessionIsLoggedOut() = runTest {
        cookies.replaceAll(loginCookies)
        ssoSessionValid = false
        profileResponses = mutableListOf(noContent())

        assertThat(repository.refreshSession()).isEqualTo(SessionState.LoggedOut)
    }

    @Test
    fun refreshWhileOfflineKeepsStateUnknown() = runTest {
        cookies.replaceAll(loginCookies)
        profileResponses = mutableListOf({ respondError(HttpStatusCode.ServiceUnavailable) })

        assertThat(repository.refreshSession()).isInstanceOf(SessionState.Unknown::class)
    }

    @Test
    fun logoutCallsWillhabenAndForgetsCookies() = runTest {
        profileResponses = mutableListOf(profile("{}"))
        repository.completeLogin(loginCookies)

        repository.logout()

        assertThat(requests.last()).contains("/webapi/logout?redirect_uri=")
        assertThat(cookies.hasCookies()).isFalse()
        assertThat(repository.sessionState.value).isEqualTo(SessionState.LoggedOut)
    }
}
