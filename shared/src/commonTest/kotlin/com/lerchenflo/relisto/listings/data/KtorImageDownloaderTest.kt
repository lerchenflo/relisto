package com.lerchenflo.relisto.listings.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KtorImageDownloaderTest {

    private val bytes = byteArrayOf(1, 2, 3)

    private fun downloader(contentType: String) = KtorImageDownloader(
        HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("missing.jpg")) respondError(HttpStatusCode.NotFound)
            else respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, contentType))
        })
    )

    @Test
    fun jpegIsDownloadedWithJpgExtension() = runTest {
        val result = downloader("image/jpeg").download("https://cache.willhaben.at/mmo/1/abc_123.jpg") as Result.Success

        assertThat(result.data.first.toList()).isEqualTo(bytes.toList())
        assertThat(result.data.second).isEqualTo("jpg")
    }

    @Test
    fun extensionFollowsContentType() = runTest {
        val result = downloader("image/png").download("https://cache.willhaben.at/image") as Result.Success

        assertThat(result.data.second).isEqualTo("png")
    }

    @Test
    fun missingImageIsReported() = runTest {
        assertThat(downloader("image/jpeg").download("https://cache.willhaben.at/missing.jpg"))
            .isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
    }
}
