package com.lerchenflo.willhabenupdater.datasource.network

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isNotEmpty
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Logs must never contain session cookies, CSRF tokens or login codes. */
class HttpLoggingTest {

    private val lines = mutableListOf<String>()
    private val logger = object : Logger {
        override fun log(message: String) {
            lines += message
        }
    }

    private val client = HttpClientFactory.createWillhabenClient(
        engine = MockEngine {
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "BBX_JSESSIONID=secret-session-cookie; Path=/"))
        },
        logger = logger,
    )

    @Test
    fun cookiesAndCsrfTokenAreNotLogged() = runTest {
        client.get("https://www.willhaben.at/webapi/iad/bap/1") {
            header(HttpHeaders.Cookie, "BBX_JSESSIONID=secret-session-cookie")
            header("x-bbx-csrf-token", "secret-csrf-token")
        }

        val log = lines.joinToString("\n")
        assertThat(log).isNotEmpty()
        assertThat(log).doesNotContain("secret-session-cookie")
        assertThat(log).doesNotContain("secret-csrf-token")
    }

    @Test
    fun loginRequestsAreNotLogged() = runTest {
        client.get("https://www.willhaben.at/webapi/oauth2/code/sso?code=secret-login-code&state=s")
        client.get("https://sso.willhaben.at/auth/realms/willhaben/login-actions/authenticate?session_code=secret-session-code")

        val log = lines.joinToString("\n")
        assertThat(log).doesNotContain("secret-login-code")
        assertThat(log).doesNotContain("secret-session-code")
    }
}
