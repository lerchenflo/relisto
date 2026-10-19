package com.lerchenflo.relisto.listings.data

import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.ImageImportError
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.listings.domain.StoredImage
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Copies images into the app image folder, named by the SHA-256 hash of their bytes.
 * WebP (what willhaben's image server sends) is stored as JPEG, so it can be shown and uploaded anywhere.
 */
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

    private fun storeBytes(originalBytes: ByteArray, extension: String): Result<StoredImage, ImageImportError> {
        val isWebp = originalBytes.isWebp()
        val normalizedExtension = when {
            isWebp -> "jpg"
            else -> extension.lowercase().removePrefix(".").let { if (it == "jpeg") "jpg" else it }
        }
        if (normalizedExtension !in SUPPORTED_EXTENSIONS) return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
        return try {
            val image = ImageIO.read(ByteArrayInputStream(originalBytes)) ?: return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
            val bytes = if (isWebp) image.toJpeg() else originalBytes
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

    /** RIFF container with the WebP form type, whatever the file name says. */
    private fun ByteArray.isWebp(): Boolean =
        size >= 12 && copyOfRange(0, 4).decodeToString() == "RIFF" && copyOfRange(8, 12).decodeToString() == "WEBP"

    /** JPEG has no transparency: transparent parts become white. */
    private fun BufferedImage.toJpeg(): ByteArray {
        val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()

        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
            writer.write(null, IIOImage(rgb, null, null), parameters)
        }
        writer.dispose()
        return output.toByteArray()
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
        const val JPEG_QUALITY = 0.92f
    }
}
