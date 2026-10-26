package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.SessionCookie

/**
 * Cookies from a "name=value; name2=value2" header (Android's WebView CookieManager only returns this form).
 * Without attributes they become host cookies of [host] for all paths that never expire on their own.
 */
fun parseCookieHeader(header: String?, host: String): List<SessionCookie> =
    header.orEmpty().split(';').mapNotNull { part ->
        val separator = part.indexOf('=')
        val name = if (separator > 0) part.substring(0, separator).trim() else return@mapNotNull null
        if (name.isEmpty()) return@mapNotNull null
        SessionCookie(name = name, value = part.substring(separator + 1).trim(), domain = host, hostOnly = true)
    }
