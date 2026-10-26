package com.lerchenflo.relisto.backup.data

import androidx.room.useWriterConnection
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.backup.domain.BackupArchive
import com.lerchenflo.relisto.backup.domain.BackupRepository
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class DefaultBackupRepository(
    private val database: AppDatabase,
    private val archive: BackupArchive,
    private val directories: AppDirectories,
) : BackupRepository {

    override suspend fun export(destination: String): EmptyResult<DataError.Local> {
        try {
            // Moves everything from the write-ahead log into the database file, which is copied alone
            database.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.Error(DataError.Local.UNKNOWN)
        }
        val images = SystemFileSystem.list(Path(directories.imagesDir))
            .associate { BackupEntries.image(it.name) to it.toString() }
        return archive.write(destination, mapOf(BackupEntries.DATABASE to directories.databaseFile) + images)
    }

    override suspend fun prepareRestore(source: String): EmptyResult<DataError.Local> =
        archive.extract(source, PendingRestore.pendingDir(directories))
}
