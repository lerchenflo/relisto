package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.app.domain.SessionCookie
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import kotlin.test.Test

class LoginCookieStoreTest {

    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val store = manager.cookieStore

    private fun responseCookie(url: String, header: String) {
        manager.put(URI(url), mapOf("Set-Cookie" to listOf(header)))
    }

    @Test
    fun hostCookieFromResponseBecomesHostOnlySessionCookie() {
        responseCookie("https://www.willhaben.at/iad", "BBX_JSESSIONID=s1; Path=/; Secure; HttpOnly")

        assertThat(store.willhabenSessionCookies(now = 1_000L)).containsExactlyInAnyOrder(
            SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true, path = "/", secure = true, httpOnly = true)
        )
    }

    @Test
    fun domainCookieKeepsDomainAndExpiry() {
        responseCookie("https://sso.willhaben.at/auth", "KEYCLOAK_IDENTITY=k; Domain=willhaben.at; Path=/auth/realms/willhaben; Max-Age=600; Secure")

        val cookie = store.willhabenSessionCookies(now = 1_000L).single()

        assertThat(cookie).isEqualTo(
            SessionCookie(
                "KEYCLOAK_IDENTITY", "k", "willhaben.at", hostOnly = false, path = "/auth/realms/willhaben",
                secure = true, httpOnly = false, expiresAt = 601_000L,
            )
        )
    }

    @Test
    fun cookiesOfOtherSitesAreLeftOut() {
        responseCookie("https://www.google.com/recaptcha", "NID=x; Path=/")
        responseCookie("https://www.willhaben.at/", "IADVISITOR=v; Path=/")

        assertThat(store.willhabenSessionCookies(now = 1_000L).map { it.name }).containsExactlyInAnyOrder("IADVISITOR")
    }

    @Test
    fun storedSessionIsPreloadedForTheLoginPage() {
        store.addSessionCookies(
            listOf(
                SessionCookie("KEYCLOAK_IDENTITY", "k", "willhaben.at", hostOnly = false, path = "/auth/realms/willhaben"),
                SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true),
            ),
            now = 1_000L,
        )

        val sent = manager.get(URI("https://sso.willhaben.at/auth/realms/willhaben/protocol"), emptyMap())["Cookie"].orEmpty()

        assertThat(sent.joinToString()).isEqualTo("KEYCLOAK_IDENTITY=k")
    }

    @Test
    fun preloadedCookiesSurviveRoundTrip() {
        val original = SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true, httpOnly = true)

        store.addSessionCookies(listOf(original), now = 1_000L)

        assertThat(store.willhabenSessionCookies(now = 1_000L)).containsExactlyInAnyOrder(original)
    }

}
