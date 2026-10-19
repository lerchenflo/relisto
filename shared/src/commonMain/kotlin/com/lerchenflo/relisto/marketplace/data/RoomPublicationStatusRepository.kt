package com.lerchenflo.relisto.marketplace.data

import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.PublicationStatus
import com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.domain.WILLHABEN_DRAFT_STATUS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
                )
            }
        }
}
