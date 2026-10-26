package com.lerchenflo.relisto.backup.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.backup.domain.BackupArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup zips as plain files (desktop). */
class FileBackupArchive : BackupArchive {

    override suspend fun write(destination: String, files: Map<String, String>): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(File(destination).outputStream().buffered()).use { zip ->
                    files.forEach { (name, path) ->
                        zip.putNextEntry(ZipEntry(name))
                        File(path).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                Result.Success(Unit)
            } catch (_: IOException) {
                File(destination).delete()
                Result.Error(DataError.Local.UNKNOWN)
            }
        }

    override suspend fun extract(source: String, targetDir: String): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            val target = File(targetDir)
            target.deleteRecursively()
            try {
                var hasDatabase = false
                ZipInputStream(File(source).inputStream().buffered()).use { zip ->
                    generateSequence { zip.nextEntry }.filter { BackupEntries.isAllowed(it.name) }.forEach { entry ->
                        val file = File(target, entry.name).apply { parentFile.mkdirs() }
                        file.outputStream().use { zip.copyTo(it) }
                        if (entry.name == BackupEntries.DATABASE) hasDatabase = true
                    }
                }
                if (hasDatabase) return@withContext Result.Success(Unit)
                target.deleteRecursively()
                Result.Error(DataError.Local.NOT_FOUND)
            } catch (_: IOException) {
                target.deleteRecursively()
                Result.Error(DataError.Local.UNKNOWN)
            }
        }
}
