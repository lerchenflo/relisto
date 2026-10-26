package com.lerchenflo.relisto.backup.domain

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult

/** Zip file with the database and all listing images. */
interface BackupRepository {
    /** Writes a backup to [destination] (a file path, or a content URI on Android). */
    suspend fun export(destination: String): EmptyResult<DataError.Local>

    /** Unpacks the backup at [source]; it replaces the local data at the next start, before the database opens. */
    suspend fun prepareRestore(source: String): EmptyResult<DataError.Local>
}

/** Platform zip reading and writing; [destination] and [source] are file paths or content URIs. */
interface BackupArchive {
    /** [files] maps zip entry names to absolute file paths. */
    suspend fun write(destination: String, files: Map<String, String>): EmptyResult<DataError.Local>

    /** Unpacks only the backup entries into [targetDir]; fails (and leaves nothing) without a database entry. */
    suspend fun extract(source: String, targetDir: String): EmptyResult<DataError.Local>
}

/** Save and open dialogs for backup files; null when the user cancels. */
interface BackupFilePicker {
    suspend fun pickSaveTarget(suggestedName: String): String?
    suspend fun pickBackupFile(): String?
}
