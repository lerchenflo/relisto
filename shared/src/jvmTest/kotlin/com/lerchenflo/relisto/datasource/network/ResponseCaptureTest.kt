package com.lerchenflo.relisto.datasource.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.lerchenflo.relisto.testutil.TempAppDirectories
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test

class ResponseCaptureTest {

    private val dirs = TempAppDirectories()
    private val captureDir = File(dirs.root, "api-samples")

    private val engine = MockEngine {
        respond("""{"advertDetails":[{"id":1}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @AfterTest
    fun tearDown() = dirs.delete()

    @Test
    fun responsesAreWrittenToCaptureFolderAndStillReadable() = runTest {
        val client = HttpClientFactory.createWillhabenClient(engine, captureDir = captureDir.absolutePath)

        val body = client.get("https://www.willhaben.at/webapi/myatz/atverz/showpagedadsfromuser?page=1").bodyAsText()

        assertThat(body).isEqualTo("""{"advertDetails":[{"id":1}]}""")
        val file = captureDir.listFiles()!!.single()
        assertThat(file.name).contains("GET_myatz_atverz_showpagedadsfromuser")
        val text = file.readText()
        assertThat(text).contains("\"status\": 200")
        assertThat(text).contains("""{"advertDetails":[{"id":1}]}""")
    }

    @Test
    fun cookiesAndCsrfTokenAreNeverWritten() = runTest {
        val client = HttpClientFactory.createWillhabenClient(engine, captureDir = captureDir.absolutePath)

        client.get("https://www.willhaben.at/webapi/iad/bap/1") {
            header(HttpHeaders.Cookie, "BBX_JSESSIONID=secret-session")
            header("x-bbx-csrf-token", "secret-csrf")
        }

        val text = captureDir.listFiles()!!.single().readText()
        assertThat(text).doesNotContain("secret-session")
        assertThat(text).doesNotContain("secret-csrf")
    }

    @Test
    fun listingsOfOtherVerticalsAreWritten() = runTest {
        val client = HttpClientFactory.createWillhabenClient(engine, captureDir = captureDir.absolutePath)

        client.get("https://www.willhaben.at/webapi/iad/motor/get/1")
        client.get("https://www.willhaben.at/webapi/iad/estate/2")

        assertThat(captureDir.listFiles()!!.map { it.name.substringAfter("GET_") }.sorted())
            .isEqualTo(listOf("iad_estate_2.json", "iad_motor_get_1.json"))
    }

    @Test
    fun userProfileAndOtherNonListingResponsesAreNeverWritten() = runTest {
        val client = HttpClientFactory.createWillhabenClient(engine, captureDir = captureDir.absolutePath)

        client.get("https://www.willhaben.at/webapi/userprofile-service/userprofile/me")
        client.get("https://www.willhaben.at/webapi/iad/user/123")
        client.get("https://www.willhaben.at/webapi/chat-api/v1/counter")

        assertThat(!captureDir.exists() || captureDir.listFiles()!!.isEmpty()).isTrue()
    }

    @Test
    fun nothingIsWrittenWithoutCaptureFolder() = runTest {
        val client = HttpClientFactory.createWillhabenClient(engine)

        client.get("https://www.willhaben.at/webapi/x")

        assertThat(!captureDir.exists()).isTrue()
    }
}
