package com.lerchenflo.relisto.backup.data

import android.content.Context
import android.net.Uri
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

/** Backup zips behind content URIs from the system document dialogs. */
class UriBackupArchive(private val context: Context) : BackupArchive {

    override suspend fun write(destination: String, files: Map<String, String>): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(Uri.parse(destination), "wt")
                    ?: return@withContext Result.Error(DataError.Local.UNKNOWN)
                ZipOutputStream(output.buffered()).use { zip ->
                    files.forEach { (name, path) ->
                        zip.putNextEntry(ZipEntry(name))
                        File(path).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                Result.Success(Unit)
            } catch (_: IOException) {
                Result.Error(DataError.Local.UNKNOWN)
            } catch (_: SecurityException) {
                Result.Error(DataError.Local.UNKNOWN)
            }
        }

    override suspend fun extract(source: String, targetDir: String): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            val target = File(targetDir)
            target.deleteRecursively()
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(source))
                    ?: return@withContext Result.Error(DataError.Local.NOT_FOUND)
                var hasDatabase = false
                ZipInputStream(input.buffered()).use { zip ->
                    generateSequence { zip.nextEntry }.filter { BackupEntries.isAllowed(it.name) }.forEach { entry ->
                        val file = File(target, entry.name).apply { parentFile?.mkdirs() }
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
            } catch (_: SecurityException) {
                target.deleteRecursively()
                Result.Error(DataError.Local.UNKNOWN)
            }
        }
}
