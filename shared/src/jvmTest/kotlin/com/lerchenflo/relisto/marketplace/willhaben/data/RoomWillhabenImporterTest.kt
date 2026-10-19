package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.listings.domain.Listing
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.ImportSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.testutil.FakeImageDownloader
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.FakeWillhabenCatalogRepository
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class RoomWillhabenImporterTest {

    private val database = inMemoryDatabase()
    private val time = FakeTimeProvider(now = 1_000L)
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), InMemoryImageStorage(), time,
    )
    private val downloader = FakeImageDownloader()
    private var remote: Result<List<RemoteListing>, DataError.Network> = Result.Success(emptyList())

    private val importer = RoomWillhabenImporter(
        source = object : WillhabenListingsSource {
            override suspend fun fetchMyListings() = remote
        },
        catalogRepository = FakeWillhabenCatalogRepository(),
        listingRepository = listings,
        publicationDao = database.publicationDao(),
        imageDownloader = downloader,
        timeProvider = time,
    )

    @AfterTest
    fun tearDown() = database.close()

    private fun remoteListing(id: String, title: String, images: List<String> = emptyList(), status: String = "Online") = RemoteListing(
        summary = RemoteListingSummary(id, "uuid-$id", 67, title, statusText = status),
        imageUrls = images,
        detailJson = """{"heading":"$title","description":"Text<br />zwei","price":45.0,"postCode":"6850","locationId":7,
            "location":"Dornbirn","categoryTreeId":3653,"categoryPath":{"path":[{"label":"Garten","code":"GARDEN"}]},
            "endDate":"2026-11-01T10:00:00.000+00:00","adUuid":"uuid-$id",
            "treeAttributes":[{"attributeNode":{"code":"Zustand"},"selectedAttributeValueNodes":[{"code":"gebraucht"}]}]}""",
    )

    private suspend fun allListings(): List<Listing> = listings.getListingsAsFlow().first()
    private suspend fun publication(listingId: Long) =
        database.publicationDao().getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!

    @Test
    fun newRemoteListingsAreCreatedLocally() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", images = listOf("https://img/1.jpg", "https://img/2.jpg"))))

        val result = importer.importMyListings()

        assertThat(result).isEqualTo(Result.Success(ImportSummary(found = 1, created = 1, updated = 0)))
        val listing = allListings().single()
        assertThat(listing.title).isEqualTo("Rasentraktor")
        assertThat(listing.description).isEqualTo("Text\nzwei")
        assertThat(listing.priceCents).isEqualTo(4500L)
        assertThat(listing.condition).isEqualTo(Condition.USED)
        assertThat(listing.images.size).isEqualTo(2)
        val publication = publication(listing.id)
        assertThat(publication.remoteId).isEqualTo("1")
        assertThat(publication.remoteUuid).isEqualTo("uuid-1")
        assertThat(publication.remoteStatus).isEqualTo("Online")
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication.expiresAt).isEqualTo(1793527200000L)
    }

    @Test
    fun listingReplacedByRepublishIsNotImportedAgain() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", status = "Abgelaufen")))
        importer.importMyListings()
        val listing = allListings().single()
        database.publicationDao().update(publication(listing.id).copy(remoteId = "2", replacedRemoteIds = "1"))
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", status = "Abgelaufen"), remoteListing("2", "Rasentraktor")))

        val result = importer.importMyListings()

        assertThat(result).isEqualTo(Result.Success(ImportSummary(found = 1, created = 0, updated = 0)))
        assertThat(allListings().size).isEqualTo(1)
        assertThat(publication(listing.id).remoteStatus).isEqualTo("Online")
    }

    @Test
    fun repeatedImportWithoutChangesChangesNothing() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", images = listOf("https://img/1.jpg"))))
        importer.importMyListings()

        val result = importer.importMyListings()

        assertThat(result).isEqualTo(Result.Success(ImportSummary(found = 1, created = 0, updated = 0)))
        assertThat(allListings().size).isEqualTo(1)
        assertThat(downloader.downloaded.size).isEqualTo(1)
    }

    @Test
    fun listingImportedWithoutImagesGetsThemOnTheNextImport() = runTest {
        // e.g. imported while images could not be read yet
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor")))
        importer.importMyListings()
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", images = listOf("https://img/1.jpg", "https://img/2.jpg"))))

        importer.importMyListings()

        assertThat(allListings().single().images.size).isEqualTo(2)
    }

    @Test
    fun remoteChangeUpdatesUneditedListing() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor")))
        importer.importMyListings()
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor neu", status = "Abgelaufen")))

        val result = importer.importMyListings()

        assertThat(result).isEqualTo(Result.Success(ImportSummary(found = 1, created = 0, updated = 1)))
        val listing = allListings().single()
        assertThat(listing.title).isEqualTo("Rasentraktor neu")
        assertThat(publication(listing.id).syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication(listing.id).remoteStatus).isEqualTo("Abgelaufen")
    }

    @Test
    fun remoteChangeOnLocallyEditedListingIsAConflict() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor")))
        importer.importMyListings()
        val listing = allListings().single()
        listings.updateContent(listing.id, com.lerchenflo.relisto.listings.domain.ListingContent(title = "Mein Titel", postcode = "6850"))
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor neu")))

        importer.importMyListings()

        assertThat(allListings().single().title).isEqualTo("Mein Titel")
        assertThat(publication(listing.id).syncState).isEqualTo(SyncState.CONFLICT.name)
    }

    @Test
    fun localEditsWithoutRemoteChangeStayLocal() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor")))
        importer.importMyListings()
        val listing = allListings().single()
        listings.updateContent(listing.id, com.lerchenflo.relisto.listings.domain.ListingContent(title = "Mein Titel", postcode = "6850"))

        importer.importMyListings()

        assertThat(allListings().single().title).isEqualTo("Mein Titel")
        assertThat(publication(listing.id).syncState).isEqualTo(SyncState.LOCAL_CHANGES.name)
    }

    @Test
    fun brokenImageIsSkipped() = runTest {
        remote = Result.Success(listOf(remoteListing("1", "Rasentraktor", images = listOf("https://img/broken.jpg", "https://img/2.jpg"))))

        importer.importMyListings()

        assertThat(allListings().single().images.size).isEqualTo(1)
    }

    @Test
    fun fetchErrorIsReturned() = runTest {
        remote = Result.Error(DataError.Network.UNAUTHORIZED)

        assertThat(importer.importMyListings()).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }
}
