package com.lerchenflo.relisto.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {

    /** Master-detail screen: listing list on the left, editor on the right. */
    @Serializable
    data object Listings : Route

    @Serializable
    data object Settings : Route

    /** What was sent to the marketplaces; [listingId] opens it filtered to one listing. */
    @Serializable
    data class ActivityLog(val listingId: Long? = null) : Route
}
