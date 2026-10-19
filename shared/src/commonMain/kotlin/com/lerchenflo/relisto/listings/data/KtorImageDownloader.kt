package com.lerchenflo.relisto.listings.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.responseToResult
import com.lerchenflo.relisto.datasource.network.safeResponse
import com.lerchenflo.relisto.listings.domain.ImageDownloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.contentType

/** Downloads public listing images (no cookies or willhaben headers needed). */
class KtorImageDownloader(
    private val httpClient: HttpClient,
) : ImageDownloader {

    override suspend fun download(url: String): Result<Pair<ByteArray, String>, DataError.Network> {
        val response = when (val result = safeResponse { httpClient.get(url) }) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        if (response.status.value !in 200..299) {
            return responseToResult<Unit>(response) as? Result.Error ?: Result.Error(DataError.Network.UNKNOWN)
        }
        val extension = when (response.contentType()?.contentSubtype?.lowercase()) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            "gif" -> "gif"
            "bmp" -> "bmp"
            // willhaben's image server answers ".jpg" URLs with WebP
            "webp" -> "webp"
            else -> url.substringAfterLast('/').substringAfterLast('.', "jpg").lowercase()
        }
        return Result.Success(response.readRawBytes() to extension)
    }
}
