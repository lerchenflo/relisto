package com.lerchenflo.relisto.backup.data

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.lerchenflo.relisto.backup.domain.BackupFilePicker
import kotlinx.coroutines.CompletableDeferred

/** System document dialogs; the results are content URIs. */
class AndroidBackupFilePicker : BackupFilePicker {

    private var saveLauncher: ActivityResultLauncher<String>? = null
    private var openLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun register(activity: ComponentActivity) {
        saveLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { pending?.complete(it) }
        openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { pending?.complete(it) }
    }

    override suspend fun pickSaveTarget(suggestedName: String): String? {
        val launcher = saveLauncher ?: return null
        val result = CompletableDeferred<Uri?>().also { pending = it }
        launcher.launch(suggestedName)
        return result.await()?.toString()
    }

    override suspend fun pickBackupFile(): String? {
        val launcher = openLauncher ?: return null
        val result = CompletableDeferred<Uri?>().also { pending = it }
        launcher.launch(arrayOf(ZIP_TYPE, "application/octet-stream"))
        return result.await()?.toString()
    }

    private companion object {
        const val ZIP_TYPE = "application/zip"
    }
}
