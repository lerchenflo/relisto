package com.lerchenflo.relisto.listings.data

import com.lerchenflo.relisto.listings.domain.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.editor_images_add
import java.awt.FileDialog
import java.awt.Frame

/** Native file dialog (AWT) with multi-selection, filtered to image files. */
class DesktopImagePicker : ImagePicker {

    override suspend fun pickImages(): List<String> {
        val title = getString(Res.string.editor_images_add)
        return withContext(Dispatchers.Main) { showDialog(title) }
    }

    private fun showDialog(title: String): List<String> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
            // Lets the macOS dialog filter by extension as well
            file = IMAGE_EXTENSIONS.joinToString(";") { "*.$it" }
        }
        dialog.isVisible = true
        return dialog.files.map { it.absolutePath }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
    }
}
