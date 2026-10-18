package com.lerchenflo.willhabenupdater.datasource.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

const val WILLHABEN_BASE_URL = "https://www.willhaben.at/webapi"

/** Client header the willhaben web app sends with every API call. */
private const val WILLHABEN_CLIENT_HEADER = "api@willhaben.at;responsive_web;server;1.0.0;desktop"

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

private const val LOGIN_SERVER_HOST = "sso.willhaben.at"

/** Headers that carry credentials and must never show up in logs. */
private val SENSITIVE_HEADERS = setOf(HttpHeaders.Cookie, HttpHeaders.SetCookie, CSRF_HEADER)

object HttpClientFactory {

    /**
     * [cookiesStorage] holds the willhaben session; the CSRF header is added for every write request.
     * With [captureDir] set, every API response is also written there (development only).
     */
    fun createWillhabenClient(
        engine: HttpClientEngine,
        cookiesStorage: CookiesStorage = AcceptAllCookiesStorage(),
        captureDir: String? = null,
        logger: Logger = Logger.SIMPLE,
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpCookies) {
            storage = cookiesStorage
        }
        install(WillhabenCsrf) {
            this.cookiesStorage = cookiesStorage
        }
        if (captureDir != null) {
            install(ResponseObserver) {
                ResponseCaptureWriter(captureDir).install(this)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(Logging) {
            this.logger = logger
            level = LogLevel.INFO
            sanitizeHeader { header -> header in SENSITIVE_HEADERS }
            // Login and its redirects carry one-time codes in the URL; they are never logged
            filter { request -> request.url.host != LOGIN_SERVER_HOST && !request.url.build().encodedPath.contains("/oauth2/") }
        }
        defaultRequest {
            headers.append(HttpHeaders.Accept, "application/json")
            headers.append(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            headers.append("X-WH-Client", WILLHABEN_CLIENT_HEADER)
        }
    }

    /** Plain JSON client for the GitHub release check; no willhaben headers or cookies. */
    fun createGitHubClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
        defaultRequest {
            headers.append(HttpHeaders.Accept, "application/vnd.github+json")
        }
    }
}
