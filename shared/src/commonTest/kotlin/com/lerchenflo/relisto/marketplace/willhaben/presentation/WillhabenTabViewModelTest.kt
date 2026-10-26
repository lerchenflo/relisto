package com.lerchenflo.relisto.marketplace.willhaben.presentation

import com.lerchenflo.relisto.utilities.toUiText
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
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttribute
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeReference
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenAttributeValue
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategory
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenVertical
import com.lerchenflo.relisto.testutil.FakeImporter
import com.lerchenflo.relisto.testutil.FakeListingRepository
import com.lerchenflo.relisto.testutil.FakePublicationStatusRepository
import com.lerchenflo.relisto.testutil.FakePublisher
import com.lerchenflo.relisto.testutil.FakeStatusActions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.FakeUpdater
import com.lerchenflo.relisto.testutil.FakeCreator
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.testutil.FakeWillhabenCatalogRepository
import com.lerchenflo.relisto.testutil.FakeWillhabenFieldsDataSource
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import com.lerchenflo.relisto.testutil.listing
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
    private fun leaf(treeId: Int, label: String) = WillhabenCategory(treeId, "C$treeId", label, emptyList(), emptyList())
    private val winterSports = WillhabenCategory(
        4400, "WINTER_SPORTS", "Wintersport",
        listOf(leaf(4401, "Ski"), leaf(4402, "Skischuhe"), leaf(4404, "Wintersportbekleidung")),
        emptyList(),
    )
    private val tree = WillhabenCategoryTree(
        root = WillhabenCategory(
            2, "Marktplatz", "Marktplatz",
            listOf(
                WillhabenCategory(4390, "SPORT_SPORTEQUIPMENT", "Sport / Sportgeräte", listOf(bicycles, winterSports), emptyList()),
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

    private val statusActions = FakeStatusActions()

    private val updater = FakeUpdater()
    private val creator = FakeCreator()

    private fun viewModel() = WillhabenTabViewModel(1, catalog, fieldsSource, listings, statuses, publisher, updater, creator, importer, statusActions, Navigator(), time)

    private fun publishedAs(statusText: String?, expiresAt: Long? = null, syncState: SyncState = SyncState.SYNCED) {
        statuses.statuses.value = listOf(
            PublicationStatus(listingId = 1, marketplace = Marketplace.WILLHABEN, statusText = statusText, expiresAt = expiresAt, syncState = syncState),
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
    fun marketplaceListingShowsMarketplaceSections() = runTest {
        assertThat(viewModel().state.value.vertical).isEqualTo(WillhabenVertical.MARKETPLACE)
    }

    @Test
    fun motorListingIsMarkedAsAutoAndMotor() = runTest {
        // Category, details and delivery only exist for marketplace listings
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(adTypeId = 21))

        assertThat(viewModel().state.value.vertical).isEqualTo(WillhabenVertical.MOTOR)
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
    fun categorySearchMatchesAnyLabelOfThePathAndRanksOwnLabelFirst() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        viewModel.onAction(WillhabenTabAction.OnCategoryQueryChange("sport"))

        // Own label contains the word first, then matches in a parent category, each sorted by path
        assertThat(viewModel.state.value.categoryResults.map { it.treeId }).containsExactly(4404, 4552, 4401, 4402)
        assertThat(viewModel.state.value.categoryResults[1].pathText).isEqualTo("Sport / Sportgeräte › Fahrräder")
    }

    @Test
    fun exactAndStartingLabelsComeFirstInTheSearch() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        viewModel.onAction(WillhabenTabAction.OnCategoryQueryChange("ski"))

        assertThat(viewModel.state.value.categoryResults.map { it.treeId }).containsExactly(4401, 4402)
    }

    @Test
    fun pickerOpensWithTheTopLevelCategories() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        val state = viewModel.state.value
        assertThat(state.isCategoryPickerOpen).isTrue()
        assertThat(state.categoryBreadcrumb).isEmpty()
        assertThat(state.categoryLevel).containsExactly(
            CategoryNodeUi(4390, "Sport / Sportgeräte", hasChildren = true),
            CategoryNodeUi(6941, "Antiquitäten / Kunst", hasChildren = true),
        )
    }

    @Test
    fun browsingIntoCategoriesShowsTheirChildrenStepByStep() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        viewModel.onAction(WillhabenTabAction.OnCategoryBrowse(4390))
        assertThat(viewModel.state.value.categoryLevel).containsExactly(
            CategoryNodeUi(4552, "Fahrräder", hasChildren = false),
            CategoryNodeUi(4400, "Wintersport", hasChildren = true),
        )

        viewModel.onAction(WillhabenTabAction.OnCategoryBrowse(4400))
        val state = viewModel.state.value
        assertThat(state.categoryBreadcrumb).containsExactly(
            CategoryNodeUi(4390, "Sport / Sportgeräte", hasChildren = true),
            CategoryNodeUi(4400, "Wintersport", hasChildren = true),
        )
        assertThat(state.categoryLevel.map { it.treeId }).containsExactly(4401, 4402, 4404)
    }

    @Test
    fun breadcrumbGoesBackUp() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)
        viewModel.onAction(WillhabenTabAction.OnCategoryBrowse(4390))
        viewModel.onAction(WillhabenTabAction.OnCategoryBrowse(4400))

        viewModel.onAction(WillhabenTabAction.OnCategoryBreadcrumbClick(4390))
        assertThat(viewModel.state.value.categoryLevel.map { it.treeId }).containsExactly(4552, 4400)
        assertThat(viewModel.state.value.categoryBreadcrumb.map { it.treeId }).containsExactly(4390)

        viewModel.onAction(WillhabenTabAction.OnCategoryBreadcrumbClick(null))
        assertThat(viewModel.state.value.categoryLevel.map { it.treeId }).containsExactly(4390, 6941)
        assertThat(viewModel.state.value.categoryBreadcrumb).isEmpty()
    }

    @Test
    fun pickerOpensNextToTheChosenCategory() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = bicyclePath))
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnOpenCategoryPickerClick)

        assertThat(viewModel.state.value.categoryBreadcrumb.map { it.treeId }).containsExactly(4390)
        assertThat(viewModel.state.value.categoryLevel.map { it.treeId }).containsExactly(4552, 4400)
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
        assertThat(publication.renewAction).isEqualTo(RenewAction.REPUBLISH)
    }

    @Test
    fun runningListingCannotBeRepublished() = runTest {
        publishedAs("Online", expiresAt = 20_000L)

        assertThat(viewModel().state.value.publication!!.renewAction).isNull()
    }

    private fun publishedWithActions(statusText: String, vararg actions: String) {
        statuses.statuses.value = listOf(
            PublicationStatus(1, Marketplace.WILLHABEN, statusText, null, SyncState.SYNCED, availableActions = actions.toSet()),
        )
    }

    @Test
    fun offeredStatusActionsAreShown() = runTest {
        publishedWithActions("Inaktiv", "ACTIVATE", "DELETE")

        assertThat(viewModel().state.value.statusActions).containsExactly(WillhabenStatusAction.ACTIVATE, WillhabenStatusAction.DELETE)
    }

    @Test
    fun reversibleActionRunsAtOnce() = runTest {
        publishedWithActions("Inaktiv", "ACTIVATE")
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnStatusActionClick(WillhabenStatusAction.ACTIVATE))

        assertThat(statusActions.performed).containsExactly(1L to WillhabenStatusAction.ACTIVATE)
    }

    @Test
    fun soldAndDeleteAskFirst() = runTest {
        publishedWithActions("Online", "SOLD", "DELETE")
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnStatusActionClick(WillhabenStatusAction.DELETE))
        assertThat(viewModel.state.value.confirmStatusAction).isEqualTo(WillhabenStatusAction.DELETE)
        assertThat(statusActions.performed).isEmpty()

        viewModel.onAction(WillhabenTabAction.OnStatusActionConfirm)

        assertThat(statusActions.performed).containsExactly(1L to WillhabenStatusAction.DELETE)
        assertThat(viewModel.state.value.confirmStatusAction).isNull()
    }

    @Test
    fun dismissedConfirmationSendsNothing() = runTest {
        publishedWithActions("Online", "SOLD")
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnStatusActionClick(WillhabenStatusAction.SOLD))

        viewModel.onAction(WillhabenTabAction.OnStatusActionDismiss)

        assertThat(statusActions.performed).isEmpty()
        assertThat(viewModel.state.value.confirmStatusAction).isNull()
    }

    @Test
    fun draftCanBeFinished() = runTest {
        // "Bearbeitung" on willhaben: continue the draft instead of copying
        publishedAs("Bearbeitung")

        assertThat(viewModel().state.value.publication!!.renewAction).isEqualTo(RenewAction.FINISH_DRAFT)
    }

    @Test
    fun deactivatedListingIsNotRenewed() = runTest {
        // Deactivated listings are activated again, not renewed (status actions, later)
        publishedAs("Deaktiviert", expiresAt = 5_000L)

        assertThat(viewModel().state.value.publication!!.renewAction).isNull()
    }

    @Test
    fun passedEndDateCountsAsExpired() = runTest {
        publishedAs("Online", expiresAt = 9_000L)

        assertThat(viewModel().state.value.publication!!.renewAction).isEqualTo(RenewAction.REPUBLISH)
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
        publisher.result = Result.Error(PublishError.PaidProduct())
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnRepublishConfirm)

            assertThat(awaitItem()).isEqualTo(WillhabenTabEvent.ShowMessage(PublishError.PaidProduct().toUiText()))
            assertThat(importer.calls).isEqualTo(0)
            assertThat(viewModel.state.value.isRepublishing).isFalse()
        }
    }

    @Test
    fun runningListingCanBeOpenedOnWillhaben() = runTest {
        val url = "https://www.willhaben.at/iad/object?adId=7"
        statuses.statuses.value = listOf(PublicationStatus(1, Marketplace.WILLHABEN, "Aktiv", null, SyncState.SYNCED, remoteUrl = url))
        assertThat(viewModel().state.value.publication!!.url).isEqualTo(url)

        // Drafts are not public, gone listings do not exist anymore
        statuses.statuses.value = listOf(PublicationStatus(1, Marketplace.WILLHABEN, "Bearbeitung", null, SyncState.SYNCED, remoteUrl = url))
        assertThat(viewModel().state.value.publication!!.url).isNull()
        statuses.statuses.value = listOf(PublicationStatus(1, Marketplace.WILLHABEN, "Aktiv", null, SyncState.REMOTE_GONE, remoteUrl = url))
        assertThat(viewModel().state.value.publication?.url).isNull()
    }

    @Test
    fun onlyLocalChangesOfARunningListingCanBePushed() = runTest {
        publishedAs("Aktiv", syncState = SyncState.LOCAL_CHANGES)
        assertThat(viewModel().state.value.publication!!.canPush).isTrue()

        publishedAs("Aktiv", syncState = SyncState.SYNCED)
        assertThat(viewModel().state.value.publication!!.canPush).isFalse()

        // Expired listings get their content with the renew
        publishedAs("Abgelaufen", syncState = SyncState.LOCAL_CHANGES)
        assertThat(viewModel().state.value.publication!!.canPush).isFalse()
    }

    @Test
    fun pushSendsTheChangesAndReportsTheResult() = runTest {
        publishedAs("Aktiv", syncState = SyncState.LOCAL_CHANGES)
        updater.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnPushChangesClick)
            assertThat(viewModel.state.value.isPushing).isTrue()

            updater.pending!!.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(WillhabenTabEvent.ShowMessage::class)
            assertThat(updater.pushed).containsExactly(1L to false)
            assertThat(viewModel.state.value.isPushing).isFalse()
        }
    }

    @Test
    fun pushIntoAConflictOpensTheComparison() = runTest {
        publishedAs("Aktiv", syncState = SyncState.LOCAL_CHANGES)
        updater.result = Result.Error(PublishError.Conflict)
        updater.remote = ListingContent(title = "Kinderrad", description = "Von willhaben", priceCents = 5000)
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnPushChangesClick)

        val conflict = viewModel.state.value.conflict!!
        assertThat(conflict.local.title).isEqualTo("Kinderfahrrad")
        assertThat(conflict.remote.title).isEqualTo("Kinderrad")
        assertThat(conflict.remote.priceText).isEqualTo("50 €")
    }

    @Test
    fun conflictFromTheImportCanBeResolved() = runTest {
        publishedAs("Aktiv", syncState = SyncState.CONFLICT)
        updater.remote = ListingContent(title = "Kinderrad")
        val viewModel = viewModel()
        assertThat(viewModel.state.value.publication!!.hasConflict).isTrue()

        viewModel.onAction(WillhabenTabAction.OnResolveConflictClick)

        assertThat(viewModel.state.value.conflict!!.remote.title).isEqualTo("Kinderrad")
    }

    @Test
    fun keepingTheLocalVersionOverwritesWillhaben() = runTest {
        publishedAs("Aktiv", syncState = SyncState.CONFLICT)
        updater.remote = ListingContent(title = "Kinderrad")
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnResolveConflictClick)

        viewModel.onAction(WillhabenTabAction.OnKeepLocalClick)

        assertThat(updater.pushed).containsExactly(1L to true)
        assertThat(viewModel.state.value.conflict).isNull()
    }

    @Test
    fun takingWillhabensVersionReplacesTheLocalOne() = runTest {
        publishedAs("Aktiv", syncState = SyncState.CONFLICT)
        updater.remote = ListingContent(title = "Kinderrad")
        val viewModel = viewModel()
        viewModel.onAction(WillhabenTabAction.OnResolveConflictClick)

        viewModel.onAction(WillhabenTabAction.OnTakeRemoteClick)

        assertThat(updater.takenRemote).containsExactly(1L)
        assertThat(updater.pushed).isEmpty()
        assertThat(viewModel.state.value.conflict).isNull()
    }

    @Test
    fun listingOnlyHereCanBePublished() = runTest {
        assertThat(viewModel().state.value.publishState).isEqualTo(PublishState.NEW)

        publishedAs("Aktiv")
        assertThat(viewModel().state.value.publishState).isNull()
    }

    @Test
    fun interruptedPublishIsContinued() = runTest {
        statuses.statuses.value = listOf(PublicationStatus(1, Marketplace.WILLHABEN, null, null, SyncState.PUBLISHING))

        assertThat(viewModel().state.value.publishState).isEqualTo(PublishState.INTERRUPTED)
    }

    @Test
    fun publishShowsItsStepsAndRefreshesAfterwards() = runTest {
        creator.steps = listOf(PublishStep.PRODUCT, PublishStep.TEMPLATE)
        creator.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnPublishClick)
            viewModel.onAction(WillhabenTabAction.OnPublishConfirm)

            val progress = viewModel.state.value.publishProgress!!
            assertThat(progress.current).isEqualTo(PublishStep.TEMPLATE)
            assertThat(progress.error).isNull()

            creator.pending!!.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(WillhabenTabEvent.ShowMessage::class)
            assertThat(creator.published).containsExactly(1L)
            assertThat(importer.calls).isEqualTo(1)
            assertThat(viewModel.state.value.publishProgress).isNull()
        }
    }

    @Test
    fun failedPublishKeepsTheErrorInTheProgressDialog() = runTest {
        creator.steps = listOf(PublishStep.PRODUCT, PublishStep.TEMPLATE, PublishStep.PRESAVE)
        creator.result = Result.Error(PublishError.Failed(PublishStep.PRESAVE, DataError.Network.SERVER_ERROR))
        val viewModel = viewModel()

        viewModel.onAction(WillhabenTabAction.OnPublishConfirm)

        val progress = viewModel.state.value.publishProgress!!
        assertThat(progress.current).isEqualTo(PublishStep.PRESAVE)
        assertThat(progress.error).isEqualTo(DataError.Network.SERVER_ERROR.toUiText())
        assertThat(importer.calls).isEqualTo(0)

        viewModel.onAction(WillhabenTabAction.OnPublishProgressDismiss)
        assertThat(viewModel.state.value.publishProgress).isNull()
    }

    @Test
    fun missingRequiredAttributeIsReportedWithoutPublishing() = runTest {
        fieldsSource.fields.value = mapOf(1L to WillhabenListingFields(categoryPath = bicyclePath))
        val viewModel = viewModel()
        assertThat(viewModel.state.value.attributeFields.any { it.required && it.selectedCodes.isEmpty() }).isTrue()

        viewModel.events.test {
            viewModel.onAction(WillhabenTabAction.OnPublishClick)

            assertThat(awaitItem()).isInstanceOf(WillhabenTabEvent.ShowMessage::class)
            assertThat(viewModel.state.value.isPublishConfirmOpen).isFalse()
            assertThat(creator.published).isEmpty()
        }
    }
}
