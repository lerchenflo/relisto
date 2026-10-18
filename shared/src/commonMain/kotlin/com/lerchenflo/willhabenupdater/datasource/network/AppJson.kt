package com.lerchenflo.willhabenupdater.datasource.network

import kotlinx.serialization.json.Json

/** Lenient JSON config: willhaben adds fields without notice, unknown keys must never break parsing. */
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
