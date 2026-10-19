package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.testutil.FakeImporter
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

/** Status actions like the web client's "Meine Anzeigen": a request on the link willhaben offers. */
class WillhabenStatusActionRunnerTest {

    private val database = inMemoryDatabase()
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), InMemoryImageStorage(), FakeTimeProvider(),
    )
    private val importer = FakeImporter()
    private val calls = mutableListOf<String>()
    private var status = HttpStatusCode.OK

    private val client = HttpClientFactory.createWillhabenClient(
        MockEngine { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            if (status != HttpStatusCode.OK) respondError(status) else respond("", HttpStatusCode.OK)
        }
    )
    private val runner = WillhabenStatusActionRunner(
        publicationDao = database.publicationDao(),
        writeDataSource = KtorWillhabenWriteDataSource(client),
        requestRunner = WillhabenRequestRunner(renewSession = { false }, backoff = {}),
        importer = importer,
    )

    @AfterTest
    fun tearDown() = database.close()

    /** Actions as the importer stores them from my-adverts. */
    private suspend fun publishedListing(actionsJson: String?): Long {
        val id = (listings.createListing(ListingContent(title = "Aprilia")) as Result.Success).data
        database.publicationDao().insert(
            ListingPublicationEntity(
                listingId = id, marketplace = Marketplace.WILLHABEN.name, marketplaceFieldsJson = null,
                remoteId = "1938189047", remoteUuid = null, remoteUrl = null, remoteStatus = "Inaktiv", expiresAt = null,
                remoteSnapshotJson = null, remoteSnapshotHash = null, syncState = SyncState.SYNCED.name,
                lastSyncedAt = null, lastError = null, remoteActionsJson = actionsJson,
            )
        )
        return id
    }

    private val inactiveActions = """[
        {"action":"ACTIVATE","serviceName":"myatz","relativePath":"/motor/activate/1938189047"},
        {"action":"DELETE","serviceName":"myatz","relativePath":"/motor/1938189047"}]"""

    @Test
    fun activateIsAPutOnTheOfferedLinkAndRefreshesTheListings() = runTest {
        val id = publishedListing(inactiveActions)

        val result = runner.perform(id, WillhabenStatusAction.ACTIVATE)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(calls).containsExactly("PUT /webapi/myatz/motor/activate/1938189047")
        assertThat(importer.calls).isEqualTo(1)
    }

    @Test
    fun deleteIsADelete() = runTest {
        val id = publishedListing(inactiveActions)

        runner.perform(id, WillhabenStatusAction.DELETE)

        assertThat(calls).containsExactly("DELETE /webapi/myatz/motor/1938189047")
    }

    @Test
    fun actionWillhabenDoesNotOfferIsNotSent() = runTest {
        val id = publishedListing(inactiveActions)

        assertThat(runner.perform(id, WillhabenStatusAction.SOLD)).isEqualTo(Result.Error(StatusActionError.NotAvailable))
        assertThat(calls).isEmpty()
    }

    @Test
    fun failedRequestIsReported() = runTest {
        status = HttpStatusCode.BadRequest
        val id = publishedListing(inactiveActions)

        assertThat(runner.perform(id, WillhabenStatusAction.ACTIVATE))
            .isEqualTo(Result.Error(StatusActionError.Failed(DataError.Network.BAD_REQUEST)))
        assertThat(importer.calls).isEqualTo(0)
    }
}
