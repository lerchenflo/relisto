package com.lerchenflo.relisto.backup.data

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.domain.Result
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

class BackupTest {

    private val root = createTempDirectory("backup").toFile()
    private val dataDir = File(root, "data").apply { mkdirs() }
    private val imagesDir = File(dataDir, "images").apply { mkdirs() }
    private val dbFile = File(dataDir, "relisto.db")
    private val pendingDir = File(dataDir, "restore-pending")
    private val archive = FileBackupArchive()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun pendingRestore() = PendingRestore(pendingDir.path, dbFile.path, imagesDir.path)

    @Test
    fun backupIsUnpackedAndSwappedInAtTheNextStart() = runTest {
        dbFile.writeText("db v1")
        File(imagesDir, "a.jpg").writeText("image a")
        val zip = File(root, "backup.zip")
        val files = mapOf("relisto.db" to dbFile.path, "images/a.jpg" to File(imagesDir, "a.jpg").path)
        assertThat(archive.write(zip.path, files)).isInstanceOf(Result.Success::class)

        // Local data changes after the backup
        dbFile.writeText("db v2")
        File("${dbFile.path}-wal").writeText("wal")
        File(imagesDir, "b.jpg").writeText("image b")

        assertThat(archive.extract(zip.path, pendingDir.path)).isInstanceOf(Result.Success::class)
        assertThat(pendingRestore().applyIfPresent()).isTrue()

        assertThat(dbFile.readText()).isEqualTo("db v1")
        assertThat(File("${dbFile.path}-wal").exists()).isFalse()
        assertThat(imagesDir.list()!!.toList()).containsExactlyInAnyOrder("a.jpg")
        assertThat(File(imagesDir, "a.jpg").readText()).isEqualTo("image a")
        assertThat(pendingDir.exists()).isFalse()
    }

    @Test
    fun nothingPendingChangesNothing() {
        dbFile.writeText("db")

        assertThat(pendingRestore().applyIfPresent()).isFalse()
        assertThat(dbFile.readText()).isEqualTo("db")
    }

    @Test
    fun zipWithoutDatabaseIsNoBackup() = runTest {
        val zip = File(root, "other.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("notes.txt")); it.write(1) }

        assertThat(archive.extract(zip.path, pendingDir.path)).isInstanceOf(Result.Error::class)
        assertThat(pendingDir.exists()).isFalse()
    }

    @Test
    fun entriesOutsideTheBackupFoldersAreIgnored() = runTest {
        val zip = File(root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("relisto.db")); out.write(1)
            out.putNextEntry(ZipEntry("images/../../escaped.txt")); out.write(1)
            out.putNextEntry(ZipEntry("../escaped.txt")); out.write(1)
        }

        assertThat(archive.extract(zip.path, pendingDir.path)).isInstanceOf(Result.Success::class)
        assertThat(File(root, "escaped.txt").exists()).isFalse()
        assertThat(File(dataDir, "escaped.txt").exists()).isFalse()
        assertThat(pendingDir.walk().filter { it.isFile }.map { it.name }.toList()).containsExactlyInAnyOrder("relisto.db")
    }
}
