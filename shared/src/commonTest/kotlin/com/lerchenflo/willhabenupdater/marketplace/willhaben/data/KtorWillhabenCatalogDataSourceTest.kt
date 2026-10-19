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

    /** Shape of GET /iad/productgroups as seen on 2026-10-19, shortened. */
    private val productGroups = """
        {"productGroupList":[
          {"title":"Marktplatz","adTypeId":67,"productContextLinkList":{"productContextLink":[
            {"adTypeId":67,"price":0.00,"duration":45,"id":"67","relativePath":"/bap","serviceName":"iad"}]}},
          {"title":"Fahrzeuge","adTypeId":0,"children":[
            {"title":"Motorrad & Quad","adTypeId":21,"productContextLinkList":{"productContextLink":[
              {"adTypeId":21,"price":19.90,"id":"40121"},
              {"adTypeId":21,"price":0.00,"duration":7,"id":"40021","relativePath":"/motor/mc?productId=40021"}]}}]},
          {"title":"Haus","adTypeId":1,"productContextLinkList":{"productContextLink":[
            {"adTypeId":1,"price":49.00,"id":"1001"}]}}
        ]}
    """

    @Test
    fun freeProductIsFoundPerAdType() = runTest {
        val dataSource = dataSource(productGroups)

        assertThat(dataSource.fetchFreeProductId(67)).isEqualTo(Result.Success(67))
        assertThat(dataSource.fetchFreeProductId(21)).isEqualTo(Result.Success(40021))
        assertThat(requestedUrls.first()).isEqualTo("https://www.willhaben.at/webapi/iad/productgroups")
    }

    @Test
    fun adTypeWithoutFreeProductGivesNull() = runTest {
        val dataSource = dataSource(productGroups)

        assertThat(dataSource.fetchFreeProductId(1)).isEqualTo(Result.Success(null))
        assertThat(dataSource.fetchFreeProductId(26)).isEqualTo(Result.Success(null))
    }
}
