package com.lerchenflo.relisto.settings.domain

import com.lerchenflo.relisto.listings.domain.Condition
import kotlinx.coroutines.flow.Flow

/** Defaults applied to every new listing, and app behaviour. */
data class AppSettings(
    val defaultPostcode: String = "",
    val defaultCondition: Condition? = null,
    val defaultPickup: Boolean = true,
    val defaultShipping: Boolean = false,
    val defaultShippingPriceCents: Long? = null,
    /** Renew every expired willhaben listing automatically once logged in after the app starts. */
    val autoRenewExpired: Boolean = false,
)

interface SettingsDataSource {
    fun getSettingsAsFlow(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}
