package com.lerchenflo.willhabenupdater.listings.domain

import com.lerchenflo.willhabenupdater.app.domain.Error
import com.lerchenflo.willhabenupdater.app.domain.Result

/**
 * Platform file store for listing images. Images are copied into the app image folder
 * and named by their SHA-256 hash, so the same image added twice is stored once.
 */
interface ImageStorage {
    suspend fun importImage(sourcePath: String): Result<StoredImage, ImageImportError>

    /** Stores image bytes, e.g. downloaded from a marketplace; [extension] like "jpg". */
    suspend fun importImageBytes(bytes: ByteArray, extension: String): Result<StoredImage, ImageImportError>
    suspend fun deleteFile(fileName: String)
    suspend fun listFileNames(): List<String>
    fun absolutePath(fileName: String): String
}

data class StoredImage(
    val fileName: String,
    val sha256: String,
    val width: Int,
    val height: Int,
)

enum class ImageImportError : Error {
    FILE_NOT_FOUND,
    UNSUPPORTED_FORMAT,
    DISK_FULL,
    UNKNOWN
}

/** Opens the platform file chooser for images. Returns absolute paths; empty when cancelled. */
interface ImagePicker {
    suspend fun pickImages(): List<String>
}

/** Downloads an image; the result is the bytes and the file extension ("jpg", "png", ...). */
interface ImageDownloader {
    suspend fun download(url: String): Result<Pair<ByteArray, String>, com.lerchenflo.willhabenupdater.app.domain.DataError.Network>
}
