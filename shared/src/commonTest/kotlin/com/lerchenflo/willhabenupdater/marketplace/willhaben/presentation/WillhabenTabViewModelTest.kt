package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.domain.Marketplace
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatus
import com.lerchenflo.willhabenupdater.marketplace.domain.SyncState
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.PublishError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttribute
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttributeReference
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttributeValue
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategory
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.willhabenupdater.testutil.FakeImporter
import com.lerchenflo.willhabenupdater.testutil.FakeListingRepository
import com.lerchenflo.willhabenupdater.testutil.FakePublicationStatusRepository
import com.lerchenflo.willhabenupdater.testutil.FakePublisher
import com.lerchenflo.willhabenupdater.testutil.FakeTimeProvider
import com.lerchenflo.willhabenupdater.testutil.FakeWillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.testutil.FakeWillhabenFieldsDataSource
import com.lerchenflo.willhabenupdater.testutil.MainDispatcherTest
import com.lerchenflo.willhabenupdater.testutil.listing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WillhabenTabViewModelTest : MainDispatcherTest() {

    private fun ref(code: String, required: Boolean, multi: Boolean) = WillhabenAttributeReference(code, required, multi)
    private fun values(vararg pairs: Pair<String, String>) = pairs.map { WillhabenAttributeValue(it.first, it.second) }

    private val bicycles = WillhabenCategory(
        treeId = 4552, code = "BICYCLE", label = "Fahrräder", children = emptyList(),
        attributeReferences = listOf(
            ref("Zustand", true, false), ref("Uebergabe", true, true), ref("COLOR", true, true), ref("BRAND", false, false)
        ),
    )
    private val comics = WillhabenCategory(
        treeId = 6943, code = "COMICS_MANGAS", label = "Comics / Mangas", children = emptyList(),
        attributeReferences = listOf(ref("LANGUAGE", true, false), ref("Zustand", true, false)),
    )
    private val tree = WillhabenCategoryTree(
        root = WillhabenCategory(
            2, "Marktplatz", "Marktplatz",
            listOf(
                WillhabenCategory(4390, "SPORT_SPORTEQUIPMENT", "Sport / Sportgeräte", listOf(bicycles), emptyList()),
                WillhabenCategory(6941, "ANTIQUES_ART", "Antiquitäten / Kunst", listOf(comics), emptyList()),
            ),
            emptyList(),
        ),
        attributesByCode = mapOf(
            "COLOR" to WillhabenAttribute("COLOR", "Farbe", values("RED" to "Rot", "BLUE" to "Blau")),
            "BRAND" to WillhabenAttribute("BRAND", "Marke", values("PUKY" to "Puky", "WOOM" to "woom")),
            "LANGUAGE" to WillhabenAttribute("LANGUAGE", "Sprache", values("GERMAN" to "Deutsch")),
            "Zustand" to WillhabenAttribute("Zustand", "Zustand", values("neu" to "Neu")),
            "Uebergabe" to WillhabenAttribute("Uebergabe", "Übergabe", values("PICKUP" to "Selbstabholung")),
        ),
    )

    private val bicyclePath = listOf(
        WillhabenCategoryRef(4390, "SPORT_SPORTEQUIPMENT", "Sport / Sportgeräte"),
        WillhabenCategoryRef(4552, "BICYCLE", "Fahrräder"),
    )
    private val comicsPath = listOf(
        WillhabenCategoryRef(6941, "ANTIQUES_ART", "Antiquitäten / Kunst"),
        WillhabenCategoryRef(6943, "COMICS_MANGAS", "Comics / Mangas"),
    )

    private val catalog = FakeWillhabenCatalogRepository(tree = Result.Success(tree))
    private val fieldsSource = FakeWillhabenFieldsDataSource()
    private val listings = FakeListingRepository().apply {
        listings.value = listOf(listing(1, title = "Kinderfahrrad", postcode = ""))
    }

    private val statuses = FakePublicationStatusRepository()
    private val publisher = FakePublisher()
    private val importer = FakeImporter()
    private val time = FakeTimeProvider(now = 10_000L)

    private fun viewModel() = WillhabenTabViewModel(1, catalog, fieldsSource, listings, statuses, publisher, importer, time)

    private fun publishedAs(statusText: String?, expiresAt: Long? = null) {
        statuses.statuses.value = listOf(
            PublicationStatus(listingId = 1, marketplace = Marketplace.WILLHABEN, statusText = statusText, expiresAt = expiresAt, syncState = SyncState.SYNCED),
            PublicationStatus(listingId = 2, marketplace = Marketplace.WILLHABEN, statusText = "Online", expiresAt = null, syncState = SyncState.SYNCED),
        )
    }
    private fun savedFields(): WillhabenListingFields = fieldsSource.fields.value.getValue(1)
    private fun setPostcode(postcode: String) {
        listings.listings.value = listings.listings.value.map { it.copy(postcode = postcode) }
    }

    @Test
    fun treeErrorIsShownAndRetryLoadsAgain() = runTest {
        catalog.tree = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()
        assertThat(viewModel.state.value.treeError).isNotNull()

        catalog.tree = Result.Success(tree)
        viewModel.onAction(WillhabenTabAction.OnRetryLoadTreeClick)

        assertThat(viewModel.state.value.treeError).isNull()
        assertThat(viewModel.state.value.isTreeLoading).isFalse()
    }

    @Test
    fun savedCategoryShowsPathAndEditableAttributes() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = bicyclePath, attributes = mapOf("COLOR" to listOf("RED"))))

        val state = viewModel().state.value

        assertThat(state.categoryPathText).isEqualTo("Sport / Sportgeräte › Fahrräder")
        assertThat(state.attributeFields.map { it.code }).containsExactly("COLOR", "BRAND")
        val color = state.attributeFields.first()
        assertThat(color.label).isEqualTo("Farbe")
        assertThat(color.required).isTrue()
        assertThat(color.multiSelect).isTrue()
        assertThat(color.selectedCodes).containsExactly("RED")
        assertThat(color.options.map { it.code }).containsExactly("RED", "BLUE")
    }

    @Test
    fun categorySearchMatchesAnyLabelOfThePath() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        viewModel.onAction(WillhabenTabAction.OnCategoryQueryChange("sport"))

        assertThat(viewModel.state.value.categoryResults.map { it.treeId }).containsExactly(4552)
        assertThat(viewModel.state.value.categoryResults.single().pathText).isEqualTo("Sport / Sportgeräte › Fahrräder")
    }

    @Test
    fun emptyCategorySearchListsAllLeaves() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        assertThat(viewModel.state.value.isCategoryPickerOpen).isTrue()
        assertThat(viewModel.state.value.categoryResults.map { it.treeId }).containsExactly(4552, 6943)
    }

    @Test
    fun selectingCategorySavesPathDropsForeignAttributesAndClosesPicker() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = comicsPath, attributes = mapOf("LANGUAGE" to listOf("GERMAN"))))
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        viewModel.onAction(WillhabenTabAction.OnCategorySelect(4552))

        assertThat(savedFields().categoryPath).isEqualTo(bicyclePath)
        assertThat(savedFields().attributes).isEqualTo(emptyMap())
        assertThat(viewModel.state.value.isCategoryPickerOpen).isFalse()
    }

    @Test
    fun suggestionsForListingTitleSkipUnknownCategories() = runTest {
        catalog.suggestionsByTitle["Kinderfahrrad"] = listOf(4552, 999)
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnSuggestCategoriesClick)

        assertThat(viewModel.state.value.suggestions).containsExactly(CategoryOptionUi(4552, "Sport / Sportgeräte › Fahrräder"))
    }

    @Test
    fun clickingSuggestionAppliesCategory() = runTest {
        catalog.suggestionsByTitle["Kinderfahrrad"] = listOf(4552)
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnSuggestCategoriesClick)

        viewModel.onAction(WillhabenTabAction.OnCategorySelect(4552))

        assertThat(savedFields().categoryPath).isEqualTo(bicyclePath)
    }

    @Test
    fun singleSelectAttributeKeepsOneValueAndCanBeCleared() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = bicyclePath))
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("BRAND", "PUKY"))
        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("BRAND", "WOOM"))
        assertThat(savedFields().attributes.getValue("BRAND")).containsExactly("WOOM")

        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("BRAND", "WOOM"))
        assertThat(savedFields().attributes.containsKey("BRAND")).isFalse()
    }

    @Test
    fun multiSelectAttributeTogglesValues() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = bicyclePath))
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("COLOR", "RED"))
        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("COLOR", "BLUE"))
        viewModel.onAction(WillhabenTabAction.OnAttributeValueToggle("COLOR", "RED"))

        assertThat(savedFields().attributes.getValue("COLOR")).containsExactly("BLUE")
    }

    @Test
    fun singleLocationForPostcodeIsSelectedAutomatically() = runTest {
        catalog.locationsByPostcode["1010"] = listOf(WillhabenLocation(117223, "Wien, 01. Bezirk, Innere Stadt"))
        setPostcode("1010")

        val viewModel = viewModel()

        assertThat(savedFields().locationId).isEqualTo(117223)
        assertThat(savedFields().locationLabel).isEqualTo("Wien, 01. Bezirk, Innere Stadt")
        assertThat(viewModel.state.value.selectedLocationId).isEqualTo(117223)
    }

    @Test
    fun severalLocationsWaitForUserChoice() = runTest {
        catalog.locationsByPostcode["6850"] = listOf(WillhabenLocation(1, "Dornbirn"), WillhabenLocation(2, "Dornbirn, Haselstauden"))
        setPostcode("6850")
        val viewModel = viewModel()
        assertThat(viewModel.state.value.locations.size).isEqualTo(2)
        assertThat(viewModel.state.value.selectedLocationId).isNull()

        viewModel.onAction(WillhabenTabAction.OnLocationSelect(2))

        assertThat(savedFields().locationId).isEqualTo(2)
        assertThat(savedFields().locationLabel).isEqualTo("Dornbirn, Haselstauden")
    }

    @Test
    fun postcodeChangeClearsLocationOfOldPostcode() = runTest {
        catalog.locationsByPostcode["6850"] = listOf(WillhabenLocation(1, "Dornbirn"), WillhabenLocation(2, "Haselstauden"))
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(locationId = 117223, locationLabel = "Wien"))
        viewModel()

        setPostcode("6850")

        assertThat(savedFields().locationId).isNull()
    }

    @Test
    fun incompletePostcodeIsNotLookedUp() = runTest {
        setPostcode("68")

        viewModel()

        assertThat(catalog.locationRequests).isEmpty()
    }

    @Test
    fun deliveryOptionsAreSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnPickupChange(false))
        viewModel.onAction(WillhabenTabAction.OnShippingChange(true))
        viewModel.onAction(WillhabenTabAction.OnShippingPriceChange("5,90"))
        viewModel.onAction(WillhabenTabAction.OnPayliveryChange(true))

        assertThat(savedFields().delivery).isEqualTo(
            DeliveryOptions(pickup = false, shipping = true, shippingPriceCents = 590, paylivery = true)
        )
    }

    @Test
    fun invalidShippingPriceIsFlaggedAndNotSaved() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(delivery = DeliveryOptions(shipping = true, shippingPriceCents = 490)))
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnShippingPriceChange("abc"))

        assertThat(viewModel.state.value.isShippingPriceInvalid).isTrue()
        assertThat(savedFields().delivery.shippingPriceCents).isEqualTo(490L)
    }

    @Test
    fun listingWithoutWillhabenFieldsShowsDefaults() = runTest {
        val state = viewModel().state.value

        assertThat(state.pickup).isTrue()
        assertThat(state.shipping).isFalse()
        assertThat(state.categoryPathText).isNull()
    }

    @Test
    fun localOnlyListingShowsNoWillhabenListing() = runTest {
        assertThat(viewModel().state.value.publication).isNull()
    }

    @Test
    fun expiredListingShowsStatusAndCanBeRepublished() = runTest {
        publishedAs("Abgelaufen", expiresAt = 5_000L)

        val publication = viewModel().state.value.publication!!

        assertThat(publication.statusText).isEqualTo("Abgelaufen")
        assertThat(publication.expiresText).isNotNull()
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(publication.canRepublish).isTrue()
    }

    @Test
    fun runningListingCannotBeRepublished() = runTest {
        publishedAs("Online", expiresAt = 20_000L)

        assertThat(viewModel().state.value.publication!!.canRepublish).isFalse()
    }

    @Test
    fun passedEndDateCountsAsExpired() = runTest {
        publishedAs("Online", expiresAt = 9_000L)

        assertThat(viewModel().state.value.publication!!.canRepublish).isTrue()
    }

    @Test
    fun republishAsksForConfirmationFirst() = runTest {
        publishedAs("Abgelaufen")
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnRepublishClick)
        assertThat(viewModel.state.value.isRepublishConfirmOpen).isTrue()
        assertThat(publisher.republished).isEmpty()

        viewModel.onAction(WillhabenTabAction.OnRepublishDismiss)
        assertThat(viewModel.state.value.isRepublishConfirmOpen).isFalse()
        assertThat(publisher.republished).isEmpty()
    }

    @Test
    fun confirmedRepublishRenewsAndRefreshesStatus() = runTest {
        publishedAs("Abgelaufen")
        publisher.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnRepublishClick)
            viewModel.onAction(WillhabenTabAction.OnRepublishConfirm)
            assertThat(viewModel.state.value.isRepublishConfirmOpen).isFalse()
            assertThat(viewModel.state.value.isRepublishing).isTrue()

            publisher.pending!!.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(WillhabenTabEvent.ShowMessage::class)
            assertThat(publisher.republished).containsExactly(1L)
            assertThat(importer.calls).isEqualTo(1)
            assertThat(viewModel.state.value.isRepublishing).isFalse()
        }
    }

    @Test
    fun failedRepublishShowsErrorAndSkipsRefresh() = runTest {
        publishedAs("Abgelaufen")
        publisher.result = Result.Error(PublishError.PaidProduct)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnRepublishConfirm)

            assertThat(awaitItem()).isEqualTo(WillhabenTabEvent.ShowMessage(PublishError.PaidProduct.toUiText()))
            assertThat(importer.calls).isEqualTo(0)
            assertThat(viewModel.state.value.isRepublishing).isFalse()
        }
    }
}
