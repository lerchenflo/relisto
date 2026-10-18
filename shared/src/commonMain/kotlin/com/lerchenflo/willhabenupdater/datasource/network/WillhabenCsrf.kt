package com.lerchenflo.willhabenupdater.datasource.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.HttpMethod

/** Cookie set by the willhaben web backend, and the header it expects back on write requests. */
internal const val CSRF_COOKIE = "x-bbx-csrf-token"
internal const val CSRF_HEADER = "x-bbx-csrf-token"

class WillhabenCsrfConfig {
    var cookiesStorage: CookiesStorage = AcceptAllCookiesStorage()
}

/** Copies the CSRF cookie into the CSRF header for every request that is not GET or HEAD. */
val WillhabenCsrf = createClientPlugin("WillhabenCsrf", ::WillhabenCsrfConfig) {
    val cookiesStorage = pluginConfig.cookiesStorage
    onRequest { request, _ ->
        if (request.method != HttpMethod.Get && request.method != HttpMethod.Head) {
            val token = cookiesStorage.get(request.url.build()).firstOrNull { it.name == CSRF_COOKIE }?.value
            if (token != null) request.headers[CSRF_HEADER] = token
        }
    }
}
