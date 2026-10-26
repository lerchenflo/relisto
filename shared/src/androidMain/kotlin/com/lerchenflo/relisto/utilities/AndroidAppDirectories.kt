package com.lerchenflo.relisto.utilities

import android.content.Context
import com.lerchenflo.relisto.datasource.database.AppDatabase
import java.io.File

/** App-private folders on Android. */
class AndroidAppDirectories(context: Context) : AppDirectories {
    override val dataDir: String = context.filesDir.absolutePath
    override val databaseFile: String = context.getDatabasePath(AppDatabase.DB_NAME).absolutePath
    override val imagesDir: String = File(context.filesDir, "images").apply { mkdirs() }.absolutePath
    override val cacheDir: String = context.cacheDir.absolutePath
}
