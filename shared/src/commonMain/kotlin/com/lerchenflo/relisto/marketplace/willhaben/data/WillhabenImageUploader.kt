package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.listings.domain.ListingImage
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Replaces all images of a willhaben listing with the local ones, like the web client: each image is
 * uploaded to the temporary store (up to four at a time), then the ordered list is attached to the
 * listing (first = cover).
 */
class WillhabenImageUploader(
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val imageStorage: ImageStorage,
    private val runner: WillhabenRequestRunner,
) {

    /** [imageList] is the listing's `imageList` link. Returns the error, null on success. */
    suspend fun replaceImages(images: List<ListingImage>, imageList: ContextLink): PublishError? {
        val files = images.sortedBy { it.position }.map { image ->
            val bytes = imageStorage.readBytes(image.filePath)
                ?: return PublishError.Failed(PublishStep.IMAGES, DataError.Local.NOT_FOUND)
            bytes to image.filePath.substringAfterLast('/').substringAfterLast('\\')
        }
        // Up to four uploads at a time; the references keep the image order
        val uploaded = coroutineScope {
            files.map { (bytes, fileName) -> async { runner.runParallel { writeDataSource.uploadTempImage(bytes, fileName) } } }.awaitAll()
        }
        val references = uploaded.map { result ->
            when (result) {
                is Result.Error -> return PublishError.Failed(PublishStep.IMAGES, result.error)
                is Result.Success -> result.data
            }
        }
        val order = buildJsonObject {
            put("advertImage", buildJsonArray { references.forEach { add(buildJsonObject { put("reference", it) }) } })
            put("floorPlans", JsonArray(emptyList()))
        }
        // Image endpoints live on the upload service, the link names the normal one
        val route = "/iad-upload${imageList.relativePath}/order-and-associate"
        return (runner.run { writeDataSource.put(route, order) } as? Result.Error)
            ?.let { PublishError.Failed(PublishStep.IMAGES, it.error) }
    }
}
