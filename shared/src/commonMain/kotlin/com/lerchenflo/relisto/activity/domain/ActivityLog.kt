package com.lerchenflo.relisto.activity.domain

import com.lerchenflo.relisto.marketplace.domain.Marketplace
import kotlinx.coroutines.flow.Flow

/** Writes the app sends to a marketplace; every one is logged with its result. */
enum class ActivityAction {
    PUSH_CHANGES,
    TAKE_REMOTE,
    REPUBLISH,
    PUBLISH,
    ACTIVATE,
    DEACTIVATE,
    MARK_RESERVED,
    REMOVE_RESERVED,
    MARK_SOLD,
    DELETE
}

data class ActivityEntry(
    val id: Long,
    val listingId: Long?,
    val listingTitle: String?,
    val marketplace: Marketplace,
    val timestamp: Long,
    val action: ActivityAction,
    val success: Boolean,
    val message: String?,
)

interface ActivityLog {
    /** Newest first. */
    fun getEntriesAsFlow(): Flow<List<ActivityEntry>>

    suspend fun record(listingId: Long?, marketplace: Marketplace, action: ActivityAction, success: Boolean, message: String? = null)
}
