package com.lerchenflo.relisto.listings.presentation.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
                snackbarHostState = SnackbarHostState(),
                marketActions = {},
                loggedInMarkets = {},
                editorContent = { Text("EDITOR") },
            )
        }
    }

    @Test
    fun wideWindowShowsListAndEditor() = runComposeUiTest {
        setContent { content(1200)() }

        assertThat(onAllNodesWithText("Kinderfahrrad").fetchSemanticsNodes()).isNotEmpty()
        assertThat(onAllNodesWithText("EDITOR").fetchSemanticsNodes()).isNotEmpty()
    }

    @Test
    fun phoneShowsTheSelectedListingAsItsOwnScreen() = runComposeUiTest {
        setContent { content(400)() }

        assertThat(onAllNodesWithText("EDITOR").fetchSemanticsNodes()).isNotEmpty()
        assertThat(onAllNodesWithText("Kinderfahrrad").fetchSemanticsNodes()).isEmpty()

        onNodeWithContentDescription("Back to the list").performClick()

        assertThat(actions).containsExactly(ListingsAction.OnCloseListingClick)
    }
}
