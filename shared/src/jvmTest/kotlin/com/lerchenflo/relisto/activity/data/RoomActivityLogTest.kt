package com.lerchenflo.relisto.activity.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RoomActivityLogTest {

    private val database = inMemoryDatabase()
    private val time = FakeTimeProvider(now = 1_000L)
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), InMemoryImageStorage(), time,
    )
    private val log = RoomActivityLog(database.activityLogDao(), database.listingDao(), time)

    private suspend fun listing(title: String): Long =
        (listings.createListing(ListingContent(title = title)) as Result.Success).data

    @Test
    fun entriesKeepTheListingTitleNewestFirst() = runTest {
        val id = listing("Rasentraktor Kart")
        log.record(id, Marketplace.WILLHABEN, ActivityAction.PUSH_CHANGES, success = true)
        time.now = 2_000L
        log.record(id, Marketplace.WILLHABEN, ActivityAction.DEACTIVATE, success = false, message = "SERVER_ERROR")

        val entries = log.getEntriesAsFlow().first()

        assertThat(entries.map { it.action }).containsExactly(ActivityAction.DEACTIVATE, ActivityAction.PUSH_CHANGES)
        assertThat(entries.first().listingTitle).isEqualTo("Rasentraktor Kart")
        assertThat(entries.first().message).isEqualTo("SERVER_ERROR")
        assertThat(entries.first().success).isEqualTo(false)
    }

    @Test
    fun entriesStayWhenTheListingIsDeleted() = runTest {
        val id = listing("Rasentraktor Kart")
        log.record(id, Marketplace.WILLHABEN, ActivityAction.DELETE, success = true)
        listings.deleteListing(id)

        assertThat(log.getEntriesAsFlow().first().single().listingTitle).isEqualTo("Rasentraktor Kart")
    }

    @Test
    fun entriesOlderThanHalfAYearAreRemoved() = runTest {
        val id = listing("Alt")
        log.record(id, Marketplace.WILLHABEN, ActivityAction.REPUBLISH, success = true)
        time.now = 1_000L + 181L * 24 * 60 * 60 * 1000
        log.record(id, Marketplace.WILLHABEN, ActivityAction.ACTIVATE, success = true)

        assertThat(log.getEntriesAsFlow().first().map { it.action }).containsExactly(ActivityAction.ACTIVATE)
    }
}
