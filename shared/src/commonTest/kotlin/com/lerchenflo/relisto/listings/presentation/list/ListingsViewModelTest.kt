package com.lerchenflo.relisto.listings.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.navigation.NavigationAction
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.app.navigation.Route
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenRenewer
import com.lerchenflo.relisto.marketplace.willhaben.presentation.toUiText
import com.lerchenflo.relisto.testutil.FakeImporter
import com.lerchenflo.relisto.testutil.FakeListingRepository
import com.lerchenflo.relisto.testutil.FakePublicationStatusRepository
import com.lerchenflo.relisto.testutil.FakePublisher
import com.lerchenflo.relisto.testutil.FakeStatusActions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.testutil.FakeSettingsDataSource
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.FakeWillhabenFieldsDataSource
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import com.lerchenflo.relisto.testutil.image
import com.lerchenflo.relisto.testutil.listing
import com.lerchenflo.relisto.app.domain.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ListingsViewModelTest : MainDispatcherTest() {

    private val repository = FakeListingRepository()
    private val settings = FakeSettingsDataSource()
    private val willhabenFields = FakeWillhabenFieldsDataSource()
    private val navigator = Navigator()
    private val statuses = FakePublicationStatusRepository()

    private val publisher = FakePublisher()
    private val importer = FakeImporter()
    private val time = FakeTimeProvider(now = 10_000L)
    private val renewer = WillhabenRenewer(publisher, importer, statuses, time)

    private val statusActions = FakeStatusActions()

    private fun viewModel() = ListingsViewModel(repository, settings, willhabenFields, navigator, statuses, renewer, statusActions, time)

    /** Listings 1 and 3 are expired on willhaben, 2 is online, 4 exists only locally. */
    private fun expiredAndOnlineListings() {
        repository.listings.value = listOf(listing(1, updatedAt = 4), listing(2, updatedAt = 3), listing(3, updatedAt = 2), listing(4, updatedAt = 1))
        statuses.statuses.value = listOf(
            PublicationStatus(1, Marketplace.WILLHABEN, "Abgelaufen", null, SyncState.SYNCED),
            PublicationStatus(2, Marketplace.WILLHABEN, "Online", 20_000L, SyncState.SYNCED),
            PublicationStatus(3, Marketplace.WILLHABEN, "Online", 5_000L, SyncState.SYNCED),
        )
    }

    @Test
    fun activeListingsAreShownNewestFirstWithFormattedPriceAndCover() = runTest {
        repository.listings.value = listOf(
            listing(1, title = "Rad", priceCents = 4500, images = listOf(image(10, 1, 0), image(11, 1, 1)), updatedAt = 1),
            listing(2, title = "Tisch", updatedAt = 2),
            listing(3, title = "Alt", archived = true, updatedAt = 3),
        )

        val items = viewModel().state.value.listings

        assertThat(items.map { it.id }).containsExactly(2L, 1L)
        val rad = items.last()
        assertThat(rad.formattedPrice).isEqualTo("45 €")
        assertThat(rad.coverImagePath).isEqualTo("/images/10.jpg")
        assertThat(rad.imageCount).isEqualTo(2)
        assertThat(items.first().formattedPrice).isNull()
    }

    @Test
    fun archivedFilterShowsOnlyArchivedListings() = runTest {
        repository.listings.value = listOf(listing(1), listing(2, archived = true))
        val viewModel = viewModel()

        viewModel.onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED))

        assertThat(viewModel.state.value.listings.map { it.id }).containsExactly(2L)
    }

    @Test
    fun searchMatchesTitleOrDescriptionIgnoringCase() = runTest {
        repository.listings.value = listOf(
            listing(1, title = "Kinderfahrrad"),
            listing(2, title = "Tisch", description = "Passt zum FAHRRAD-Ständer"),
            listing(3, title = "Lampe"),
        )
        val viewModel = viewModel()

        viewModel.onAction(ListingsAction.OnSearchQueryChange("fahrrad"))

        assertThat(viewModel.state.value.listings.map { it.id }.sorted()).containsExactly(1L, 2L)
    }

    @Test
    fun clickingListingSelectsIt() = runTest {
        repository.listings.value = listOf(listing(1), listing(2))
        val viewModel = viewModel()

        viewModel.onAction(ListingsAction.OnListingClick(2))

        assertThat(viewModel.state.value.selectedListingId).isEqualTo(2L)
    }

    @Test
    fun closingTheEditorGoesBackToTheList() = runTest {
        // Phones show the editor as its own screen
        repository.listings.value = listOf(listing(1))
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingClick(1))

        viewModel.onAction(ListingsAction.OnCloseListingClick)

        assertThat(viewModel.state.value.selectedListingId).isNull()
    }

    @Test
    fun selectionIsClearedWhenListingIsDeleted() = runTest {
        repository.listings.value = listOf(listing(1), listing(2))
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingClick(2))

        repository.listings.value = listOf(listing(1))

        assertThat(viewModel.state.value.selectedListingId).isNull()
    }

    @Test
    fun newListingUsesDefaultsFromSettingsAndIsSelected() = runTest {
        settings.settings.value = AppSettings(
            defaultPostcode = "6850",
            defaultCondition = Condition.USED,
            defaultPickup = true,
            defaultShipping = true,
            defaultShippingPriceCents = 590,
        )
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED))
        viewModel.onAction(ListingsAction.OnSearchQueryChange("xyz"))

        viewModel.onAction(ListingsAction.OnNewListingClick)

        val created = repository.listings.value.single()
        assertThat(created.postcode).isEqualTo("6850")
        assertThat(created.condition).isEqualTo(Condition.USED)
        assertThat(willhabenFields.fields.value.getValue(created.id).delivery).isEqualTo(
            DeliveryOptions(pickup = true, shipping = true, shippingPriceCents = 590, paylivery = false)
        )
        val state = viewModel.state.value
        assertThat(state.selectedListingId).isEqualTo(created.id)
        assertThat(state.filter).isEqualTo(ListingFilter.ACTIVE)
        assertThat(state.searchQuery).isEqualTo("")
    }

    @Test
    fun failedCreationShowsMessage() = runTest {
        repository.createError = DataError.Local.DISK_FULL
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ListingsAction.OnNewListingClick)
            assertThat(awaitItem()).isInstanceOf(ListingsEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.selectedListingId).isNull()
    }

    @Test
    fun settingsClickNavigatesToSettings() = runTest {
        val viewModel = viewModel()

        navigator.navigationActions.test {
            viewModel.onAction(ListingsAction.OnSettingsClick)
            assertThat(awaitItem()).isEqualTo(NavigationAction.Navigate(Route.Settings))
        }
    }

    @Test
    fun unusedImageFilesAreCleanedUpOnStart() = runTest {
        viewModel()

        assertThat(repository.cleanupCalls).isEqualTo(1)
    }

    @Test
    fun loadingEndsAfterFirstListEmission() = runTest {
        assertThat(viewModel().state.value.isLoading).isFalse()
    }

    @Test
    fun willhabenStatusIsShownForPublishedListings() = runTest {
        repository.listings.value = listOf(listing(1), listing(2))
        statuses.statuses.value = listOf(
            PublicationStatus(1, Marketplace.WILLHABEN, "Abgelaufen", expiresAt = 5L, syncState = SyncState.SYNCED),
            PublicationStatus(2, Marketplace.WILLHABEN, statusText = null, expiresAt = null, syncState = SyncState.LOCAL_ONLY),
        )

        val items = viewModel().state.value.listings.associateBy { it.id }

        assertThat(items.getValue(1).willhabenStatus).isEqualTo("Abgelaufen")
        assertThat(items.getValue(2).willhabenStatus).isNull()
    }

    @Test
    fun willhabenStatusBecomesAStateForTheIcon() = runTest {
        // "Inaktiv" is what willhaben shows for a deactivated listing
        val texts = listOf("Online", "Abgelaufen", "Bearbeitung", "in Warteschlange", "Verkauft", "Inaktiv", "Reserviert", "Neu")
        repository.listings.value = texts.indices.map { listing(it + 1L) }
        statuses.statuses.value = texts.mapIndexed { index, text ->
            PublicationStatus(index + 1L, Marketplace.WILLHABEN, text, expiresAt = 20_000L, syncState = SyncState.SYNCED)
        } + PublicationStatus(9, Marketplace.WILLHABEN, "Online", expiresAt = 5_000L, syncState = SyncState.SYNCED)
        repository.listings.value = repository.listings.value + listing(9)

        val states = viewModel().state.value.listings.associate { it.id to it.willhabenState }

        assertThat(states).isEqualTo(
            mapOf(
                1L to WillhabenStateUi.ONLINE,
                2L to WillhabenStateUi.EXPIRED,
                3L to WillhabenStateUi.DRAFT,
                4L to WillhabenStateUi.IN_REVIEW,
                5L to WillhabenStateUi.SOLD,
                6L to WillhabenStateUi.DEACTIVATED,
                7L to WillhabenStateUi.RESERVED,
                8L to WillhabenStateUi.OTHER,
                // End date passed since the last import
                9L to WillhabenStateUi.EXPIRED,
            )
        )
    }

    @Test
    fun listingGoneOnWillhabenShowsThat() = runTest {
        repository.listings.value = listOf(listing(1))
        statuses.statuses.value = listOf(PublicationStatus(1, Marketplace.WILLHABEN, null, null, SyncState.REMOTE_GONE))

        val item = viewModel().state.value.listings.single()

        assertThat(item.willhabenState).isEqualTo(WillhabenStateUi.GONE)
        assertThat(item.canRenew).isFalse()
    }

    @Test
    fun localOnlyListingHasNoWillhabenState() = runTest {
        repository.listings.value = listOf(listing(1))

        assertThat(viewModel().state.value.listings.single().willhabenState).isNull()
    }

    @Test
    fun onlyExpiredWillhabenListingsCanBeRenewed() = runTest {
        expiredAndOnlineListings()

        val items = viewModel().state.value.listings.associateBy { it.id }

        assertThat(items.getValue(1).canRenew).isTrue()
        assertThat(items.getValue(2).canRenew).isFalse()
        assertThat(items.getValue(3).canRenew).isTrue()
        assertThat(items.getValue(4).canRenew).isFalse()
    }

    @Test
    fun anyListingCanBeCheckedAndUnchecked() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()

        viewModel.onAction(ListingsAction.OnListingCheckedChange(1, true))
        viewModel.onAction(ListingsAction.OnListingCheckedChange(2, true))
        viewModel.onAction(ListingsAction.OnListingCheckedChange(4, true))
        assertThat(viewModel.state.value.checkedListingIds).isEqualTo(setOf(1L, 2L, 4L))
        assertThat(viewModel.state.value.checkedRenewableCount).isEqualTo(1)
        assertThat(viewModel.state.value.listings.first { it.id == 2L }.isChecked).isTrue()

        viewModel.onAction(ListingsAction.OnListingCheckedChange(1, false))
        assertThat(viewModel.state.value.checkedListingIds).isEqualTo(setOf(2L, 4L))
    }

    @Test
    fun selectAllVisibleChecksOnlyTheShownListings() = runTest {
        repository.listings.value = listOf(listing(1, title = "Rad"), listing(2, title = "Tisch"), listing(3, title = "Radhelm"))
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnSearchQueryChange("rad"))

        viewModel.onAction(ListingsAction.OnSelectAllVisibleClick)

        assertThat(viewModel.state.value.checkedListingIds).isEqualTo(setOf(1L, 3L))
    }

    @Test
    fun switchingBetweenActiveAndArchivedClearsTheSelection() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(2, true))

        viewModel.onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED))

        assertThat(viewModel.state.value.checkedListingIds).isEmpty()
    }

    @Test
    fun renewingTheSelectionRenewsOnlyTheExpiredListings() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()
        listOf(1L, 2L, 3L, 4L).forEach { viewModel.onAction(ListingsAction.OnListingCheckedChange(it, true)) }

        viewModel.onAction(ListingsAction.OnRenewCheckedClick)

        assertThat(publisher.republished).containsExactly(1L, 3L)
    }

    @Test
    fun checkedListingsAreArchivedAndRestoredTogether() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(1, true))
        viewModel.onAction(ListingsAction.OnListingCheckedChange(4, true))

        viewModel.onAction(ListingsAction.OnArchiveCheckedClick)

        assertThat(repository.listings.value.filter { it.archived }.map { it.id }).containsExactly(1L, 4L)
        assertThat(viewModel.state.value.checkedListingIds).isEmpty()

        viewModel.onAction(ListingsAction.OnFilterChange(ListingFilter.ARCHIVED))
        viewModel.onAction(ListingsAction.OnSelectAllVisibleClick)
        viewModel.onAction(ListingsAction.OnUnarchiveCheckedClick)

        assertThat(repository.listings.value.none { it.archived }).isTrue()
    }

    @Test
    fun checkedListingsAreDeletedLocallyAfterOneConfirmation() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(2, true))
        viewModel.onAction(ListingsAction.OnListingCheckedChange(4, true))

        viewModel.onAction(ListingsAction.OnDeleteCheckedClick)
        assertThat(viewModel.state.value.isDeleteConfirmOpen).isTrue()
        assertThat(repository.listings.value.size).isEqualTo(4)

        viewModel.onAction(ListingsAction.OnDeleteCheckedConfirm)

        assertThat(repository.listings.value.map { it.id }).containsExactly(1L, 3L)
        assertThat(viewModel.state.value.isDeleteConfirmOpen).isFalse()
        assertThat(viewModel.state.value.checkedListingIds).isEmpty()
    }

    /** Listing 1 and 2 are on willhaben and can be deleted there, 3 only locally. */
    private fun deletableOnWillhaben() {
        repository.listings.value = listOf(listing(1), listing(2), listing(3))
        statuses.statuses.value = listOf(
            PublicationStatus(1, Marketplace.WILLHABEN, "Online", null, SyncState.SYNCED, availableActions = setOf("DELETE")),
            PublicationStatus(2, Marketplace.WILLHABEN, "Abgelaufen", null, SyncState.SYNCED, availableActions = setOf("DELETE")),
        )
    }

    @Test
    fun deleteDialogOffersWillhabenOnlyForListingsThatAreThere() = runTest {
        deletableOnWillhaben()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(3, true))
        viewModel.onAction(ListingsAction.OnDeleteCheckedClick)
        assertThat(viewModel.state.value.canDeleteCheckedOnWillhaben).isFalse()

        viewModel.onAction(ListingsAction.OnListingCheckedChange(1, true))

        assertThat(viewModel.state.value.canDeleteCheckedOnWillhaben).isTrue()
    }

    @Test
    fun deleteEverywhereDeletesOnWillhabenThenLocally() = runTest {
        deletableOnWillhaben()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnSelectAllVisibleClick)
        viewModel.onAction(ListingsAction.OnDeleteCheckedClick)

        viewModel.onAction(ListingsAction.OnDeleteCheckedEverywhereConfirm)

        assertThat(statusActions.performed).containsOnly(1L to WillhabenStatusAction.DELETE, 2L to WillhabenStatusAction.DELETE)
        assertThat(repository.listings.value).isEmpty()
    }

    @Test
    fun listingThatCouldNotBeDeletedOnWillhabenStaysLocally() = runTest {
        deletableOnWillhaben()
        statusActions.failingIds = setOf(2L)
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnSelectAllVisibleClick)
        viewModel.onAction(ListingsAction.OnDeleteCheckedClick)

        viewModel.onAction(ListingsAction.OnDeleteCheckedEverywhereConfirm)

        assertThat(repository.listings.value.map { it.id }).containsExactly(2L)
    }

    @Test
    fun dismissedDeleteKeepsEverything() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(2, true))
        viewModel.onAction(ListingsAction.OnDeleteCheckedClick)

        viewModel.onAction(ListingsAction.OnDeleteCheckedDismiss)

        assertThat(repository.listings.value.size).isEqualTo(4)
        assertThat(viewModel.state.value.isDeleteConfirmOpen).isFalse()
        assertThat(viewModel.state.value.checkedListingIds).isEqualTo(setOf(2L))
    }

    @Test
    fun selectAllExpiredChecksEveryRenewableListing() = runTest {
        expiredAndOnlineListings()
        val viewModel = viewModel()

        viewModel.onAction(ListingsAction.OnSelectAllExpiredClick)

        assertThat(viewModel.state.value.checkedListingIds).isEqualTo(setOf(1L, 3L))
        assertThat(viewModel.state.value.renewableCount).isEqualTo(2)
    }

    @Test
    fun checkedListingsAreRenewedTogether() = runTest {
        expiredAndOnlineListings()
        publisher.pending = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnSelectAllExpiredClick)

        viewModel.events.test {
            viewModel.onAction(ListingsAction.OnRenewCheckedClick)
            assertThat(viewModel.state.value.isRenewing).isTrue()

            publisher.pending!!.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(ListingsEvent.ShowMessage::class)
            assertThat(publisher.republished).containsExactly(1L, 3L)
            assertThat(importer.calls).isEqualTo(1)
            assertThat(viewModel.state.value.isRenewing).isFalse()
            assertThat(viewModel.state.value.checkedListingIds).isEmpty()
        }
    }

    @Test
    fun failedRenewalShowsSummaryAndFirstError() = runTest {
        expiredAndOnlineListings()
        publisher.result = Result.Error(PublishError.PaidProduct())
        val viewModel = viewModel()
        viewModel.onAction(ListingsAction.OnListingCheckedChange(1, true))

        viewModel.events.test {
            viewModel.onAction(ListingsAction.OnRenewCheckedClick)

            assertThat(awaitItem()).isInstanceOf(ListingsEvent.ShowMessage::class)
            assertThat(awaitItem()).isEqualTo(ListingsEvent.ShowMessage(PublishError.PaidProduct().toUiText()))
        }
    }
}
