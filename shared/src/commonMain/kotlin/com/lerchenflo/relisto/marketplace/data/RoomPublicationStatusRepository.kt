package com.lerchenflo.relisto.marketplace.data

import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.datasource.network.AppJson
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.domain.WILLHABEN_DRAFT_STATUS
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenAdTypes
import com.lerchenflo.relisto.marketplace.willhaben.data.dto.WillhabenFieldsDto
import com.lerchenflo.relisto.marketplace.willhaben.data.toStatusActionLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The ad type stored in the willhaben fields; null for marketplace listings stored before it was kept. */
private fun ListingPublicationEntity.adTypeId(): Int? = marketplaceFieldsJson
    ?.let { runCatching { AppJson.decodeFromString(WillhabenFieldsDto.serializer(), it) }.getOrNull() }
    ?.adTypeId

class RoomPublicationStatusRepository(
    private val publicationDao: PublicationDao,
) : PublicationStatusRepository {

    override fun getStatusesAsFlow(): Flow<List<PublicationStatus>> =
        publicationDao.getAllAsFlow().map { rows ->
            rows.mapNotNull { row ->
                val marketplace = Marketplace.entries.firstOrNull { it.name == row.marketplace } ?: return@mapNotNull null
                PublicationStatus(
                    listingId = row.listingId,
                    marketplace = marketplace,
                    statusText = row.remoteStatus,
                    expiresAt = row.expiresAt,
                    syncState = SyncState.entries.firstOrNull { it.name == row.syncState } ?: SyncState.LOCAL_ONLY,
                    isRenewDraft = row.remoteStatus == WILLHABEN_DRAFT_STATUS && !row.replacedRemoteIds.isNullOrBlank(),
                    availableActions = row.remoteActionsJson?.toStatusActionLinks().orEmpty().map { it.action.name }.toSet(),
                    runtimeMillis = if (marketplace == Marketplace.WILLHABEN) WillhabenAdTypes.runtimeMillis(row.adTypeId()) else null,
                    remoteUrl = row.remoteUrl,
                )
            }
        }
}
