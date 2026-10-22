package com.lerchenflo.relisto.listings.domain

data class Listing(
    val id: Long,
    val title: String,
    val description: String,
    val priceCents: Long?,
    val condition: Condition?,
    val postcode: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Ordered by position; the first image is the cover. */
    val images: List<ListingImage>,
)

data class ListingImage(
    val id: Long,
    val listingId: Long,
    val position: Int,
    /** Absolute path of the stored copy. */
    val filePath: String,
    val width: Int,
    val height: Int,
    /** SHA-256 of the image bytes; also the stored file's name. */
    val sha256: String,
)

/** Fingerprint of an ordered image list (the images' SHA-256 values), to see whether the images changed. */
fun List<String>.imagesFingerprint(): String = joinToString(",").hashCode().toUInt().toString(16)

/** The fields of a listing the user edits in the editor. */
data class ListingContent(
    val title: String = "",
    val description: String = "",
    val priceCents: Long? = null,
    val condition: Condition? = null,
    val postcode: String = "",
)

fun Listing.toContent() = ListingContent(
    title = title,
    description = description,
    priceCents = priceCents,
    condition = condition,
    postcode = postcode,
)

/** Marketplace-neutral item condition; each marketplace maps it to its own values. */
enum class Condition {
    NEW,
    LIKE_NEW,
    USED,
    DEFECTIVE
}
