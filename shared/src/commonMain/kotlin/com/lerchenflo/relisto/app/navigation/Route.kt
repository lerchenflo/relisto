package com.lerchenflo.relisto.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {

    /** Master-detail screen: listing list on the left, editor on the right. */
    @Serializable
    data object Listings : Route

    @Serializable
    data object Settings : Route
}
