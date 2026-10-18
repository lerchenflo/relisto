package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields

/** Fingerprint of everything the app manages on willhaben; status and expiry are not part of it. */
internal fun willhabenContentHash(content: ListingContent, fields: WillhabenListingFields): String =
    "$content|$fields".hashCode().toUInt().toString(16)
