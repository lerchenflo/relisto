package com.lerchenflo.relisto.listings.presentation.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Desktop shows list and editor side by side; a phone shows one of them. */
@OptIn(ExperimentalTestApi::class)
class ListingsScreenLayoutTest {

    private val originalLocale = Locale.getDefault()
    private val actions = mutableListOf<ListingsAction>()

    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun tearDown() = Locale.setDefault(originalLocale)

    private val state = ListingsState(
        isLoading = false,
        listings = listOf(ListingListItemUi(1, "Kinderfahrrad", null, null, 0, "today")),
        selectedListingId = 1,
    )

    private fun content(width: Int) = @androidx.compose.runtime.Composable {
        Box(Modifier.size(width.dp, 800.dp)) {
            ListingsScreen(
                state = state,
                onAction = { actions += it },
                marketActions = {},
                loggedInMarkets = {},
                editorContent = { _, onBack ->
                    Text("EDITOR")
                    // The phone editor draws the back arrow in its own top bar
                    if (onBack != null) IconButton(onClick = onBack) { Text("BACK") }
                },
            )
        }
    }

    @Test
    fun wideWindowShowsListAndEditor() = runComposeUiTest {
        setContent { content(1200)() }

        assertThat(onAllNodesWithText("Kinderfahrrad").fetchSemanticsNodes()).isNotEmpty()
        assertThat(onAllNodesWithText("EDITOR").fetchSemanticsNodes()).isNotEmpty()
        assertThat(onAllNodesWithText("BACK").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun phoneShowsTheSelectedListingAsItsOwnScreen() = runComposeUiTest {
        setContent { content(400)() }

        assertThat(onAllNodesWithText("EDITOR").fetchSemanticsNodes()).isNotEmpty()
        assertThat(onAllNodesWithText("Kinderfahrrad").fetchSemanticsNodes()).isEmpty()

        onNodeWithText("BACK").performClick()

        assertThat(actions).containsExactly(ListingsAction.OnCloseListingClick)
    }
}
