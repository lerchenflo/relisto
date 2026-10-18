package com.lerchenflo.willhabenupdater.testutil

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files

fun inMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

/** App folders inside a fresh temporary directory. */
class TempAppDirectories : AppDirectories {
    val root: File = Files.createTempDirectory("willhabenupdater-test").toFile()
    override val dataDir: String = root.absolutePath
    override val imagesDir: String = File(root, "images").apply { mkdirs() }.absolutePath
    override val cacheDir: String = File(root, "cache").apply { mkdirs() }.absolutePath

    fun delete() {
        root.deleteRecursively()
    }
}

/** Time that only moves when the test says so. */
class FakeTimeProvider(var now: Long = 1_000L) : TimeProvider {
    override fun nowEpochMillis(): Long = now
}
