package com.lerchenflo.willhabenupdater.datasource.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.settings.domain.AppSettings
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** App settings in Preferences DataStore. */
class Preferencemanager(
    private val prefs: DataStore<Preferences>,
) : SettingsDataSource {

    private object Keys {
        val DEFAULT_POSTCODE = stringPreferencesKey("default_postcode")
        val DEFAULT_CONDITION = stringPreferencesKey("default_condition")
        val DEFAULT_PICKUP = booleanPreferencesKey("default_pickup")
        val DEFAULT_SHIPPING = booleanPreferencesKey("default_shipping")
        val DEFAULT_SHIPPING_PRICE_CENTS = longPreferencesKey("default_shipping_price_cents")
        val AUTO_RENEW_EXPIRED = booleanPreferencesKey("auto_renew_expired")
    }

    override fun getSettingsAsFlow(): Flow<AppSettings> = prefs.data.map { it.toAppSettings() }

    override suspend fun getSettings(): AppSettings = getSettingsAsFlow().first()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        prefs.edit { preferences ->
            preferences.write(transform(preferences.toAppSettings()))
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            defaultPostcode = this[Keys.DEFAULT_POSTCODE] ?: defaults.defaultPostcode,
            defaultCondition = this[Keys.DEFAULT_CONDITION]?.let { name -> Condition.entries.firstOrNull { it.name == name } },
            defaultPickup = this[Keys.DEFAULT_PICKUP] ?: defaults.defaultPickup,
            defaultShipping = this[Keys.DEFAULT_SHIPPING] ?: defaults.defaultShipping,
            defaultShippingPriceCents = this[Keys.DEFAULT_SHIPPING_PRICE_CENTS],
            autoRenewExpired = this[Keys.AUTO_RENEW_EXPIRED] ?: defaults.autoRenewExpired,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.DEFAULT_POSTCODE] = settings.defaultPostcode
        this[Keys.DEFAULT_PICKUP] = settings.defaultPickup
        this[Keys.DEFAULT_SHIPPING] = settings.defaultShipping
        this[Keys.AUTO_RENEW_EXPIRED] = settings.autoRenewExpired
        val condition = settings.defaultCondition
        if (condition == null) remove(Keys.DEFAULT_CONDITION) else this[Keys.DEFAULT_CONDITION] = condition.name
        val shippingPrice = settings.defaultShippingPriceCents
        if (shippingPrice == null) remove(Keys.DEFAULT_SHIPPING_PRICE_CENTS) else this[Keys.DEFAULT_SHIPPING_PRICE_CENTS] = shippingPrice
    }
}
