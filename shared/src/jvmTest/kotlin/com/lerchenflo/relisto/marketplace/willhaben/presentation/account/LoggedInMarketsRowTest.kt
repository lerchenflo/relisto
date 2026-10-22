package com.lerchenflo.relisto.marketplace.willhaben.presentation.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
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
    fun loggedOutShowsALoginHint() = runComposeUiTest {
        val actions = mutableListOf<AccountAction>()
        setContent { LoggedInMarketsRow(state = AccountState(isChecking = false, isLoggedIn = false), onAction = { actions += it }) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Log in", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(onAllNodesWithContentDescription("willhaben: logged in").fetchSemanticsNodes()).isEmpty()
        assertThat(onAllNodesWithText("Not logged in to willhaben", substring = true).fetchSemanticsNodes()).hasSize(1)

        onNodeWithText("Log in").performClick()
        assertThat(actions).containsExactly(AccountAction.OnLoginClick)
    }

    @Test
    fun noHintWhileTheLoginIsChecked() = runComposeUiTest {
        setContent { LoggedInMarketsRow(state = AccountState(isChecking = true, isLoggedIn = false)) }

        waitForIdle()
        assertThat(onAllNodesWithText("Not logged in to willhaben", substring = true).fetchSemanticsNodes()).isEmpty()
    }
}
