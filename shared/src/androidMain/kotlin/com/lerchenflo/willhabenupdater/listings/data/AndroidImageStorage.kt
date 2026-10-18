package com.lerchenflo.willhabenupdater.listings.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.listings.domain.ImageImportError
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.StoredImage
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import java.io.File

/**
 * Android has no image import yet (desktop first, see PLAN.md). Stored files can still be
 * listed and deleted, so cleanup works the same way on both platforms.
 */
class AndroidImageStorage(
    appDirectories: AppDirectories,
) : ImageStorage {

    private val imagesDir = File(appDirectories.imagesDir)

    override suspend fun importImage(sourcePath: String): Result<StoredImage, ImageImportError> =
        Result.Error(ImageImportError.UNSUPPORTED_FORMAT)

    override suspend fun deleteFile(fileName: String) {
        File(imagesDir, fileName).delete()
    }

    override suspend fun listFileNames(): List<String> =
        imagesDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    override fun absolutePath(fileName: String): String = File(imagesDir, fileName).absolutePath
}

/** No image picker on Android yet. */
class AndroidImagePicker : ImagePicker {
    override suspend fun pickImages(): List<String> = emptyList()
}
