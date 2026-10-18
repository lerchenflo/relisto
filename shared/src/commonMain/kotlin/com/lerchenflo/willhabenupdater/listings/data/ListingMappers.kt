package com.lerchenflo.willhabenupdater.listings.data

import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.listings.domain.Listing
import com.lerchenflo.willhabenupdater.listings.domain.ListingImage

fun ListingWithImages.toListing(absolutePath: (String) -> String): Listing = Listing(
    id = listing.id,
    title = listing.title,
    description = listing.description,
    priceCents = listing.priceCents,
    condition = listing.condition?.let { name -> Condition.entries.firstOrNull { it.name == name } },
    postcode = listing.postcode,
    archived = listing.archived,
    createdAt = listing.createdAt,
    updatedAt = listing.updatedAt,
    images = images.sortedBy { it.position }.map { it.toListingImage(absolutePath) },
)

fun ListingImageEntity.toListingImage(absolutePath: (String) -> String): ListingImage = ListingImage(
    id = id,
    listingId = listingId,
    position = position,
    filePath = absolutePath(fileName),
    width = width,
    height = height,
)
