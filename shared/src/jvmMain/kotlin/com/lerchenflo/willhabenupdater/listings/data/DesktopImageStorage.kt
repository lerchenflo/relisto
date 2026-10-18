package com.lerchenflo.willhabenupdater.listings.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.listings.domain.ImageImportError
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.StoredImage
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.imageio.ImageIO

/** Copies images into the app image folder, named by the SHA-256 hash of their bytes. */
class DesktopImageStorage(
    appDirectories: AppDirectories,
) : ImageStorage {

    private val imagesDir = File(appDirectories.imagesDir)

    override suspend fun importImage(sourcePath: String): Result<StoredImage, ImageImportError> =
        withContext(Dispatchers.IO) {
            val source = File(sourcePath)
            if (!source.isFile) return@withContext Result.Error(ImageImportError.FILE_NOT_FOUND)
            val bytes = try {
                source.readBytes()
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext Result.Error(ImageImportError.UNKNOWN)
            }
            storeBytes(bytes, source.extension)
        }

    override suspend fun importImageBytes(bytes: ByteArray, extension: String): Result<StoredImage, ImageImportError> =
        withContext(Dispatchers.IO) { storeBytes(bytes, extension) }

    private fun storeBytes(bytes: ByteArray, extension: String): Result<StoredImage, ImageImportError> {
        val normalizedExtension = extension.lowercase().removePrefix(".").let { if (it == "jpeg") "jpg" else it }
        if (normalizedExtension !in SUPPORTED_EXTENSIONS) return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
        return try {
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val fileName = "$sha256.$normalizedExtension"
            val target = File(imagesDir, fileName)
            if (!target.exists()) target.writeBytes(bytes)
            Result.Success(StoredImage(fileName, sha256, image.width, image.height))
        } catch (e: IOException) {
            e.printStackTrace()
            val diskFull = e.message?.contains("space", ignoreCase = true) == true
            Result.Error(if (diskFull) ImageImportError.DISK_FULL else ImageImportError.UNKNOWN)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.Error(ImageImportError.UNKNOWN)
        }
    }

    override suspend fun readBytes(absolutePath: String): ByteArray? =
        withContext(Dispatchers.IO) { File(absolutePath).takeIf { it.isFile }?.readBytes() }

    override suspend fun deleteFile(fileName: String) {
        withContext(Dispatchers.IO) { File(imagesDir, fileName).delete() }
    }

    override suspend fun listFileNames(): List<String> = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
    }

    override fun absolutePath(fileName: String): String = File(imagesDir, fileName).absolutePath

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
    }
}
