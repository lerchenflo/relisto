package com.lerchenflo.relisto.listings.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.ImageImportError
import com.lerchenflo.relisto.listings.domain.StoredImage
import com.lerchenflo.relisto.testutil.TempAppDirectories
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.Test

class DesktopImageStorageTest {

    private val dirs = TempAppDirectories()
    private val sourceDir = File(dirs.root, "source").apply { mkdirs() }
    private val storage = DesktopImageStorage(dirs)

    @AfterTest
    fun tearDown() = dirs.delete()

    private fun writeImage(name: String, width: Int, height: Int, format: String): File {
        val file = File(sourceDir, name)
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file)
        return file
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    /** willhaben's image server sends WebP, even for URLs ending in ".jpg". */
    @OptIn(ExperimentalEncodingApi::class)
    private val lossyWebp = Base64.decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA")

    @OptIn(ExperimentalEncodingApi::class)
    private val losslessWebp = Base64.decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")

    @Test
    fun webpIsStoredAsJpegWhateverItsExtensionSays() = runTest {
        listOf(lossyWebp to "jpg", losslessWebp to "webp").forEach { (bytes, extension) ->
            val stored = (storage.importImageBytes(bytes, extension) as Result.Success).data

            assertThat(stored.fileName.endsWith(".jpg")).isTrue()
            val file = File(dirs.imagesDir, stored.fileName)
            // JPEG files start with FF D8
            assertThat(file.readBytes().take(2)).isEqualTo(listOf(0xFF.toByte(), 0xD8.toByte()))
            assertThat(ImageIO.read(file).width).isEqualTo(1)
            assertThat(stored.width to stored.height).isEqualTo(1 to 1)
        }
    }

    @Test
    fun pngIsCopiedAndNamedByItsHash() = runTest {
        val source = writeImage("photo.png", 40, 30, "png")
        val hash = sha256(source)

        val result = storage.importImage(source.absolutePath)

        assertThat(result).isEqualTo(Result.Success(StoredImage("$hash.png", hash, 40, 30)))
        assertThat(File(dirs.imagesDir, "$hash.png").readBytes().contentEquals(source.readBytes())).isTrue()
    }

    @Test
    fun jpegExtensionIsStoredAsJpg() = runTest {
        val source = writeImage("photo.jpeg", 10, 10, "jpg")

        val result = storage.importImage(source.absolutePath) as Result.Success

        assertThat(result.data.fileName).isEqualTo("${sha256(source)}.jpg")
    }

    @Test
    fun sameImageImportedTwiceIsStoredOnce() = runTest {
        val source = writeImage("photo.png", 10, 10, "png")
        val copy = source.copyTo(File(sourceDir, "copy.png"))

        storage.importImage(source.absolutePath)
        storage.importImage(copy.absolutePath)

        assertThat(File(dirs.imagesDir).listFiles()!!.size).isEqualTo(1)
    }

    @Test
    fun missingFileIsReported() = runTest {
        val result = storage.importImage(File(sourceDir, "missing.png").absolutePath)

        assertThat(result).isEqualTo(Result.Error(ImageImportError.FILE_NOT_FOUND))
    }

    @Test
    fun fileThatIsNoImageIsUnsupported() = runTest {
        val fake = File(sourceDir, "notes.png").apply { writeText("not an image") }

        val result = storage.importImage(fake.absolutePath)

        assertThat(result).isEqualTo(Result.Error(ImageImportError.UNSUPPORTED_FORMAT))
    }

    @Test
    fun unsupportedExtensionIsRejected() = runTest {
        val source = writeImage("photo.png", 10, 10, "png")
        val webp = source.copyTo(File(sourceDir, "photo.webp"))

        val result = storage.importImage(webp.absolutePath)

        assertThat(result).isEqualTo(Result.Error(ImageImportError.UNSUPPORTED_FORMAT))
    }

    @Test
    fun storedFilesCanBeListedAndDeleted() = runTest {
        val stored = (storage.importImage(writeImage("photo.png", 10, 10, "png").absolutePath) as Result.Success).data

        assertThat(storage.listFileNames()).containsExactly(stored.fileName)
        storage.deleteFile(stored.fileName)

        assertThat(File(storage.absolutePath(stored.fileName)).exists()).isFalse()
        assertThat(storage.listFileNames().isEmpty()).isTrue()
    }

    @Test
    fun downloadedBytesAreStoredLikeFiles() = runTest {
        val source = writeImage("photo.jpg", 30, 20, "jpg")

        val result = storage.importImageBytes(source.readBytes(), "jpeg")

        val hash = sha256(source)
        assertThat(result).isEqualTo(Result.Success(StoredImage("$hash.jpg", hash, 30, 20)))
        assertThat(File(dirs.imagesDir, "$hash.jpg").exists()).isTrue()
    }

    @Test
    fun downloadedBytesThatAreNoImageAreRejected() = runTest {
        val result = storage.importImageBytes("<html>".encodeToByteArray(), "jpg")

        assertThat(result).isEqualTo(Result.Error(ImageImportError.UNSUPPORTED_FORMAT))
    }
}
