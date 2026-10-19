package com.lerchenflo.relisto.settings.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.navigation.NavigationAction
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.testutil.FakeSettingsDataSource
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SettingsViewModelTest : MainDispatcherTest() {

    private val settings = FakeSettingsDataSource(
        AppSettings(
            defaultPostcode = "6850",
            defaultCondition = Condition.USED,
            defaultPickup = true,
            defaultShipping = true,
            defaultShippingPriceCents = 590,
        )
    )
    private val navigator = Navigator()
    private val directories = object : AppDirectories {
        override val dataDir = "/data/Relisto"
        override val imagesDir = "/data/Relisto/images"
        override val cacheDir = "/data/Relisto/cache"
    }

    private fun viewModel() = SettingsViewModel(settings, navigator, directories)

    @Test
    fun storedSettingsAreShown() = runTest {
        val state = viewModel().state.value

        assertThat(state.postcode).isEqualTo("6850")
        assertThat(state.condition).isEqualTo(Condition.USED)
        assertThat(state.pickup).isTrue()
        assertThat(state.shipping).isTrue()
        assertThat(state.shippingPriceText).isEqualTo("5,90")
        assertThat(state.dataFolder).isEqualTo("/data/Relisto")
    }

    @Test
    fun postcodeKeepsFourDigitsAndIsSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnPostcodeChange("1a0105"))

        assertThat(viewModel.state.value.postcode).isEqualTo("1010")
        assertThat(settings.settings.value.defaultPostcode).isEqualTo("1010")
    }

    @Test
    fun conditionIsSaved() = runTest {
        viewModel().onAction(SettingsAction.OnConditionChange(null))

        assertThat(settings.settings.value.defaultCondition).isNull()
    }

    @Test
    fun deliveryTogglesAreSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnPickupChange(false))
        viewModel.onAction(SettingsAction.OnShippingChange(false))

        assertThat(settings.settings.value.defaultPickup).isFalse()
        assertThat(settings.settings.value.defaultShipping).isFalse()
    }

    @Test
    fun validShippingPriceIsSaved() = runTest {
        viewModel().onAction(SettingsAction.OnShippingPriceChange("4,50"))

        assertThat(settings.settings.value.defaultShippingPriceCents).isEqualTo(450L)
    }

    @Test
    fun emptyShippingPriceRemovesIt() = runTest {
        viewModel().onAction(SettingsAction.OnShippingPriceChange(""))

        assertThat(settings.settings.value.defaultShippingPriceCents).isNull()
    }

    @Test
    fun invalidShippingPriceIsFlaggedAndNotSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnShippingPriceChange("gratis"))

        assertThat(viewModel.state.value.isShippingPriceInvalid).isTrue()
        assertThat(settings.settings.value.defaultShippingPriceCents).isEqualTo(590L)
    }

    @Test
    fun backClickNavigatesBack() = runTest {
        val viewModel = viewModel()

        navigator.navigationActions.test {
            viewModel.onAction(SettingsAction.OnBackClick)
            assertThat(awaitItem()).isEqualTo(NavigationAction.NavigateBack())
        }
    }

    @Test
    fun autoRenewIsShownAndSaved() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.state.value.autoRenewExpired).isFalse()

        viewModel.onAction(SettingsAction.OnAutoRenewChange(true))

        assertThat(viewModel.state.value.autoRenewExpired).isTrue()
        assertThat(settings.settings.value.autoRenewExpired).isTrue()
    }
}
