package com.lerchenflo.willhabenupdater.marketplace.data

import com.lerchenflo.willhabenupdater.datasource.database.PublicationDao
import com.lerchenflo.willhabenupdater.marketplace.domain.Marketplace
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatus
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.SyncState
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
                )
            }
        }
}
