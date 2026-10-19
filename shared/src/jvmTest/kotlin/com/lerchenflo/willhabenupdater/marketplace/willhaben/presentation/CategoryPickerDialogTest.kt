package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CategoryPickerDialogTest {

    private val originalLocale = Locale.getDefault()
    private val actions = mutableListOf<WillhabenTabAction>()

    @BeforeTest
    fun setUp() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun tearDown() = Locale.setDefault(originalLocale)

    private val browsing = WillhabenTabState(
        isTreeLoading = false,
        isCategoryPickerOpen = true,
        categoryBreadcrumb = listOf(CategoryNodeUi(4390, "Sport / Sportgeräte", hasChildren = true)),
        categoryLevel = listOf(
            CategoryNodeUi(4552, "Fahrräder", hasChildren = false),
            CategoryNodeUi(4400, "Wintersport", hasChildren = true),
        ),
    )

    @Test
    fun categoryWithChildrenOpensAndLeafIsSelected() = runComposeUiTest {
        setContent { WillhabenTabScreen(state = browsing, onAction = { actions += it }) }

        onNodeWithText("Wintersport").performClick()
        onNodeWithText("Fahrräder").performClick()

        assertThat(actions).containsExactly(
            WillhabenTabAction.OnCategoryBrowse(4400),
            WillhabenTabAction.OnCategorySelect(4552),
        )
    }

    @Test
    fun breadcrumbLeadsBackToTheTopLevel() = runComposeUiTest {
        setContent { WillhabenTabScreen(state = browsing, onAction = { actions += it }) }

        onNodeWithText("All categories").performClick()

        assertThat(actions).containsExactly(WillhabenTabAction.OnCategoryBreadcrumbClick(null))
    }
}
