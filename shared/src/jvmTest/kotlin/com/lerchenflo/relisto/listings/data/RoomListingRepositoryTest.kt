package com.lerchenflo.relisto.listings.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.listings.domain.AddImagesResult
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.listings.domain.FailedImage
import com.lerchenflo.relisto.listings.domain.ImageImportError
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.listings.domain.Listing
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.StoredImage
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class RoomListingRepositoryTest {

    /** Stores "files" in memory; a source path "x.png" becomes file "sha-x.png" with hash "sha-x". */
    private class FakeImageStorage : ImageStorage {
        val files = mutableSetOf<String>()
        val unreadable = mutableSetOf<String>()

        override suspend fun importImage(sourcePath: String): Result<StoredImage, ImageImportError> {
            if (sourcePath in unreadable) return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
            val hash = "sha-" + sourcePath.substringBefore('.')
            val fileName = "$hash.png"
            files += fileName
            return Result.Success(StoredImage(fileName = fileName, sha256 = hash, width = 800, height = 600))
        }

        override suspend fun importImageBytes(bytes: ByteArray, extension: String): Result<StoredImage, ImageImportError> {
            val text = bytes.decodeToString()
            if (text == "broken") return Result.Error(ImageImportError.UNSUPPORTED_FORMAT)
            val hash = "sha-$text"
            files += "$hash.$extension"
            return Result.Success(StoredImage(fileName = "$hash.$extension", sha256 = hash, width = 800, height = 600))
        }

        override suspend fun readBytes(absolutePath: String): ByteArray? = null

        override suspend fun deleteFile(fileName: String) {
            files -= fileName
        }

        override suspend fun listFileNames(): List<String> = files.toList()
        override fun absolutePath(fileName: String): String = "/images/$fileName"
    }

    private val database: AppDatabase = inMemoryDatabase()
    private val storage = FakeImageStorage()
    private val time = FakeTimeProvider(now = 1_000L)
    private val repository = RoomListingRepository(
        listingDao = database.listingDao(),
        imageDao = database.listingImageDao(),
        publicationDao = database.publicationDao(),
        imageStorage = storage,
        timeProvider = time,
    )

    private val content = ListingContent(
        title = "Kinderfahrrad 20 Zoll",
        description = "Gut erhalten",
        priceCents = 4500,
        condition = Condition.USED,
        postcode = "6850",
    )

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun create(listingContent: ListingContent = content): Long =
        (repository.createListing(listingContent) as Result.Success).data

    private suspend fun listing(id: Long): Listing? = repository.getListingByIdAsFlow(id).first()

    private suspend fun insertWillhabenPublication(listingId: Long, syncState: SyncState) {
        database.publicationDao().insert(
            ListingPublicationEntity(
                listingId = listingId,
                marketplace = Marketplace.WILLHABEN.name,
                marketplaceFieldsJson = """{"locationId":1}""",
                remoteId = "123456789",
                remoteUuid = "uuid",
                remoteUrl = "https://www.willhaben.at/iad/123456789",
                remoteStatus = "ACTIVE",
                expiresAt = 99,
                remoteSnapshotJson = "{}",
                remoteSnapshotHash = "hash",
                syncState = syncState.name,
                lastSyncedAt = 5,
                lastError = null,
            )
        )
    }

    @Test
    fun createdListingHasContentAndCreationTime() = runTest {
        val id = create()

        val listing = listing(id)!!
        assertThat(listing.title).isEqualTo("Kinderfahrrad 20 Zoll")
        assertThat(listing.priceCents).isEqualTo(4500L)
        assertThat(listing.condition).isEqualTo(Condition.USED)
        assertThat(listing.postcode).isEqualTo("6850")
        assertThat(listing.createdAt).isEqualTo(1_000L)
        assertThat(listing.archived).isFalse()
    }

    @Test
    fun listingsAreSortedByLastChangeNewestFirst() = runTest {
        val first = create(content.copy(title = "A"))
        time.now = 2_000
        val second = create(content.copy(title = "B"))
        time.now = 3_000
        repository.updateContent(first, content.copy(title = "A2"))

        val ids = repository.getListingsAsFlow().first().map { it.id }

        assertThat(ids).containsExactly(first, second)
    }

    @Test
    fun updateContentSavesFieldsAndChangeTime() = runTest {
        val id = create()
        time.now = 5_000

        repository.updateContent(id, content.copy(title = "Neu", priceCents = null, condition = null))

        val listing = listing(id)!!
        assertThat(listing.title).isEqualTo("Neu")
        assertThat(listing.priceCents).isNull()
        assertThat(listing.condition).isNull()
        assertThat(listing.updatedAt).isEqualTo(5_000L)
    }

    @Test
    fun updateWithSameContentKeepsChangeTime() = runTest {
        val id = create()
        time.now = 5_000

        repository.updateContent(id, content)

        assertThat(listing(id)!!.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun updateOfUnknownListingReturnsNotFound() = runTest {
        val result = repository.updateContent(999, content)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.NOT_FOUND))
    }

    @Test
    fun contentChangeMarksSyncedPublicationAsLocalChanges() = runTest {
        val id = create()
        insertWillhabenPublication(id, SyncState.SYNCED)

        repository.updateContent(id, content.copy(title = "Neu"))

        val publication = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!
        assertThat(publication.syncState).isEqualTo(SyncState.LOCAL_CHANGES.name)
    }

    @Test
    fun archiveFlagIsSaved() = runTest {
        val id = create()

        repository.setArchived(id, true)

        assertThat(listing(id)!!.archived).isTrue()
    }

    @Test
    fun addedImagesAreAppendedInOrderWithStoredPath() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png"))

        val result = repository.addImages(id, listOf("b.png", "c.png"))

        assertThat(result).isEqualTo(Result.Success(AddImagesResult(addedCount = 2, failedFiles = emptyList())))
        assertThat(listing(id)!!.images.map { it.filePath }).containsExactly(
            "/images/sha-a.png", "/images/sha-b.png", "/images/sha-c.png"
        )
    }

    @Test
    fun sameImageTwiceInOneListingIsAddedOnce() = runTest {
        val id = create()

        val result = repository.addImages(id, listOf("a.png", "a.png"))

        assertThat(result).isEqualTo(Result.Success(AddImagesResult(addedCount = 1, failedFiles = emptyList())))
        assertThat(listing(id)!!.images.size).isEqualTo(1)
    }

    @Test
    fun unreadableImagesAreReportedAndSkipped() = runTest {
        val id = create()
        storage.unreadable += "broken.png"

        val result = repository.addImages(id, listOf("broken.png", "a.png"))

        assertThat(result).isEqualTo(
            Result.Success(
                AddImagesResult(
                    addedCount = 1,
                    failedFiles = listOf(FailedImage("broken.png", ImageImportError.UNSUPPORTED_FORMAT))
                )
            )
        )
    }

    @Test
    fun removingImageClosesGapAndDeletesUnusedFile() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png", "b.png", "c.png"))
        val imageB = listing(id)!!.images[1]

        repository.removeImage(imageB.id)

        val images = listing(id)!!.images
        assertThat(images.map { it.filePath }).containsExactly("/images/sha-a.png", "/images/sha-c.png")
        assertThat(images.map { it.position }).containsExactly(0, 1)
        assertThat("sha-b.png" in storage.files).isFalse()
    }

    @Test
    fun removingImageKeepsFileUsedByOtherListing() = runTest {
        val first = create()
        val second = create()
        repository.addImages(first, listOf("a.png"))
        repository.addImages(second, listOf("a.png"))

        repository.removeImage(listing(first)!!.images.single().id)

        assertThat("sha-a.png" in storage.files).isTrue()
    }

    @Test
    fun reorderingImagesChangesCover() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png", "b.png", "c.png"))
        val (a, b, c) = listing(id)!!.images

        repository.reorderImages(id, listOf(c.id, a.id, b.id))

        assertThat(listing(id)!!.images.map { it.id }).containsExactly(c.id, a.id, b.id)
    }

    @Test
    fun duplicateCopiesContentImagesAndMarketplaceFieldsButNoRemoteIds() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png", "b.png"))
        insertWillhabenPublication(id, SyncState.SYNCED)
        repository.setArchived(id, true)

        val copyId = (repository.duplicateListing(id) as Result.Success).data

        val copy = listing(copyId)!!
        assertThat(copy.title).isEqualTo("Kinderfahrrad 20 Zoll")
        assertThat(copy.archived).isFalse()
        assertThat(copy.images.map { it.filePath }).containsExactly("/images/sha-a.png", "/images/sha-b.png")
        val publication = database.publicationDao().getByListingAndMarketplace(copyId, Marketplace.WILLHABEN.name)!!
        assertThat(publication.marketplaceFieldsJson).isEqualTo("""{"locationId":1}""")
        assertThat(publication.remoteId).isNull()
        assertThat(publication.remoteSnapshotJson).isNull()
        assertThat(publication.syncState).isEqualTo(SyncState.LOCAL_ONLY.name)
    }

    @Test
    fun deletingListingRemovesItsRowsAndUnusedFiles() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png"))
        insertWillhabenPublication(id, SyncState.LOCAL_ONLY)

        repository.deleteListing(id)

        assertThat(listing(id)).isNull()
        assertThat(database.publicationDao().getByListingId(id).isEmpty()).isTrue()
        assertThat(storage.files.isEmpty()).isTrue()
    }

    @Test
    fun cleanupDeletesFilesWithoutListing() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png"))
        storage.files += "orphan.png"

        repository.deleteUnusedImageFiles()

        assertThat(storage.files.toList()).containsExactly("sha-a.png")
    }

    @Test
    fun downloadedImagesAreAppendedAndBrokenOnesSkipped() = runTest {
        val id = create()
        repository.addImages(id, listOf("a.png"))

        val added = repository.addImageBytes(
            id,
            listOf("x".encodeToByteArray() to "jpg", "broken".encodeToByteArray() to "jpg", "y".encodeToByteArray() to "jpg"),
        )

        assertThat(added).isEqualTo(Result.Success(2))
        assertThat(listing(id)!!.images.map { it.filePath }).containsExactly(
            "/images/sha-a.png", "/images/sha-x.jpg", "/images/sha-y.jpg"
        )
    }
}
