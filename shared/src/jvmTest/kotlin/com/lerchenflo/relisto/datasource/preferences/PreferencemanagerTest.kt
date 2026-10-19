package com.lerchenflo.relisto.datasource.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.di.createDataStore
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.testutil.TempAppDirectories
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test

class PreferencemanagerTest {

    private val dirs = TempAppDirectories()
    private val preferencemanager = Preferencemanager(
        createDataStore { File(dirs.root, "test.preferences_pb").absolutePath }
    )

    @AfterTest
    fun tearDown() = dirs.delete()

    @Test
    fun emptyStoreGivesDefaults() = runTest {
        assertThat(preferencemanager.getSettings()).isEqualTo(
            AppSettings(
                defaultPostcode = "",
                defaultCondition = null,
                defaultPickup = true,
                defaultShipping = false,
                defaultShippingPriceCents = null,
                autoRenewExpired = false,
            )
        )
    }

    @Test
    fun updatedSettingsAreReadBack() = runTest {
        val wanted = AppSettings(
            defaultPostcode = "6850",
            defaultCondition = Condition.LIKE_NEW,
            defaultPickup = false,
            defaultShipping = true,
            defaultShippingPriceCents = 590,
            autoRenewExpired = true,
        )

        preferencemanager.updateSettings { wanted }

        assertThat(preferencemanager.getSettings()).isEqualTo(wanted)
    }

    @Test
    fun clearingOptionalValuesRemovesThem() = runTest {
        preferencemanager.updateSettings { it.copy(defaultCondition = Condition.NEW, defaultShippingPriceCents = 590) }

        preferencemanager.updateSettings { it.copy(defaultCondition = null, defaultShippingPriceCents = null) }

        val settings = preferencemanager.getSettings()
        assertThat(settings.defaultCondition).isEqualTo(null)
        assertThat(settings.defaultShippingPriceCents).isEqualTo(null)
    }

    @Test
    fun updateTransformsCurrentValue() = runTest {
        preferencemanager.updateSettings { it.copy(defaultPostcode = "1010") }

        preferencemanager.updateSettings { it.copy(defaultShipping = true) }

        val settings = preferencemanager.getSettings()
        assertThat(settings.defaultPostcode).isEqualTo("1010")
        assertThat(settings.defaultShipping).isEqualTo(true)
    }
}
