package com.lerchenflo.relisto.listings.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One image of a listing. The file lives in the app image folder, named by its SHA-256 hash. */
@Entity(
    tableName = "listing_images",
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listingId")]
)
data class ListingImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listingId: Long,
    val position: Int,
    val fileName: String,
    val sha256: String,
    val width: Int,
    val height: Int,
)
