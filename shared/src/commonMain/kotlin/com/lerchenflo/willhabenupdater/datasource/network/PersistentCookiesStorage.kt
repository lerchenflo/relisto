package com.lerchenflo.willhabenupdater.datasource.network

import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.datasource.preferences.SecureStorage
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Ktor cookie storage for the willhaben session, kept encrypted in [SecureStorage].
 * Implements the parts of RFC 6265 the session needs: host-only vs. domain cookies,
 * path prefix, secure flag and expiry.
 */
class PersistentCookiesStorage(
    private val secureStorage: SecureStorage,
    private val timeProvider: TimeProvider,
) : CookiesStorage {

    private val mutex = Mutex()
    private var cookies: List<StoredCookie>? = null

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = timeProvider.nowEpochMillis()
        loaded().filter { it.isValidAt(now) && it.matches(requestUrl) }.map { it.toKtorCookie() }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        val now = timeProvider.nowEpochMillis()
        val stored = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = (cookie.domain ?: requestUrl.host).trimStart('.').lowercase(),
            hostOnly = cookie.domain == null,
            path = cookie.path?.takeIf { it.startsWith("/") } ?: "/",
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            expiresAt = cookie.maxAge?.let { now + it * 1000L } ?: cookie.expires?.timestamp,
        )
        val others = loaded().filterNot { it.sameKeyAs(stored) }
        save(if (stored.isValidAt(now)) others + stored else others)
    }

    override fun close() = Unit

    /** Replaces the whole session, e.g. with the cookies of a fresh browser login. */
    suspend fun replaceAll(sessionCookies: List<SessionCookie>) = mutex.withLock {
        save(sessionCookies.map { it.toStoredCookie() })
    }

    suspend fun valueOf(name: String, requestUrl: Url): String? = get(requestUrl).firstOrNull { it.name == name }?.value

    suspend fun hasCookies(): Boolean = mutex.withLock { loaded().isNotEmpty() }

    suspend fun clear() = mutex.withLock {
        cookies = emptyList()
        secureStorage.delete(STORAGE_KEY)
    }

    private suspend fun loaded(): List<StoredCookie> {
        cookies?.let { return it }
        val json = secureStorage.getString(STORAGE_KEY)
        val restored = json?.let {
            runCatching { AppJson.decodeFromString(ListSerializer(StoredCookie.serializer()), it) }.getOrNull()
        }.orEmpty()
        cookies = restored
        return restored
    }

    private suspend fun save(updated: List<StoredCookie>) {
        cookies = updated
        secureStorage.putString(STORAGE_KEY, AppJson.encodeToString(ListSerializer(StoredCookie.serializer()), updated))
    }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val hostOnly: Boolean,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val expiresAt: Long?,
    ) {
        fun isValidAt(now: Long): Boolean = expiresAt == null || expiresAt > now

        fun sameKeyAs(other: StoredCookie): Boolean = name == other.name && domain == other.domain && path == other.path

        fun matches(url: Url): Boolean {
            val host = url.host.lowercase()
            val domainMatches = if (hostOnly) host == domain else host == domain || host.endsWith(".$domain")
            val requestPath = url.encodedPath.ifEmpty { "/" }
            val pathMatches = requestPath == path ||
                (requestPath.startsWith(path) && (path.endsWith("/") || requestPath[path.length] == '/'))
            val schemeMatches = !secure || url.protocol == URLProtocol.HTTPS
            return domainMatches && pathMatches && schemeMatches
        }

        fun toKtorCookie(): Cookie = Cookie(
            name = name,
            value = value,
            domain = domain,
            path = path,
            secure = secure,
            httpOnly = httpOnly,
        )
    }

    private fun SessionCookie.toStoredCookie() = StoredCookie(
        name = name,
        value = value,
        domain = domain.trimStart('.').lowercase(),
        hostOnly = hostOnly,
        path = path.ifEmpty { "/" },
        secure = secure,
        httpOnly = httpOnly,
        expiresAt = expiresAt,
    )

    private companion object {
        const val STORAGE_KEY = "willhabenupdater.session_cookies"
    }
}
