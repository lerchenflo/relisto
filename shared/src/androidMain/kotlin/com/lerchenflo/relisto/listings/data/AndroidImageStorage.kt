package com.lerchenflo.relisto.listings.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.ImageImportError
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.listings.domain.StoredImage
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Copies images into the app image folder, named by the SHA-256 hash of their bytes, like on desktop.
 * Sources are files or `content://` URIs from the photo picker. Formats other than JPEG, PNG, GIF
 * and BMP (WebP from willhaben, HEIC from the camera) are stored as JPEG, so they can be uploaded anywhere.
 */
class AndroidImageStorage(
    private val context: Context,
    appDirectories: AppDirectories,
) : ImageStorage {

    private val imagesDir = File(appDirectories.imagesDir)

    override suspend fun importImage(sourcePath: String): Result<StoredImage, ImageImportError> =
        withContext(Dispatchers.IO) {
            val bytes = try {
                if (sourcePath.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(sourcePath))?.use { it.readBytes() }
                } else {
                    File(sourcePath).takeIf { it.isFile }?.readBytes()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext Result.Error(ImageImportError.UNKNOWN)
            } catch (e: SecurityException) {
                e.printStackTrace()
                return@withContext Result.Error(ImageImportError.FILE_NOT_FOUND)
            } ?: return@withContext Result.Error(ImageImportError.FILE_NOT_FOUND)
            storeBytes(bytes)
        }

    override suspend fun importImageBytes(bytes: ByteArray, extension: String): Result<StoredImage, ImageImportError> =
        withContext(Dispatchers.IO) { storeBytes(bytes) }

    /** The format comes from the bytes: picker URIs carry no file extension. */
    private fun storeBytes(originalBytes: ByteArray): Result<StoredImage, ImageImportError> = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
        } else {
            val keptExtension = KEPT_FORMATS[bounds.outMimeType]
            val bytes = if (keptExtension != null) originalBytes else originalBytes.toJpeg()
            if (bytes == null) {
                Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
            } else {
                val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val fileName = "$sha256.${keptExtension ?: "jpg"}"
                val target = File(imagesDir, fileName)
                if (!target.exists()) target.writeBytes(bytes)
                Result.Success(StoredImage(fileName, sha256, bounds.outWidth, bounds.outHeight))
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
        val diskFull = e.message?.contains("space", ignoreCase = true) == true
        Result.Error(if (diskFull) ImageImportError.DISK_FULL else ImageImportError.UNKNOWN)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        e.printStackTrace()
        Result.Error(ImageImportError.UNKNOWN)
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

    /** JPEG has no transparency: transparent parts become white. Null when Android cannot decode the image. */
    private fun ByteArray.toJpeg(): ByteArray? {
        val decoded = BitmapFactory.decodeByteArray(this, 0, size) ?: return null
        val rgb = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(rgb).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        decoded.recycle()
        val output = ByteArrayOutputStream()
        rgb.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        rgb.recycle()
        return output.toByteArray()
    }

    private companion object {
        /** Formats stored as they are, by the MIME type Android reads from the bytes. */
        val KEPT_FORMATS = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/gif" to "gif",
            "image/bmp" to "bmp",
        )
        const val JPEG_QUALITY = 92
    }
}
