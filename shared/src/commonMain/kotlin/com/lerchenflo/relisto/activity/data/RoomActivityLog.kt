package com.lerchenflo.relisto.activity.data

import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.activity.domain.ActivityEntry
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.datasource.database.ActivityLogDao
import com.lerchenflo.relisto.datasource.database.ListingDao
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.utilities.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Activity log in the local database; entries older than half a year are removed when a new one is written. */
class RoomActivityLog(
    private val activityLogDao: ActivityLogDao,
    private val listingDao: ListingDao,
    private val timeProvider: TimeProvider,
) : ActivityLog {

    override fun getEntriesAsFlow(): Flow<List<ActivityEntry>> =
        activityLogDao.getAllAsFlow().map { rows -> rows.mapNotNull { it.toEntry() } }

    override suspend fun record(
        listingId: Long?,
        marketplace: Marketplace,
        action: ActivityAction,
        success: Boolean,
        message: String?,
    ) {
        val now = timeProvider.nowEpochMillis()
        activityLogDao.deleteOlderThan(now - KEEP_MILLIS)
        activityLogDao.insert(
            ActivityLogEntity(
                listingId = listingId,
                listingTitle = listingId?.let { listingDao.getById(it)?.listing?.title },
                marketplace = marketplace.name,
                timestamp = now,
                action = action.name,
                success = success,
                message = message,
            )
        )
    }

    private fun ActivityLogEntity.toEntry(): ActivityEntry? = ActivityEntry(
        id = id,
        listingId = listingId,
        listingTitle = listingTitle,
        marketplace = Marketplace.entries.firstOrNull { it.name == marketplace } ?: return null,
        timestamp = timestamp,
        action = ActivityAction.entries.firstOrNull { it.name == action } ?: return null,
        success = success,
        message = message,
    )

    private companion object {
        const val KEEP_MILLIS = 180L * 24 * 60 * 60 * 1000
    }
}
