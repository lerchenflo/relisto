package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.datasource.network.constructRoute
import com.lerchenflo.relisto.datasource.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Logged-in willhaben write requests. Answers are read as JSON objects; an empty answer is an
 * empty object, because several willhaben write endpoints answer without a body.
 */
class KtorWillhabenWriteDataSource(
    private val httpClient: HttpClient,
) {

    suspend fun post(route: String, body: JsonObject? = null): Result<JsonObject, DataError.Network> =
        send(HttpMethod.Post, route, body)

    suspend fun put(route: String, body: JsonObject? = null): Result<JsonObject, DataError.Network> =
        send(HttpMethod.Put, route, body)

    /** Uploads one image to willhaben's temporary image store; the result is its upload reference. */
    suspend fun uploadTempImage(bytes: ByteArray, fileName: String): Result<String, DataError.Network> {
        val form = MultiPartFormDataContent(
            formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, imageContentType(fileName))
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    },
                )
            }
        )
        val result = request {
            method = HttpMethod.Post
            url(constructRoute("/iad-upload/atimage/temp"))
            setBody(form)
        }
        return when (result) {
            is Result.Error -> result
            is Result.Success -> (result.data["reference"] as? JsonPrimitive)?.contentOrNull
                ?.let { Result.Success(it) }
                ?: Result.Error(DataError.Network.SERIALIZATION)
        }
    }

    private suspend fun send(method: HttpMethod, route: String, body: JsonObject?): Result<JsonObject, DataError.Network> =
        request {
            this.method = method
            url(constructRoute(route))
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun request(block: HttpRequestBuilder.() -> Unit): Result<JsonObject, DataError.Network> {
        return when (val result = safeCall<String> { httpClient.request(block) }) {
            is Result.Error -> result
            is Result.Success -> parseObject(result.data)
        }
    }

    private fun parseObject(text: String): Result<JsonObject, DataError.Network> {
        if (text.isBlank()) return Result.Success(JsonObject(emptyMap()))
        val json = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        return json?.let { Result.Success(it) } ?: Result.Error(DataError.Network.SERIALIZATION)
    }

    private fun imageContentType(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
