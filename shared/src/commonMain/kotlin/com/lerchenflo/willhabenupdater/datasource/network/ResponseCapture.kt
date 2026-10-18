package com.lerchenflo.willhabenupdater.datasource.network

import com.lerchenflo.willhabenupdater.utilities.nowEpochMillis
import io.ktor.client.plugins.observer.ResponseObserverConfig
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes willhaben listing API responses (method, URL, status, body; never headers, so no cookies
 * or CSRF tokens, and never profile data) as JSON files into [directory]. Only used in development runs to learn the
 * response shapes of the unofficial API.
 */
internal class ResponseCaptureWriter(private val directory: String) {

    private companion object {
        /** Only listing data is recorded, never the user's profile, chats or other personal data. */
        val LISTING_ENDPOINTS = Regex("^/webapi/(myatz/|iad/(bap|bapcom|productgroups|categorytree|locations|deletereasons|soldreasons)(/|$)|iad-upload/|p2p-aggregator/)")
    }

    private val mutex = Mutex()
    private var counter = 0

    fun install(config: ResponseObserverConfig) {
        config.onResponse { response -> capture(response) }
    }

    private suspend fun capture(response: HttpResponse) {
        val url = response.request.url
        if (!LISTING_ENDPOINTS.containsMatchIn(url.encodedPath)) return
        try {
            val body = response.bodyAsText()
            val bodyJson: JsonElement = runCatching { AppJson.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body) }
            val method = response.request.method.value
            val name = mutex.withLock {
                counter++
                val path = url.encodedPath.removePrefix("/webapi/").replace(Regex("[^A-Za-z0-9]+"), "_").take(80)
                "${nowEpochMillis()}_${counter.toString().padStart(4, '0')}_${method}_$path.json"
            }
            val text = buildString {
                appendLine("{")
                appendLine("  \"method\": \"$method\",")
                appendLine("  \"url\": ${JsonPrimitive(url.toString())},")
                appendLine("  \"status\": ${response.status.value},")
                appendLine("  \"body\": $bodyJson")
                appendLine("}")
            }
            withContext(Dispatchers.IO) {
                val dir = Path(directory)
                SystemFileSystem.createDirectories(dir)
                SystemFileSystem.sink(Path(dir, name)).buffered().use { it.writeString(text) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Capturing is a development aid and must never break a request
            e.printStackTrace()
        }
    }
}

/** Where development runs write captured API responses; null in releases and tests. */
data class ResponseCaptureSettings(val directory: String?)
