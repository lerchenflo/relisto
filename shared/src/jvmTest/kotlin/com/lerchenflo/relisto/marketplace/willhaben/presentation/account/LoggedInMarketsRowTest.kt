package com.lerchenflo.relisto.marketplace.willhaben.presentation.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoggedInMarketsRowTest {

    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun tearDown() = Locale.setDefault(originalLocale)

    @Test
    fun loggedInMarketplaceIsShown() = runComposeUiTest {
        setContent { LoggedInMarketsRow(state = AccountState(isChecking = false, isLoggedIn = true)) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription("willhaben: logged in").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(onAllNodesWithContentDescription("willhaben: logged in").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun nothingIsShownWhenLoggedOut() = runComposeUiTest {
        setContent { LoggedInMarketsRow(state = AccountState(isChecking = false, isLoggedIn = false)) }

        waitForIdle()
        assertThat(onAllNodesWithContentDescription("willhaben: logged in").fetchSemanticsNodes()).isEmpty()
    }
}
