package com.lerchenflo.relisto.app.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.isNull
import com.lerchenflo.relisto.app.domain.AvailableUpdate
import com.lerchenflo.relisto.app.domain.UpdateChecker
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppUpdateViewModelTest : MainDispatcherTest() {

    private val update = AvailableUpdate("1.0.12", "https://github.com/lerchenflo/relisto/releases/tag/v1.0.12")

    private fun viewModel(available: AvailableUpdate?) = AppUpdateViewModel(
        object : UpdateChecker {
            override suspend fun checkForUpdate(): AvailableUpdate? = available
        }
    )

    @Test
    fun availableUpdateIsShown() = runTest {
        assertThat(viewModel(update).state.value.availableVersion).isEqualTo("1.0.12")
    }

    @Test
    fun noUpdateShowsNothing() = runTest {
        assertThat(viewModel(null).state.value.availableVersion).isNull()
    }

    @Test
    fun dismissedBannerHidesButKeepsUpdateButton() = runTest {
        val viewModel = viewModel(update)
        assertThat(viewModel.state.value.isBannerVisible).isTrue()

        viewModel.onAction(AppUpdateAction.OnDismissClick)

        assertThat(viewModel.state.value.isBannerVisible).isFalse()
        assertThat(viewModel.state.value.availableVersion).isEqualTo("1.0.12")
    }

    @Test
    fun noUpdateShowsNoBanner() = runTest {
        assertThat(viewModel(null).state.value.isBannerVisible).isFalse()
    }

    @Test
    fun downloadOpensReleasePage() = runTest {
        val viewModel = viewModel(update)

        viewModel.events.test {
            viewModel.onAction(AppUpdateAction.OnDownloadClick)
            assertThat(awaitItem()).isEqualTo(AppUpdateEvent.OpenUrl(update.releaseUrl))
        }
    }
}
