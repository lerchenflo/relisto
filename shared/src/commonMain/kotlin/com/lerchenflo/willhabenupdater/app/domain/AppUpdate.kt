package com.lerchenflo.willhabenupdater.app.domain

/** A newer release on GitHub; updating is optional. */
data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
)

interface UpdateChecker {
    /** Null when the app is up to date, the check failed, or this is a development build. */
    suspend fun checkForUpdate(): AvailableUpdate?
}

interface AppVersionProvider {
    /** Version of the installed release, null for development builds. */
    val currentVersion: String?
}
