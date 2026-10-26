package com.lerchenflo.relisto.utilities

import com.lerchenflo.relisto.datasource.database.AppDatabase
import java.io.File

/** OS-specific application data folder ("Application Support" on macOS, %APPDATA% on Windows). */
class DesktopAppDirectories : AppDirectories {

    private val root: File = run {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: userHome, APP_FOLDER)
            os.contains("mac") -> File(userHome, "Library/Application Support/$APP_FOLDER")
            else -> File(userHome, ".local/share/${APP_FOLDER.lowercase()}")
        }
    }

    override val dataDir: String = root.ensureExists().absolutePath
    override val databaseFile: String = File(root, AppDatabase.DB_NAME).absolutePath
    override val imagesDir: String = File(root, "images").ensureExists().absolutePath
    override val cacheDir: String = File(root, "cache").ensureExists().absolutePath

    private fun File.ensureExists(): File = apply { if (!exists()) mkdirs() }

    private companion object {
        const val APP_FOLDER = "Relisto"
    }
}
