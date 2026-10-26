package com.lerchenflo.relisto.backup.data

import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * A restore is unpacked into [pendingDir] while the app runs and swapped in at the next start,
 * before the database is opened, so no open database file is replaced.
 */
class PendingRestore(
    private val pendingDir: String,
    private val databaseFile: String,
    private val imagesDir: String,
) {
    private val fs = SystemFileSystem

    /** Returns true when a backup replaced the local data. */
    fun applyIfPresent(): Boolean {
        val pending = Path(pendingDir)
        if (!fs.exists(pending)) return false
        val pendingDatabase = Path(pending, BackupEntries.DATABASE)
        if (!fs.exists(pendingDatabase)) {
            deleteRecursively(pending)
            return false
        }
        DATABASE_SUFFIXES.forEach { fs.delete(Path(databaseFile + it), mustExist = false) }
        fs.atomicMove(pendingDatabase, Path(databaseFile))

        val images = Path(imagesDir)
        fs.list(images).forEach { fs.delete(it, mustExist = false) }
        val pendingImages = Path(pending, BackupEntries.IMAGES_FOLDER)
        if (fs.exists(pendingImages)) {
            fs.list(pendingImages).forEach { fs.atomicMove(it, Path(images, it.name)) }
        }
        deleteRecursively(pending)
        return true
    }

    private fun deleteRecursively(path: Path) {
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteRecursively)
        fs.delete(path, mustExist = false)
    }

    companion object {
        /** The database file itself and SQLite's side files, which must not survive the swap. */
        private val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

        fun pendingDir(directories: AppDirectories) = Path(directories.dataDir, "restore-pending").toString()

        fun of(directories: AppDirectories) =
            PendingRestore(pendingDir(directories), directories.databaseFile, directories.imagesDir)
    }
}
