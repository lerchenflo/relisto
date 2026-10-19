package com.lerchenflo.willhabenupdater

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEmpty
import com.lerchenflo.willhabenupdater.di.datasourceModule
import com.lerchenflo.willhabenupdater.di.presentationModule
import com.lerchenflo.willhabenupdater.testutil.TempAppDirectories
import com.lerchenflo.willhabenupdater.testutil.testPlatformModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Starts the whole app on the desktop renderer and walks the main flow. */
@OptIn(ExperimentalTestApi::class)
class AppSmokeTest {

    private val dirs = TempAppDirectories()
    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        // Texts below are the English ones; the machine's language must not matter
        Locale.setDefault(Locale.ENGLISH)
        startKoin { modules(testPlatformModule(dirs), datasourceModule, presentationModule) }
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
        stopKoin()
        dirs.delete()
    }

    @Test
    fun newListingCanBeCreatedAndTitled() = runComposeUiTest {
        setContent { App() }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("No listings yet. Create your first one.").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("New listing").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Title").fetchSemanticsNodes().isNotEmpty()
        }
        onNode(hasSetTextAction() and hasText("Title")).performTextInput("Kinderfahrrad")

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Kinderfahrrad").fetchSemanticsNodes().size >= 2
        }
    }

    @Test
    fun settingsScreenOpens() = runComposeUiTest {
        setContent { App() }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("No listings yet. Create your first one.").fetchSemanticsNodes().isNotEmpty()
        }
        onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Defaults for new listings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun willhabenLoginIsInSettingsNotOnHome() = runComposeUiTest {
        setContent { App() }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("No listings yet. Create your first one.").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(onAllNodesWithText("Log in to willhaben").fetchSemanticsNodes()).isEmpty()
        // Home only lists the marketplaces the user is logged in to
        assertThat(onAllNodesWithContentDescription("willhaben: logged in").fetchSemanticsNodes()).isEmpty()

        onNode(hasContentDescription("Settings") and hasClickAction()).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Log in to willhaben").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
