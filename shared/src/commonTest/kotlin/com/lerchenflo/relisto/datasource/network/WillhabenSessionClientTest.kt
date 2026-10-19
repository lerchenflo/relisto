package com.lerchenflo.relisto.datasource.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.relisto.app.domain.SessionCookie
import com.lerchenflo.relisto.testutil.FakeSecureStorage
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Cookies and the CSRF header on the willhaben client. */
class WillhabenSessionClientTest {

    private val cookies = PersistentCookiesStorage(FakeSecureStorage(), FakeTimeProvider(now = 1_000L))
    private var lastCookieHeader: String? = null
    private var lastCsrfHeader: String? = null

    private val client = HttpClientFactory.createWillhabenClient(
        engine = MockEngine { request ->
            lastCookieHeader = request.headers[HttpHeaders.Cookie]
            lastCsrfHeader = request.headers["x-bbx-csrf-token"]
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.SetCookie to listOf("x-bbx-csrf-token=fresh; Path=/; Secure"),
                ),
            )
        },
        cookiesStorage = cookies,
    )

    private suspend fun login() {
        cookies.replaceAll(
            listOf(
                SessionCookie("BBX_JSESSIONID", "session1", "www.willhaben.at", hostOnly = true),
                SessionCookie("x-bbx-csrf-token", "csrf1", "www.willhaben.at", hostOnly = true),
            )
        )
    }

    @Test
    fun sessionCookieIsSent() = runTest {
        login()

        client.get("https://www.willhaben.at/webapi/userprofile-service/userprofile/me")

        assertThat(lastCookieHeader?.contains("BBX_JSESSIONID=session1")).isEqualTo(true)
    }

    @Test
    fun writeRequestCarriesCsrfTokenFromCookie() = runTest {
        login()

        client.post("https://www.willhaben.at/webapi/iad/bap") { setBody("{}") }

        assertThat(lastCsrfHeader).isEqualTo("csrf1")
    }

    @Test
    fun readRequestHasNoCsrfHeader() = runTest {
        login()

        client.get("https://www.willhaben.at/webapi/iad/bap/1")

        assertThat(lastCsrfHeader).isNull()
    }

    @Test
    fun writeRequestWithoutCsrfCookieHasNoHeader() = runTest {
        client.post("https://www.willhaben.at/webapi/iad/bap") { setBody("{}") }

        assertThat(lastCsrfHeader).isNull()
    }

    @Test
    fun cookiesSetByResponsesAreUsedForNextRequest() = runTest {
        login()
        client.get("https://www.willhaben.at/webapi/iad/bap/1")

        client.post("https://www.willhaben.at/webapi/iad/bap") { setBody("{}") }

        assertThat(lastCsrfHeader).isEqualTo("fresh")
    }
}
