package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.get
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.dto.CategorySuggestionResponseDto
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.dto.LocationsResponseDto
import io.ktor.client.HttpClient

/** Free private marketplace listing ("Marktplatz"). */
internal const val MARKETPLACE_AD_TYPE_ID = 67
internal const val MARKETPLACE_PRODUCT_ID = 67

/** Public willhaben endpoints; none of them needs a login. */
class KtorWillhabenCatalogDataSource(
    private val httpClient: HttpClient,
) {

    /** Raw JSON, so the response can be cached on disk exactly as received. */
    suspend fun fetchCategoryTreeJson(): Result<String, DataError.Network> {
        return httpClient.get(route = "/iad/categorytree/withattributes/$MARKETPLACE_AD_TYPE_ID")
    }

    suspend fun fetchCategorySuggestions(title: String): Result<CategorySuggestionResponseDto, DataError.Network> {
        return httpClient.get(
            route = "/iad/bap/categorysuggestion/attributetree",
            queryParameters = mapOf(
                "title" to title,
                "adTypeId" to MARKETPLACE_AD_TYPE_ID,
                "productId" to MARKETPLACE_PRODUCT_ID,
            )
        )
    }

    suspend fun fetchLocations(postcode: String): Result<LocationsResponseDto, DataError.Network> {
        return httpClient.get(
            route = "/iad/locations/$postcode",
            queryParameters = mapOf("isoCountryCode" to "AT")
        )
    }
}
