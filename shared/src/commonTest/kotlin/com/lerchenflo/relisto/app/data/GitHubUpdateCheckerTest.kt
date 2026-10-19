package com.lerchenflo.relisto.app.data

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.relisto.app.domain.AppVersionProvider
import com.lerchenflo.relisto.app.domain.AvailableUpdate
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GitHubUpdateCheckerTest {

    private val requests = mutableListOf<String>()
    private var latestTag = "v1.0.12"
    private var serverUp = true

    private val engine = MockEngine { request ->
        requests += request.url.toString()
        if (!serverUp) return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
        respond(
            content = """{"tag_name":"$latestTag","html_url":"https://github.com/lerchenflo/relisto/releases/tag/$latestTag","name":"x"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun checker(currentVersion: String?) = GitHubUpdateChecker(
        httpClient = HttpClientFactory.createGitHubClient(engine),
        appVersionProvider = object : AppVersionProvider {
            override val currentVersion: String? = currentVersion
        },
    )

    @Test
    fun newerReleaseIsReported() = runTest {
        val update = checker("1.0.5").checkForUpdate()

        assertThat(update).isEqualTo(
            AvailableUpdate("1.0.12", "https://github.com/lerchenflo/relisto/releases/tag/v1.0.12")
        )
        assertThat(requests.single()).isEqualTo("https://api.github.com/repos/lerchenflo/relisto/releases/latest")
    }

    @Test
    fun sameVersionIsNoUpdate() = runTest {
        assertThat(checker("1.0.12").checkForUpdate()).isNull()
    }

    @Test
    fun versionsAreComparedAsNumbers() = runTest {
        latestTag = "v1.0.9"

        assertThat(checker("1.0.12").checkForUpdate()).isNull()
    }

    @Test
    fun newerMinorVersionWins() = runTest {
        latestTag = "v1.1.0"

        assertThat(checker("1.0.99").checkForUpdate()?.version).isEqualTo("1.1.0")
    }

    @Test
    fun developmentBuildWithoutVersionNeverChecks() = runTest {
        assertThat(checker(null).checkForUpdate()).isNull()
        assertThat(requests).isEmpty()
    }

    @Test
    fun unreachableGitHubMeansNoUpdate() = runTest {
        serverUp = false

        assertThat(checker("1.0.5").checkForUpdate()).isNull()
    }

    @Test
    fun malformedTagMeansNoUpdate() = runTest {
        latestTag = "nightly"

        assertThat(checker("1.0.5").checkForUpdate()).isNull()
    }
}
