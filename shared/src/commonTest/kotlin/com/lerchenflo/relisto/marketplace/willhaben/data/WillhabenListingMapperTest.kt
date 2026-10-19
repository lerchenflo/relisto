package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListing
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import kotlin.test.Test

/** Structure of GET /iad/bap/{adId} as recorded on 2026-10-18; all values are made up. */
class WillhabenListingMapperTest {

    private fun detailJson(
        zustand: String = "gebraucht",
        uebergabe: List<String> = listOf("PICKUP"),
        price: String = "200.0",
    ) = """
        {
          "heading": "Rasentraktor",
          "description": "Gut erhalten<br />Mit Fangkorb",
          "price": $price,
          "adTypeId": 67,
          "productId": 67,
          "phoneNo": "secret-phone",
          "emailAddress": "secret@example.at",
          "mainCategory": "Haus",
          "categoryTreeId": 3653,
          "categoryPath": {"path": [
            {"label": "Haus / Garten / Werkstatt", "code": "HOUSE_GARDENING_WORKSHOP"},
            {"label": "Rasenmäher", "code": "MOWER_TRACTOR"}
          ]},
          "postCode": "6850",
          "locationId": 117,
          "location": "Dornbirn",
          "adId": 706217052,
          "endDate": "2023-10-08T09:44:00.000+00:00",
          "treeAttributes": [
            {"attributeTreeId": 21, "attributeNode": {"label": "Zustand", "code": "Zustand"},
             "selectedAttributeValueNodes": [{"label": "x", "code": "$zustand"}]},
            {"attributeTreeId": 2535, "attributeNode": {"label": "Übergabe", "code": "Uebergabe"},
             "selectedAttributeValueNodes": [${uebergabe.joinToString { """{"label": "x", "code": "$it"}""" }}]},
            {"attributeTreeId": 99, "attributeNode": {"label": "Farbe", "code": "COLOR"},
             "selectedAttributeValueNodes": [{"label": "Rot", "code": "RED"}, {"label": "Blau", "code": "BLUE"}]}
          ],
          "adUuid": "uuid-1"
        }
    """

    private val summary = RemoteListingSummary(
        remoteId = "706217052",
        remoteUuid = "uuid-1",
        adTypeId = 67,
        title = "Rasentraktor",
        statusText = "Abgelaufen",
        imageUrls = listOf("https://cache.willhaben.at/a.jpg"),
    )

    private fun map(json: String = detailJson(), pathOf: (Int) -> List<WillhabenCategoryRef>? = { null }) =
        WillhabenListingMapper.toImportedListing(RemoteListing(summary, json), pathOf)!!

    @Test
    fun contentIsTakenFromTheListing() {
        assertThat(map().content).isEqualTo(
            ListingContent(
                title = "Rasentraktor",
                description = "Gut erhalten\nMit Fangkorb",
                priceCents = 20000,
                condition = Condition.USED,
                postcode = "6850",
            )
        )
    }

    @Test
    fun conditionCodesMapToNeutralConditions() {
        assertThat(map(detailJson(zustand = "neu")).content.condition).isEqualTo(Condition.NEW)
        assertThat(map(detailJson(zustand = "MintCondition")).content.condition).isEqualTo(Condition.LIKE_NEW)
        assertThat(map(detailJson(zustand = "defekt")).content.condition).isEqualTo(Condition.DEFECTIVE)
        assertThat(map(detailJson(zustand = "unbekannt")).content.condition).isNull()
    }

    @Test
    fun centsAreRoundedFromDecimalPrice() {
        assertThat(map(detailJson(price = "12.5")).content.priceCents).isEqualTo(1250L)
    }

    @Test
    fun handoverBecomesDeliveryOptions() {
        assertThat(map(detailJson(uebergabe = listOf("PICKUP", "Versand"))).fields.delivery)
            .isEqualTo(DeliveryOptions(pickup = true, shipping = true))
        assertThat(map(detailJson(uebergabe = listOf("Versand"))).fields.delivery)
            .isEqualTo(DeliveryOptions(pickup = false, shipping = true))
    }

    @Test
    fun otherAttributesAreKeptWithoutConditionAndHandover() {
        assertThat(map().fields.attributes).isEqualTo(mapOf("COLOR" to listOf("RED", "BLUE")))
    }

    @Test
    fun locationIsKept() {
        val fields = map().fields
        assertThat(fields.locationId).isEqualTo(117)
        assertThat(fields.locationLabel).isEqualTo("Dornbirn")
    }

    @Test
    fun categoryPathComesFromTheCategoryTreeWhenKnown() {
        val treePath = listOf(WillhabenCategoryRef(6000, "HOUSE_GARDENING_WORKSHOP", "Haus / Garten / Werkstatt"), WillhabenCategoryRef(3653, "MOWER_TRACTOR", "Rasenmäher"))

        assertThat(map(pathOf = { if (it == 3653) treePath else null }).fields.categoryPath).isEqualTo(treePath)
    }

    @Test
    fun categoryPathFallsBackToLabelsWithLeafId() {
        assertThat(map().fields.categoryPath).isEqualTo(
            listOf(
                WillhabenCategoryRef(0, "HOUSE_GARDENING_WORKSHOP", "Haus / Garten / Werkstatt"),
                WillhabenCategoryRef(3653, "MOWER_TRACTOR", "Rasenmäher"),
            )
        )
    }

    @Test
    fun remoteDataComesFromSummaryAndDetail() {
        val imported = map()
        assertThat(imported.remoteId).isEqualTo("706217052")
        assertThat(imported.remoteUuid).isEqualTo("uuid-1")
        assertThat(imported.remoteStatus).isEqualTo("Abgelaufen")
        // 2023-10-08T09:44:00Z
        assertThat(imported.expiresAt).isEqualTo(1696758240000L)
        assertThat(imported.imageUrls).isEqualTo(listOf("https://cache.willhaben.at/a.jpg"))
    }

    @Test
    fun adTypeIsKeptInTheFields() {
        assertThat(map().fields.adTypeId).isEqualTo(67)
    }

    @Test
    fun listingOfAnotherVerticalFallsBackToTheSummary() {
        // Auto & Motor JSON (GET /iad/motor/get/{adId}) has not been recorded yet; only shared keys are known
        val motorSummary = summary.copy(adTypeId = 21, title = "Beta RR 50 Moped / Mofa")

        val imported = WillhabenListingMapper.toImportedListing(RemoteListing(motorSummary, """{"adId":1,"price":1500}""")) { null }!!

        assertThat(imported.content.title).isEqualTo("Beta RR 50 Moped / Mofa")
        assertThat(imported.content.priceCents).isEqualTo(150000L)
        assertThat(imported.fields.adTypeId).isEqualTo(21)
        assertThat(imported.fields.categoryPath).isEqualTo(emptyList())
    }

    @Test
    fun adTypeDoesNotChangeTheContentHash() {
        // Listings stored before the ad type was kept must not look changed
        val imported = map()

        assertThat(willhabenContentHash(imported.content, imported.fields))
            .isEqualTo(willhabenContentHash(imported.content, imported.fields.copy(adTypeId = null)))
    }

    @Test
    fun brokenJsonGivesNull() {
        assertThat(WillhabenListingMapper.toImportedListing(RemoteListing(summary, "not json")) { null }).isNull()
    }
}
