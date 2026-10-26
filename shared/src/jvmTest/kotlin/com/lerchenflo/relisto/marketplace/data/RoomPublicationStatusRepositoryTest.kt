package com.lerchenflo.relisto.marketplace.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class RoomPublicationStatusRepositoryTest {

    private val database = inMemoryDatabase()
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), InMemoryImageStorage(), FakeTimeProvider(),
    )
    private val repository = RoomPublicationStatusRepository(database.publicationDao())

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun publication(status: String, replacedRemoteIds: String?, actionsJson: String? = null, url: String? = null) {
        val id = (listings.createListing(ListingContent(title = "x")) as Result.Success).data
        database.publicationDao().insert(
            ListingPublicationEntity(
                listingId = id, marketplace = Marketplace.WILLHABEN.name, marketplaceFieldsJson = null,
                remoteId = "r$id", remoteUuid = null, remoteUrl = url, remoteStatus = status, expiresAt = null,
                remoteSnapshotJson = null, remoteSnapshotHash = null, syncState = SyncState.SYNCED.name,
                lastSyncedAt = null, lastError = null, replacedRemoteIds = replacedRemoteIds, remoteActionsJson = actionsJson,
            )
        )
    }

    @Test
    fun offeredStatusActionsAreListed() = runTest {
        publication("Inaktiv", null, """[{"action":"ACTIVATE","serviceName":"myatz","relativePath":"/a"},{"action":"DELETE","serviceName":"myatz","relativePath":"/d"}]""")
        publication("Online", null, null)

        val statuses = repository.getStatusesAsFlow().first()

        assertThat(statuses[0].availableActions).isEqualTo(setOf("ACTIVATE", "DELETE"))
        assertThat(statuses[1].availableActions).isEqualTo(emptySet())
    }

    @Test
    fun draftThatReplacedAnExpiredListingIsARenewDraft() = runTest {
        publication("Bearbeitung", replacedRemoteIds = "111")
        publication("Bearbeitung", replacedRemoteIds = null)

        val statuses = repository.getStatusesAsFlow().first()

        assertThat(statuses[0].isRenewDraft).isTrue()
        assertThat(statuses[1].isRenewDraft).isFalse()
    }

    @Test
    fun remoteUrlIsPassedOn() = runTest {
        publication("Online", null, url = "https://www.willhaben.at/iad/object?adId=1")

        assertThat(repository.getStatusesAsFlow().first()[0].remoteUrl).isEqualTo("https://www.willhaben.at/iad/object?adId=1")
    }
}
