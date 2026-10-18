package com.lerchenflo.willhabenupdater.utilities

/**
 * Platform folders the app writes to. All paths are absolute and the folders exist.
 */
interface AppDirectories {
    /** Root data folder (database, settings). */
    val dataDir: String

    /** Copies of all listing images, named by their SHA-256 hash. */
    val imagesDir: String

    /** Downloaded reference data that can be fetched again (category tree). */
    val cacheDir: String
}
