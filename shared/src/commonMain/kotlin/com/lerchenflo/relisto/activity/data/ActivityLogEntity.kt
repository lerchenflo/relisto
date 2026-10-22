package com.lerchenflo.relisto.activity.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One write the app sent to a marketplace and its result. No foreign key: entries stay when the
 * listing is deleted. Only listing data is stored, never personal data, cookies or tokens.
 */
@Entity(tableName = "activity_log", indices = [Index(value = ["listingId"])])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listingId: Long?,
    /** The listing's title when the entry was written, so the log stays readable after a delete. */
    val listingTitle: String?,
    /** Name of [com.lerchenflo.relisto.marketplace.domain.Marketplace]. */
    val marketplace: String,
    val timestamp: Long,
    /** Name of [com.lerchenflo.relisto.activity.domain.ActivityAction]. */
    val action: String,
    val success: Boolean,
    /** Error name on failure (e.g. "Failed(EDIT, SERVER_ERROR)"), null on success. */
    val message: String?,
)
