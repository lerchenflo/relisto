package com.lerchenflo.relisto.listings.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Marketplace-neutral content of one listing. */
@Entity(tableName = "listings")
data class ListingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val priceCents: Long?,
    /** Name of [com.lerchenflo.relisto.listings.domain.Condition], null = not set. */
    val condition: String?,
    val postcode: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
