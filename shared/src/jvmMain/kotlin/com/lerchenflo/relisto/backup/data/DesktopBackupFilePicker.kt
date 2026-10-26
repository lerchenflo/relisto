package com.lerchenflo.relisto.backup.data

import com.lerchenflo.relisto.backup.domain.BackupFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.backup_export
import relisto.shared.generated.resources.backup_restore
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native save and open dialogs (AWT) for backup zips. */
class DesktopBackupFilePicker : BackupFilePicker {

    override suspend fun pickSaveTarget(suggestedName: String): String? {
        val title = getString(Res.string.backup_export)
        return withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply { file = suggestedName }
            dialog.isVisible = true
            val name = dialog.file ?: return@withContext null
            File(dialog.directory, if (name.endsWith(ZIP)) name else name + ZIP).absolutePath
        }
    }

    override suspend fun pickBackupFile(): String? {
        val title = getString(Res.string.backup_restore)
        return withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.lowercase().endsWith(ZIP) }
                // Lets the macOS dialog filter by extension as well
                file = "*$ZIP"
            }
            dialog.isVisible = true
            dialog.files.firstOrNull()?.absolutePath
        }
    }

    private companion object {
        const val ZIP = ".zip"
    }
}
