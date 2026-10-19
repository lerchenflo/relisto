package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.map
import com.lerchenflo.relisto.datasource.network.get
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.CategorySuggestionResponseDto
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.LocationsResponseDto
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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

    /**
     * Id of the free product for listings of [adTypeId] (e.g. 67 marketplace, 40021 motorbike),
     * null when willhaben only sells paid products for it. Products sit at any depth below the groups.
     */
    suspend fun fetchFreeProductId(adTypeId: Int): Result<Int?, DataError.Network> {
        return httpClient.get<JsonObject>(route = "/iad/productgroups").map { groups ->
            groups.allObjects()
                .firstOrNull { it.int("adTypeId") == adTypeId && it.price() == 0.0 && it.int("id") != null }
                ?.int("id")
        }
    }

    private fun JsonElement.allObjects(): Sequence<JsonObject> = when (this) {
        is JsonObject -> sequenceOf(this) + values.asSequence().flatMap { it.allObjects() }
        is JsonArray -> asSequence().flatMap { it.allObjects() }
        else -> emptySequence()
    }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.price(): Double? = (this["price"] as? JsonPrimitive)?.doubleOrNull

    suspend fun fetchLocations(postcode: String): Result<LocationsResponseDto, DataError.Network> {
        return httpClient.get(
            route = "/iad/locations/$postcode",
            queryParameters = mapOf("isoCountryCode" to "AT")
        )
    }
}
