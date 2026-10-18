package com.lerchenflo.willhabenupdater.listings.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.navigation.NavigationAction
import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.app.navigation.Route
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.marketplace.domain.Marketplace
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatus
import com.lerchenflo.willhabenupdater.marketplace.domain.SyncState
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.willhabenupdater.settings.domain.AppSettings
import com.lerchenflo.willhabenupdater.testutil.FakeListingRepository
import com.lerchenflo.willhabenupdater.testutil.FakePublicationStatusRepository
import com.lerchenflo.willhabenupdater.testutil.FakeSettingsDataSource
import com.lerchenflo.willhabenupdater.testutil.FakeWillhabenFieldsDataSource
import com.lerchenflo.willhabenupdater.testutil.MainDispatcherTest
import com.lerchenflo.willhabenupdater.testutil.image
import com.lerchenflo.willhabenupdater.testutil.listing
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ListingsViewModelTest : MainDispatcherTest() {

    private val repository = FakeListingRepository()
    private val settings = FakeSettingsDataSource()
    private val willhabenFields = FakeWillhabenFieldsDataSource()
    private val navigator = Navigator()
    private val statuses = FakePublicationStatusRepository()

    private fun viewModel() = ListingsViewModel(repository, settings, willhabenFields, navigator, statuses)

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
}
