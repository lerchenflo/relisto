package com.lerchenflo.relisto.marketplace.willhaben.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.domain.canRenew
import com.lerchenflo.relisto.marketplace.domain.isExpired
import com.lerchenflo.relisto.testutil.FakeImporter
import com.lerchenflo.relisto.testutil.FakePublicationStatusRepository
import com.lerchenflo.relisto.testutil.FakePublisher
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WillhabenRenewerTest {

    private val publisher = FakePublisher()
    private val importer = FakeImporter()
    private val statuses = FakePublicationStatusRepository()
    private val time = FakeTimeProvider(now = 10_000L)
    private val renewer = WillhabenRenewer(publisher, importer, statuses, time)

    private fun status(listingId: Long, text: String?, expiresAt: Long? = null, isRenewDraft: Boolean = false) =
        PublicationStatus(listingId, Marketplace.WILLHABEN, text, expiresAt, SyncState.SYNCED, isRenewDraft = isRenewDraft)

    @Test
    fun expiredMeansExpiredStatusOrPassedEndDate() {
        assertThat(status(1, "Abgelaufen").isExpired(time.now)).isTrue()
        assertThat(status(1, "Online", expiresAt = 9_000L).isExpired(time.now)).isTrue()
        assertThat(status(1, "Online", expiresAt = 20_000L).isExpired(time.now)).isFalse()
        assertThat(status(1, null).isExpired(time.now)).isFalse()
    }

    @Test
    fun soldOrDeactivatedListingsAreNeverExpired() {
        // Their end date has passed too, but renewing them would put a sold item online again
        assertThat(status(1, "Verkauft", expiresAt = 9_000L).isExpired(time.now)).isFalse()
        assertThat(status(1, "Deaktiviert", expiresAt = 9_000L).isExpired(time.now)).isFalse()
        assertThat(status(1, "Reserviert", expiresAt = 9_000L).isExpired(time.now)).isFalse()
    }

    @Test
    fun expiredListingsAndDraftsOfARenewCanBeRenewed() {
        assertThat(status(1, "Abgelaufen").canRenew(time.now)).isTrue()
        // A draft a renew left behind (e.g. activation failed) is finished by the next renew
        assertThat(status(1, "Bearbeitung", isRenewDraft = true).canRenew(time.now)).isTrue()
        // Drafts the user started on willhaben are only finished on request, never in bulk
        assertThat(status(1, "Bearbeitung").canRenew(time.now)).isFalse()
        assertThat(status(1, "Bearbeitung").isDraft).isTrue()
        assertThat(status(1, "Deaktiviert", expiresAt = 9_000L).canRenew(time.now)).isFalse()
    }

    @Test
    fun renewAllExpiredAlsoFinishesDraftsOfARenew() = runTest {
        statuses.statuses.value = listOf(status(1, "Bearbeitung", isRenewDraft = true), status(2, "Bearbeitung"))

        renewer.renewAllExpired()

        assertThat(publisher.republished).containsExactly(1L)
    }

    @Test
    fun renewAllExpiredSkipsSoldListings() = runTest {
        statuses.statuses.value = listOf(status(1, "Abgelaufen"), status(2, "Verkauft", expiresAt = 5_000L))

        renewer.renewAllExpired()

        assertThat(publisher.republished).containsExactly(1L)
    }

    @Test
    fun listingsAreRenewedOneAfterAnotherThenRefreshedOnce() = runTest {
        val summary = renewer.renew(listOf(3L, 1L))

        assertThat(publisher.republished).containsExactly(3L, 1L)
        assertThat(importer.calls).isEqualTo(1)
        assertThat(summary).isEqualTo(RenewSummary(renewed = 2, failed = 0, firstError = null))
    }

    @Test
    fun failedListingDoesNotStopTheOthers() = runTest {
        publisher.result = Result.Error(PublishError.PaidProduct())

        val summary = renewer.renew(listOf(1L, 2L))

        assertThat(publisher.republished).containsExactly(1L, 2L)
        assertThat(summary).isEqualTo(RenewSummary(renewed = 0, failed = 2, firstError = PublishError.PaidProduct()))
        assertThat(importer.calls).isEqualTo(0)
    }

    @Test
    fun renewAllExpiredRefreshesFirstAndRenewsOnlyExpiredListings() = runTest {
        statuses.statuses.value = listOf(
            status(1, "Abgelaufen"),
            status(2, "Online", expiresAt = 20_000L),
            status(3, "Online", expiresAt = 5_000L),
        )

        val result = renewer.renewAllExpired()

        assertThat(publisher.republished).containsExactly(1L, 3L)
        assertThat(importer.calls).isEqualTo(2)
        assertThat(result).isEqualTo(Result.Success(RenewSummary(renewed = 2, failed = 0, firstError = null)))
    }

    @Test
    fun renewAllExpiredStopsWhenRefreshFails() = runTest {
        importer.result = Result.Error(DataError.Network.UNAUTHORIZED)
        statuses.statuses.value = listOf(status(1, "Abgelaufen"))

        val result = renewer.renewAllExpired()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
        assertThat(publisher.republished).isEmpty()
    }
}
