package com.lerchenflo.relisto.marketplace.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lerchenflo.relisto.listings.data.ListingEntity

/**
 * Everything one marketplace knows about one listing: its fields for that marketplace,
 * the remote ids and status, and the sync state. One row per listing and marketplace.
 */
@Entity(
    tableName = "listing_publications",
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["listingId", "marketplace"], unique = true)]
)
data class ListingPublicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listingId: Long,
    /** Name of [com.lerchenflo.relisto.marketplace.domain.Marketplace]. */
    val marketplace: String,
    /** Marketplace-specific fields as JSON (willhaben: category, attributes, location, delivery). */
    val marketplaceFieldsJson: String?,
    val remoteId: String?,
    val remoteUuid: String?,
    val remoteUrl: String?,
    val remoteStatus: String?,
    val expiresAt: Long?,
    val remoteSnapshotJson: String?,
    val remoteSnapshotHash: String?,
    /** Name of [com.lerchenflo.relisto.marketplace.domain.SyncState]. */
    val syncState: String,
    val lastSyncedAt: Long?,
    val lastError: String?,
    /** Comma-separated remote ids this listing replaced by republishing; the importer skips them. */
    val replacedRemoteIds: String? = null,
    /** Status actions the marketplace offers right now (willhaben: activate, deactivate, sold, ...) as JSON. */
    val remoteActionsJson: String? = null,
    /** Fingerprint of the local images (their hashes in order) when they last matched the marketplace. */
    val syncedImagesHash: String? = null,
)
