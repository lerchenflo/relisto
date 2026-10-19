package com.lerchenflo.relisto.listings.data

import androidx.room.Embedded
import androidx.room.Relation

data class ListingWithImages(
    @Embedded
    val listing: ListingEntity,
    @Relation(parentColumn = "id", entityColumn = "listingId")
    val images: List<ListingImageEntity>,
)
