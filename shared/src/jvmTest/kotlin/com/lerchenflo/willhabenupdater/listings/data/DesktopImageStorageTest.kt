package com.lerchenflo.willhabenupdater.listings.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.listings.domain.ImageImportError
import com.lerchenflo.willhabenupdater.listings.domain.StoredImage
import com.lerchenflo.willhabenupdater.testutil.TempAppDirectories
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
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
}
