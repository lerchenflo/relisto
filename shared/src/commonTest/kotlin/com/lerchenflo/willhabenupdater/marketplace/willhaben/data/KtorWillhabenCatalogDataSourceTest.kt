package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.dto.LocationDto
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.dto.LocationsResponseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KtorWillhabenCatalogDataSourceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requestedUrls = mutableListOf<String>()

    private fun dataSource(body: String) = KtorWillhabenCatalogDataSource(
        HttpClientFactory.createWillhabenClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                respond(body, HttpStatusCode.OK, jsonHeaders)
            }
        )
    )

    @Test
    fun categoryTreeIsLoadedForFreeMarketplaceAdType() = runTest {
        val result = dataSource("""{"categoryNode":{}}""").fetchCategoryTreeJson()

        assertThat(requestedUrls.single()).isEqualTo("https://www.willhaben.at/webapi/iad/categorytree/withattributes/67")
        assertThat(result).isEqualTo(Result.Success("""{"categoryNode":{}}"""))
    }

    @Test
    fun categorySuggestionsSendTitleAdTypeAndProduct() = runTest {
        dataSource("""{"categories":[]}""").fetchCategorySuggestions("Kinder Fahrrad")

        assertThat(requestedUrls.single()).isEqualTo(
            "https://www.willhaben.at/webapi/iad/bap/categorysuggestion/attributetree?title=Kinder+Fahrrad&adTypeId=67&productId=67"
        )
    }

    @Test
    fun locationsAreLookedUpByPostcodeInAustria() = runTest {
        val result = dataSource("""{"locations":[{"id":117223,"description":"Wien, 01. Bezirk, Innere Stadt"}]}""")
            .fetchLocations("1010")

        assertThat(requestedUrls.single()).isEqualTo("https://www.willhaben.at/webapi/iad/locations/1010?isoCountryCode=AT")
        assertThat(result).isEqualTo(
            Result.Success(LocationsResponseDto(listOf(LocationDto(117223, "Wien, 01. Bezirk, Innere Stadt"))))
        )
    }
}
