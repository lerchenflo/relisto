package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.WillhabenFieldsDto
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** willhaben fields as JSON in the listing's willhaben publication row. */
class RoomWillhabenFieldsDataSource(
    private val publicationDao: PublicationDao,
) : WillhabenFieldsLocalDataSource {

    override fun getFieldsAsFlow(listingId: Long): Flow<WillhabenListingFields?> {
        return publicationDao.getByListingAndMarketplaceAsFlow(listingId, MARKETPLACE)
            .map { it?.marketplaceFieldsJson?.decodeFields() }
    }

    override suspend fun getFields(listingId: Long): WillhabenListingFields? {
        return publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)?.marketplaceFieldsJson?.decodeFields()
    }

    override suspend fun saveFields(listingId: Long, fields: WillhabenListingFields): EmptyResult<DataError.Local> {
        return try {
            val json = AppJson.encodeToString(WillhabenFieldsDto.serializer(), fields.toDto())
            val current = publicationDao.getByListingAndMarketplace(listingId, MARKETPLACE)
            when {
                current == null -> publicationDao.insert(newPublication(listingId, json))
                current.marketplaceFieldsJson != json -> {
                    val syncState = if (current.syncState == SyncState.SYNCED.name) {
                        SyncState.LOCAL_CHANGES.name
                    } else {
                        current.syncState
                    }
                    publicationDao.update(current.copy(marketplaceFieldsJson = json, syncState = syncState))
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private fun newPublication(listingId: Long, json: String) = ListingPublicationEntity(
        listingId = listingId,
        marketplace = MARKETPLACE,
        marketplaceFieldsJson = json,
        remoteId = null,
        remoteUuid = null,
        remoteUrl = null,
        remoteStatus = null,
        expiresAt = null,
        remoteSnapshotJson = null,
        remoteSnapshotHash = null,
        syncState = SyncState.LOCAL_ONLY.name,
        lastSyncedAt = null,
        lastError = null,
    )

    private fun String.decodeFields(): WillhabenListingFields? = try {
        AppJson.decodeFromString(WillhabenFieldsDto.serializer(), this).toFields()
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        null
    }

    private companion object {
        val MARKETPLACE = Marketplace.WILLHABEN.name
    }
}
