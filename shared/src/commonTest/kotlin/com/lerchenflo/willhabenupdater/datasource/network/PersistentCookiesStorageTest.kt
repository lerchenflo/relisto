package com.lerchenflo.willhabenupdater.datasource.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.testutil.FakeSecureStorage
import com.lerchenflo.willhabenupdater.testutil.FakeTimeProvider
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PersistentCookiesStorageTest {

    private val secureStorage = FakeSecureStorage()
    private val time = FakeTimeProvider(now = 1_000_000L)
    private fun storage() = PersistentCookiesStorage(secureStorage, time)

    private val www = Url("https://www.willhaben.at/webapi/iad/bap")
    private val sso = Url("https://sso.willhaben.at/auth/realms/willhaben/protocol/openid-connect/auth")

    private suspend fun PersistentCookiesStorage.names(url: Url) = get(url).map { it.name }

    @Test
    fun hostOnlyCookieIsSentOnlyToItsHost() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true)))

        assertThat(storage.names(www)).containsExactly("BBX_JSESSIONID")
        assertThat(storage.names(sso)).isEmpty()
    }

    @Test
    fun domainCookieIsSentToSubdomains() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("IADVISITOR", "v", "willhaben.at", hostOnly = false)))

        assertThat(storage.names(www)).containsExactly("IADVISITOR")
        assertThat(storage.names(sso)).containsExactly("IADVISITOR")
        assertThat(storage.names(Url("https://notwillhaben.at/"))).isEmpty()
    }

    @Test
    fun cookieIsOnlySentBelowItsPath() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("KC", "k", "sso.willhaben.at", hostOnly = true, path = "/auth/realms/willhaben")))

        assertThat(storage.names(sso)).containsExactly("KC")
        assertThat(storage.names(Url("https://sso.willhaben.at/auth/realms/other"))).isEmpty()
        assertThat(storage.names(Url("https://sso.willhaben.at/auth/realms/willhabenx"))).isEmpty()
    }

    @Test
    fun secureCookieIsNotSentOverHttp() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("S", "s", "www.willhaben.at", hostOnly = true, secure = true)))

        assertThat(storage.names(Url("http://www.willhaben.at/"))).isEmpty()
    }

    @Test
    fun expiredCookieIsNotSent() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("OLD", "o", "www.willhaben.at", hostOnly = true, expiresAt = 999_999L)))

        assertThat(storage.names(www)).isEmpty()
    }

    @Test
    fun responseCookieWithoutDomainBecomesHostOnly() = runTest {
        val storage = storage()

        storage.addCookie(www, Cookie(name = "x-bbx-csrf-token", value = "csrf1"))

        assertThat(storage.names(www)).containsExactly("x-bbx-csrf-token")
        assertThat(storage.names(sso)).isEmpty()
    }

    @Test
    fun responseCookieReplacesCookieWithSameNameDomainAndPath() = runTest {
        val storage = storage()
        storage.addCookie(www, Cookie(name = "BBX_JSESSIONID", value = "old", path = "/"))

        storage.addCookie(www, Cookie(name = "BBX_JSESSIONID", value = "new", path = "/"))

        assertThat(storage.get(www).map { it.value }).containsExactly("new")
    }

    @Test
    fun responseCookieWithMaxAgeZeroDeletesCookie() = runTest {
        val storage = storage()
        storage.addCookie(www, Cookie(name = "BBX_JSESSIONID", value = "s", path = "/"))

        storage.addCookie(www, Cookie(name = "BBX_JSESSIONID", value = "", path = "/", maxAge = 0))

        assertThat(storage.names(www)).isEmpty()
    }

    @Test
    fun cookiesSurviveNewInstance() = runTest {
        storage().replaceAll(listOf(SessionCookie("BBX_JSESSIONID", "s1", "www.willhaben.at", hostOnly = true)))

        assertThat(storage().get(www).map { it.value }).containsExactly("s1")
    }

    @Test
    fun cookieValueIsReadable() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("x-bbx-csrf-token", "csrf1", "www.willhaben.at", hostOnly = true)))

        assertThat(storage.valueOf("x-bbx-csrf-token", www)).isEqualTo("csrf1")
    }

    @Test
    fun validCookiesAreListedForTheLoginBrowser() = runTest {
        val storage = storage()
        val sso = SessionCookie("KEYCLOAK_IDENTITY", "k", "willhaben.at", hostOnly = false, path = "/auth/realms/willhaben")
        storage.replaceAll(listOf(sso, SessionCookie("OLD", "o", "www.willhaben.at", hostOnly = true, expiresAt = 1L)))

        assertThat(storage.allCookies()).containsExactly(sso)
    }

    @Test
    fun clearRemovesEverything() = runTest {
        val storage = storage()
        storage.replaceAll(listOf(SessionCookie("A", "a", "www.willhaben.at", hostOnly = true)))
        assertThat(storage.hasCookies()).isTrue()

        storage.clear()

        assertThat(storage.hasCookies()).isFalse()
        assertThat(storage().names(www)).isEmpty()
    }
}
