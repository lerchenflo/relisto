package com.lerchenflo.relisto.app.data

import com.lerchenflo.relisto.app.domain.AppVersionProvider
import com.lerchenflo.relisto.app.domain.AvailableUpdate
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.UpdateChecker
import com.lerchenflo.relisto.datasource.network.get
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Compares the installed version with the latest GitHub release (tag "v1.0.<run number>").
 * Local builds (no version, or the default "1.0.0" without `-PappVersion`) never check.
 */
class GitHubUpdateChecker(
    private val httpClient: HttpClient,
    private val appVersionProvider: AppVersionProvider,
) : UpdateChecker {

    override suspend fun checkForUpdate(): AvailableUpdate? {
        val currentText = appVersionProvider.currentVersion?.takeIf { it != LOCAL_BUILD_VERSION } ?: return null
        val current = parseVersion(currentText) ?: return null
        val release = (httpClient.get<LatestReleaseDto>(LATEST_RELEASE_URL) as? Result.Success)?.data ?: return null
        val latestText = release.tagName.removePrefix("v")
        val latest = parseVersion(latestText) ?: return null
        return if (compareVersions(latest, current) > 0) AvailableUpdate(latestText, release.htmlUrl) else null
    }

    private fun parseVersion(text: String): List<Int>? =
        text.trim().split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    @Serializable
    private data class LatestReleaseDto(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
    )

    private companion object {
        /** Default of `appVersion` in the Gradle builds; release versions start at 1.0.1. */
        const val LOCAL_BUILD_VERSION = "1.0.0"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/lerchenflo/relisto/releases/latest"
    }
}
