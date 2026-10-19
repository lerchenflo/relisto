package com.lerchenflo.relisto.testutil

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files

fun inMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

/** App folders inside a fresh temporary directory. */
class TempAppDirectories : AppDirectories {
    val root: File = Files.createTempDirectory("relisto-test").toFile()
    override val dataDir: String = root.absolutePath
    override val imagesDir: String = File(root, "images").apply { mkdirs() }.absolutePath
    override val cacheDir: String = File(root, "cache").apply { mkdirs() }.absolutePath

    fun delete() {
        root.deleteRecursively()
    }
}
