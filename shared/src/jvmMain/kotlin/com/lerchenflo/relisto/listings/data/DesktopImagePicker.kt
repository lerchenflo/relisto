package com.lerchenflo.relisto.listings.data

import com.lerchenflo.relisto.listings.domain.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame

/** Native file dialog (AWT) with multi-selection, filtered to image files. */
class DesktopImagePicker : ImagePicker {

    override suspend fun pickImages(): List<String> = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, "Add images", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
            // Lets the macOS dialog filter by extension as well
            file = IMAGE_EXTENSIONS.joinToString(";") { "*.$it" }
        }
        dialog.isVisible = true
        dialog.files.map { it.absolutePath }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
    }
}
