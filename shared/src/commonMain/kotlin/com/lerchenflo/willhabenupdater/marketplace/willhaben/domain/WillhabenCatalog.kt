package com.lerchenflo.willhabenupdater.marketplace.willhaben.domain

import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.EmptyResult
import com.lerchenflo.willhabenupdater.app.domain.Result
import kotlinx.coroutines.flow.Flow

/** Marketplace category tree of willhaben, including the attribute definitions. */
data class WillhabenCategoryTree(
    val root: WillhabenCategory,
    val attributesByCode: Map<String, WillhabenAttribute>,
) {
    /** Every leaf category with its path from the top category (the root itself is not part of a path). */
    fun leaves(): List<List<WillhabenCategory>> = pathsByTreeId.values.filter { it.last().children.isEmpty() }

    /** Path from the top category down to the category with [treeId], or null if unknown. */
    fun pathOf(treeId: Int): List<WillhabenCategory>? = pathsByTreeId[treeId]

    private val pathsByTreeId: Map<Int, List<WillhabenCategory>> by lazy {
        buildMap {
            fun walk(node: WillhabenCategory, parentPath: List<WillhabenCategory>) {
                val path = parentPath + node
                put(node.treeId, path)
                node.children.forEach { walk(it, path) }
            }
            root.children.forEach { walk(it, emptyList()) }
        }
    }
}

data class WillhabenCategory(
    val treeId: Int,
    val code: String,
    val label: String,
    val children: List<WillhabenCategory>,
    val attributeReferences: List<WillhabenAttributeReference>,
)

data class WillhabenAttributeReference(
    val code: String,
    val required: Boolean,
    val multiSelect: Boolean,
)

data class WillhabenAttribute(
    val code: String,
    val label: String,
    val values: List<WillhabenAttributeValue>,
)

data class WillhabenAttributeValue(
    val code: String,
    val label: String,
)

data class WillhabenLocation(
    val id: Int,
    val label: String,
)

/** Public willhaben reference data (no login needed). */
interface WillhabenCatalogRepository {
    /** Cached for a week; [forceRefresh] downloads it again. */
    suspend fun getCategoryTree(forceRefresh: Boolean = false): Result<WillhabenCategoryTree, DataError>

    /** Leaf category tree ids suggested by willhaben for a listing title, best first. */
    suspend fun suggestCategories(title: String): Result<List<Int>, DataError.Network>

    suspend fun findLocations(postcode: String): Result<List<WillhabenLocation>, DataError.Network>
}

/** willhaben fields stored per listing. */
interface WillhabenFieldsLocalDataSource {
    fun getFieldsAsFlow(listingId: Long): Flow<WillhabenListingFields?>
    suspend fun getFields(listingId: Long): WillhabenListingFields?
    suspend fun saveFields(listingId: Long, fields: WillhabenListingFields): EmptyResult<DataError.Local>
}

/** Attribute codes that are not edited in the willhaben tab because they come from neutral fields. */
object WillhabenAttributeCodes {
    /** "Zustand" - filled from the listing condition. */
    const val CONDITION = "Zustand"

    /** "Übergabe" - filled from the delivery options. */
    const val HANDOVER = "Uebergabe"
}
