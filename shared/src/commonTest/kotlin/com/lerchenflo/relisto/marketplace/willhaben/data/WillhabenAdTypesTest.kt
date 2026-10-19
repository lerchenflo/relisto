package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

/** Routes per ad type as the willhaben web client (bbx-adin) builds them. */
class WillhabenAdTypesTest {

    @Test
    fun marketplaceListingsUseTheBapRoutes() {
        assertThat(WillhabenAdTypes.detailRoute("1", 67)).isEqualTo("/iad/bap/1")
        assertThat(WillhabenAdTypes.copyRoute("1", 67)).isEqualTo("/iad/bap/copy/1")
    }

    @Test
    fun unknownAdTypeIsTreatedAsMarketplace() {
        // Listings imported before the ad type was stored are all marketplace listings
        assertThat(WillhabenAdTypes.detailRoute("1", null)).isEqualTo("/iad/bap/1")
        assertThat(WillhabenAdTypes.copyRoute("1", null)).isEqualTo("/iad/bap/copy/1")
    }

    @Test
    fun commercialMarketplaceListingsUseTheBapcomRoute() {
        assertThat(WillhabenAdTypes.detailRoute("1", 69)).isEqualTo("/iad/bapcom/1")
    }

    @Test
    fun motorListingsUseTheMotorRoutes() {
        listOf(20, 21, 25, 26).forEach { adTypeId ->
            assertThat(WillhabenAdTypes.detailRoute("1", adTypeId)).isEqualTo("/iad/motor/get/1")
            assertThat(WillhabenAdTypes.copyRoute("1", adTypeId)).isEqualTo("/iad/motor/copy/1")
        }
    }

    @Test
    fun realEstateListingsUseTheEstateRoute() {
        listOf(1, 2, 3, 4, 5, 6, 8, 15, 16).forEach { adTypeId ->
            assertThat(WillhabenAdTypes.detailRoute("1", adTypeId)).isEqualTo("/iad/estate/1")
        }
    }

    @Test
    fun jobsAndUnknownTypesHaveNoRoute() {
        assertThat(WillhabenAdTypes.detailRoute("1", 401)).isNull()
        assertThat(WillhabenAdTypes.detailRoute("1", 999)).isNull()
        assertThat(WillhabenAdTypes.copyRoute("1", 999)).isNull()
    }
}
