package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields

/** Fingerprint of everything the app manages on willhaben; status, expiry and ad type are not part of it. */
internal fun willhabenContentHash(content: ListingContent, fields: WillhabenListingFields): String =
    "$content|${fields.copy(adTypeId = null)}".hashCode().toUInt().toString(16)
