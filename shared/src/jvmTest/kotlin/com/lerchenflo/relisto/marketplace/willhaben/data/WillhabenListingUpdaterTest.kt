package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.lerchenflo.relisto.activity.data.RoomActivityLog
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.listings.domain.imagesFingerprint
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.testutil.FakeImageDownloader
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

/** "Änderungen übertragen": local content and images go to an existing willhaben listing. */
class WillhabenListingUpdaterTest {

    private val database = inMemoryDatabase()
    private val time = FakeTimeProvider(now = 1_000L)
    private val imageStorage = InMemoryImageStorage()
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), imageStorage, time,
    )
    private val fields = RoomWillhabenFieldsDataSource(database.publicationDao())
    private val activityLog = RoomActivityLog(database.activityLogDao(), database.listingDao(), time)

    private val calls = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()
    /** What willhaben has right now; the snapshot stored locally is [SNAPSHOT]. */
    private var remoteHeading = "Rasentraktor Kart"
    private var editStatus = HttpStatusCode.OK
    private var reloads = 0

    private fun listingJson(heading: String) =
        """{"heading":"$heading","description":"Gut erhalten","price":180.0,"postCode":"6850","adId":111,"adUuid":"uuid-111",
           "phoneNo":"x","endDate":"2026-12-02T10:00:00.000+00:00","treeAttributes":[],"contextLinkList":{"contextLink":[
           {"id":"selfLinkEdit","serviceName":"iad","relativePath":"/bap/edit"},
           {"id":"imageList","serviceName":"iad","relativePath":"/atimage/111"}]}}"""

    private val engine = MockEngine { request ->
        val call = "${request.method.value} ${request.url.encodedPath}"
        calls += call
        (request.body as? OutgoingContent.ByteArrayContent)?.let { bodies[call] = it.bytes().decodeToString() }
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when (call) {
            "GET /webapi/iad/bap/111" -> {
                // After the edit willhaben answers with the sent content
                val heading = if (reloads++ == 0) remoteHeading else "Rasentraktor Kart neu"
                respond(listingJson(heading), HttpStatusCode.OK, json)
            }
            "PUT /webapi/iad/bap/edit" -> if (editStatus == HttpStatusCode.OK) respond("{}", HttpStatusCode.OK, json) else respondError(editStatus)
            "POST /webapi/iad-upload/atimage/temp" -> respond("""{"reference":"temp-${calls.size}"}""", HttpStatusCode.OK, json)
            "PUT /webapi/iad-upload/atimage/111/order-and-associate" -> respond("""{"advertImage":[]}""", HttpStatusCode.OK, json)
            "GET /webapi/iad/atimage/111" -> respond(
                """{"advertImage":[{"referenceImageUrl":"https://cache.willhaben.at/remote-1.jpg"}]}""", HttpStatusCode.OK, json,
            )
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private val downloader = FakeImageDownloader()
    private val client = HttpClientFactory.createWillhabenClient(engine)
    private val runner = WillhabenRequestRunner(renewSession = { false }, backoff = {})
    private val updater = WillhabenListingUpdater(
        writeDataSource = KtorWillhabenWriteDataSource(client),
        listingsDataSource = KtorWillhabenListingsDataSource(client),
        imageUploader = WillhabenImageUploader(KtorWillhabenWriteDataSource(client), imageStorage, runner),
        runner = runner,
        listingRepository = listings,
        fieldsDataSource = fields,
        publicationDao = database.publicationDao(),
        imageDownloader = downloader,
        activityLog = activityLog,
        timeProvider = time,
    )

    @AfterTest
    fun tearDown() = database.close()

    /** A listing that was in sync with willhaben, then edited locally ("Rasentraktor Kart neu"). */
    private suspend fun editedListing(syncState: SyncState = SyncState.LOCAL_CHANGES, remoteId: String? = "111"): Long {
        val id = (listings.createListing(ListingContent(title = "Rasentraktor Kart neu", description = "Gut erhalten", priceCents = 18000, postcode = "6850")) as Result.Success).data
        database.publicationDao().insert(
            ListingPublicationEntity(
                listingId = id, marketplace = Marketplace.WILLHABEN.name, marketplaceFieldsJson = null,
                remoteId = remoteId, remoteUuid = "uuid-111", remoteUrl = null, remoteStatus = "Aktiv",
                expiresAt = 5L, remoteSnapshotJson = SNAPSHOT, remoteSnapshotHash = "old", syncState = syncState.name,
                lastSyncedAt = 1L, lastError = null, syncedImagesHash = emptyList<String>().imagesFingerprint(),
            )
        )
        return id
    }

    private suspend fun publication(id: Long) = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!

    @Test
    fun localContentIsSentToTheEditLinkAndUnknownFieldsStay() = runTest {
        val id = editedListing()

        assertThat(updater.pushChanges(id)).isEqualTo(Result.Success(Unit))

        assertThat(calls).containsExactly("GET /webapi/iad/bap/111", "PUT /webapi/iad/bap/edit", "GET /webapi/iad/bap/111")
        val body = bodies.getValue("PUT /webapi/iad/bap/edit")
        assertThat(body).contains("\"heading\":\"Rasentraktor Kart neu\"")
        assertThat(body).contains("\"phoneNo\":\"x\"")
    }

    @Test
    fun publicationIsInSyncAfterwards() = runTest {
        val id = editedListing()

        updater.pushChanges(id)

        val publication = publication(id)
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication.remoteSnapshotJson!!).contains("Rasentraktor Kart neu")
        assertThat(publication.lastSyncedAt).isEqualTo(1_000L)
        assertThat(publication.lastError).isNull()
        assertThat(activityLog.getEntriesAsFlow().first().single().action).isEqualTo(ActivityAction.PUSH_CHANGES)
    }

    @Test
    fun unchangedImagesAreNotUploaded() = runTest {
        val id = editedListing()

        updater.pushChanges(id)

        assertThat(calls.filter { "atimage" in it }).containsExactly()
    }

    @Test
    fun changedImagesReplaceTheImagesOnWillhaben() = runTest {
        val id = editedListing()
        listings.addImageBytes(id, listOf("a".encodeToByteArray() to "jpg", "b".encodeToByteArray() to "jpg"))

        assertThat(updater.pushChanges(id)).isEqualTo(Result.Success(Unit))

        assertThat(calls.filter { "atimage" in it }).containsExactly(
            "POST /webapi/iad-upload/atimage/temp",
            "POST /webapi/iad-upload/atimage/temp",
            "PUT /webapi/iad-upload/atimage/111/order-and-associate",
        )
        val images = listings.getListingByIdAsFlow(id).first()!!.images
        assertThat(publication(id).syncedImagesHash).isEqualTo(images.map { it.sha256 }.imagesFingerprint())
    }

    @Test
    fun listingChangedOnWillhabenSinceTheLastSyncIsAConflict() = runTest {
        remoteHeading = "Changed on the website"
        val id = editedListing()

        assertThat(updater.pushChanges(id)).isEqualTo(Result.Error(PublishError.Conflict))

        assertThat(calls).doesNotContain("PUT /webapi/iad/bap/edit")
        assertThat(publication(id).syncState).isEqualTo(SyncState.CONFLICT.name)
        assertThat(publication(id).remoteSnapshotJson!!).contains("Changed on the website")
    }

    @Test
    fun keepingTheLocalVersionOverwritesWillhaben() = runTest {
        remoteHeading = "Changed on the website"
        val id = editedListing(syncState = SyncState.CONFLICT)

        assertThat(updater.pushChanges(id, overwriteRemote = true)).isEqualTo(Result.Success(Unit))

        assertThat(bodies.getValue("PUT /webapi/iad/bap/edit")).contains("\"heading\":\"Rasentraktor Kart neu\"")
        assertThat(publication(id).syncState).isEqualTo(SyncState.SYNCED.name)
    }

    @Test
    fun failedEditKeepsTheLocalChangesAndIsLogged() = runTest {
        editStatus = HttpStatusCode.InternalServerError
        val id = editedListing()

        assertThat(updater.pushChanges(id)).isEqualTo(Result.Error(PublishError.Failed(PublishStep.EDIT, DataError.Network.SERVER_ERROR)))

        assertThat(publication(id).syncState).isEqualTo(SyncState.LOCAL_CHANGES.name)
        assertThat(publication(id).lastError).isNotNull()
        val entry = activityLog.getEntriesAsFlow().first().single()
        assertThat(entry.success).isEqualTo(false)
    }

    @Test
    fun listingNotOnWillhabenCannotBePushed() = runTest {
        val gone = editedListing(syncState = SyncState.REMOTE_GONE)
        val local = editedListing(remoteId = null)

        assertThat(updater.pushChanges(gone)).isEqualTo(Result.Error(PublishError.NotPublished))
        assertThat(updater.pushChanges(local)).isEqualTo(Result.Error(PublishError.NotPublished))
        assertThat(calls).containsExactly()
    }

    @Test
    fun remoteVersionIsReadFromTheSnapshot() = runTest {
        val id = editedListing()

        assertThat(updater.remoteContent(id)?.title).isEqualTo("Rasentraktor Kart")
    }

    @Test
    fun takingWillhabensVersionReplacesLocalContentAndImages() = runTest {
        remoteHeading = "Changed on the website"
        val id = editedListing(syncState = SyncState.CONFLICT)
        listings.addImageBytes(id, listOf("local".encodeToByteArray() to "jpg"))

        assertThat(updater.takeRemote(id)).isEqualTo(Result.Success(Unit))

        val listing = listings.getListingByIdAsFlow(id).first()!!
        assertThat(listing.title).isEqualTo("Changed on the website")
        assertThat(downloader.downloaded).containsExactly("https://cache.willhaben.at/remote-1.jpg")
        assertThat(listing.images.size).isEqualTo(1)
        val publication = publication(id)
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication.syncedImagesHash).isEqualTo(listing.images.map { it.sha256 }.imagesFingerprint())
        assertThat(publication.remoteSnapshotJson!!).contains("Changed on the website")
        assertThat(calls).doesNotContain("PUT /webapi/iad/bap/edit")
    }

    private companion object {
        /** willhaben's listing at the last sync, before the local edit. */
        val SNAPSHOT = """{"heading":"Rasentraktor Kart","description":"Gut erhalten","price":180.0,"postCode":"6850","adId":111,
           "adUuid":"uuid-111","endDate":"2026-12-02T10:00:00.000+00:00","treeAttributes":[]}"""
    }
}
