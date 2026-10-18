package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI

private const val WILLHABEN_DOMAIN = "willhaben.at"

/**
 * willhaben cookies of the login browser's cookie store as session cookies.
 * java.net stores host cookies with the host as domain, so a cookie counts as host-only unless its
 * domain is the bare "willhaben.at" or starts with a dot.
 */
fun CookieStore.willhabenSessionCookies(now: Long): List<SessionCookie> =
    cookies
        .filter { !it.hasExpired() && it.domain?.trimStart('.')?.endsWith(WILLHABEN_DOMAIN) == true }
        .map { cookie ->
            val domain = cookie.domain.trimStart('.').lowercase()
            SessionCookie(
                name = cookie.name,
                value = cookie.value,
                domain = domain,
                hostOnly = !cookie.domain.startsWith(".") && domain != WILLHABEN_DOMAIN,
                path = cookie.path ?: "/",
                secure = cookie.secure,
                httpOnly = cookie.isHttpOnly,
                expiresAt = cookie.maxAge.takeIf { it >= 0 }?.let { now + it * 1000 },
            )
        }

/** Puts stored session cookies into the login browser, so a valid login server session is reused. */
fun CookieStore.addSessionCookies(cookies: List<SessionCookie>, now: Long) {
    cookies.forEach { cookie ->
        val httpCookie = HttpCookie(cookie.name, cookie.value).apply {
            // Netscape style, so the cookie is sent as "name=value"
            version = 0
            domain = if (cookie.hostOnly) cookie.domain else ".${cookie.domain}"
            path = cookie.path
            secure = cookie.secure
            isHttpOnly = cookie.httpOnly
            maxAge = cookie.expiresAt?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: -1
        }
        add(URI("https", cookie.domain, cookie.path, null), httpCookie)
    }
}
