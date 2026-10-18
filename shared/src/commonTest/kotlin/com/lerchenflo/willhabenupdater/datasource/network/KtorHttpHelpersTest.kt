package com.lerchenflo.willhabenupdater.datasource.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test

class KtorHttpHelpersTest {

    @Serializable
    data class SampleDto(val id: Int, val name: String)

    private fun client(engine: MockEngine) = HttpClientFactory.createWillhabenClient(engine)

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun relativeRouteIsResolvedAgainstWillhabenBaseUrl() {
        assertThat(constructRoute("/iad/locations/1010")).isEqualTo("https://www.willhaben.at/webapi/iad/locations/1010")
    }

    @Test
    fun routeWithoutLeadingSlashGetsOne() {
        assertThat(constructRoute("iad/bap")).isEqualTo("https://www.willhaben.at/webapi/iad/bap")
    }

    @Test
    fun absoluteUrlIsKept() {
        assertThat(constructRoute("https://example.org/x")).isEqualTo("https://example.org/x")
    }

    @Test
    fun getSendsQueryParametersAndWillhabenHeaders() = runTest {
        var requestedUrl = ""
        var clientHeader: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            clientHeader = request.headers["X-WH-Client"]
            respond("""{"id":1,"name":"a"}""", HttpStatusCode.OK, jsonHeaders)
        }

        client(engine).get<SampleDto>("/iad/test", mapOf("title" to "Kinder Rad", "page" to 2))

        assertThat(requestedUrl).isEqualTo("https://www.willhaben.at/webapi/iad/test?title=Kinder+Rad&page=2")
        assertThat(clientHeader).isEqualTo("api@willhaben.at;responsive_web;server;1.0.0;desktop")
    }

    @Test
    fun successBodyIsDecodedIgnoringUnknownFields() = runTest {
        val engine = MockEngine {
            respond("""{"id":7,"name":"Rad","newField":true}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = client(engine).get<SampleDto>("/x")

        assertThat(result).isEqualTo(Result.Success(SampleDto(7, "Rad")))
    }

    @Test
    fun stringResponseReturnsRawBody() = runTest {
        val engine = MockEngine { respond("""{"raw":1}""", HttpStatusCode.OK, jsonHeaders) }

        val result = client(engine).get<String>("/x")

        assertThat(result).isEqualTo(Result.Success("""{"raw":1}"""))
    }

    @Test
    fun unauthorizedMapsToUnauthorized() = runTest {
        val result = client(MockEngine { respondError(HttpStatusCode.Unauthorized) }).get<SampleDto>("/x")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }

    @Test
    fun tooManyRequestsMapsToTooManyRequests() = runTest {
        val result = client(MockEngine { respondError(HttpStatusCode.TooManyRequests) }).get<SampleDto>("/x")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.TOO_MANY_REQUESTS))
    }

    @Test
    fun serviceUnavailableMapsToServiceUnavailable() = runTest {
        val result = client(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }).get<SampleDto>("/x")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVICE_UNAVAILABLE))
    }

    @Test
    fun otherServerErrorMapsToServerError() = runTest {
        val result = client(MockEngine { respondError(HttpStatusCode.BadGateway) }).get<SampleDto>("/x")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }

    @Test
    fun notFoundMapsToNotFound() = runTest {
        val result = client(MockEngine { respondError(HttpStatusCode.NotFound) }).get<SampleDto>("/x")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
    }

    @Test
    fun malformedBodyMapsToSerialization() = runTest {
        val engine = MockEngine { respond("""{"id":"not a number"}""", HttpStatusCode.OK, jsonHeaders) }

        val result = client(engine).get<SampleDto>("/x")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERIALIZATION))
    }

    @Test
    fun engineExceptionMapsToUnknown() = runTest {
        val engine = MockEngine { throw IllegalStateException("connection reset") }

        val result = client(engine).get<SampleDto>("/x")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNKNOWN))
    }
}
