package com.lerchenflo.relisto.listings.presentation.editor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Desktop shows labelled buttons; a phone shows a top bar with back arrow and icon buttons. */
@OptIn(ExperimentalTestApi::class)
class ListingEditorScreenLayoutTest {

    private val originalLocale = Locale.getDefault()
    private val actions = mutableListOf<ListingEditorAction>()
    private var backClicks = 0

    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun tearDown() = Locale.setDefault(originalLocale)

    private val state = ListingEditorState(isLoading = false, title = "Kinderfahrrad")

    @Test
    fun desktopShowsLabelledButtonsWithoutBackArrow() = runComposeUiTest {
        setContent { ListingEditorScreen(state = state, onAction = { actions += it }, marketplaceContent = {}) }

        onNodeWithText("Duplicate").performClick()
        assertThat(actions).containsExactly(ListingEditorAction.OnDuplicateClick)
        assertThat(onAllNodesWithContentDescription("Back to the list").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun phoneShowsBackArrowAndIconButtons() = runComposeUiTest {
        setContent {
            ListingEditorScreen(state = state, onAction = { actions += it }, marketplaceContent = {}, onBack = { backClicks++ })
        }

        assertThat(onAllNodesWithText("Duplicate").fetchSemanticsNodes()).isEmpty()
        onNodeWithContentDescription("Back to the list").performClick()
        onNodeWithContentDescription("Duplicate").performClick()
        onNodeWithContentDescription("Archive").performClick()
        onNodeWithContentDescription("Delete").performClick()

        assertThat(backClicks).isEqualTo(1)
        assertThat(actions).containsExactly(
            ListingEditorAction.OnDuplicateClick,
            ListingEditorAction.OnArchiveToggleClick,
            ListingEditorAction.OnDeleteClick,
        )
    }
}
