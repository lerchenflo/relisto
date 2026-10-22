package com.lerchenflo.relisto.activity.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.activity.domain.ActivityEntry
import com.lerchenflo.relisto.app.navigation.NavigationAction
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.testutil.FakeActivityLog
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ActivityLogViewModelTest : MainDispatcherTest() {

    private val log = FakeActivityLog().apply {
        entries.value = listOf(
            entry(3, listingId = 1, title = "Rasentraktor Kart", action = ActivityAction.DEACTIVATE, success = true),
            entry(2, listingId = 2, title = "Aprilia", action = ActivityAction.PUSH_CHANGES, success = false, message = "Conflict"),
            entry(1, listingId = 1, title = "Rasentraktor Kart", action = ActivityAction.REPUBLISH, success = true),
        )
    }
    private val navigator = Navigator()

    private fun entry(id: Long, listingId: Long, title: String, action: ActivityAction, success: Boolean, message: String? = null) =
        ActivityEntry(id, listingId, title, Marketplace.WILLHABEN, timestamp = id * 1000, action = action, success = success, message = message)

    @Test
    fun allEntriesAreShownNewestFirst() = runTest {
        val state = ActivityLogViewModel(null, log, navigator).state.value

        assertThat(state.entries.map { it.id }).containsExactly(3L, 2L, 1L)
        assertThat(state.listingTitle).isNull()
        assertThat(state.entries[1].message).isEqualTo("Conflict")
    }

    @Test
    fun opensFilteredToOneListing() = runTest {
        val state = ActivityLogViewModel(1, log, navigator).state.value

        assertThat(state.entries.map { it.id }).containsExactly(3L, 1L)
        assertThat(state.listingTitle).isEqualTo("Rasentraktor Kart")
    }

    @Test
    fun listingFilterCanBeCleared() = runTest {
        val viewModel = ActivityLogViewModel(1, log, navigator)

        viewModel.onAction(ActivityLogAction.OnClearListingFilterClick)

        assertThat(viewModel.state.value.entries.map { it.id }).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun onlyFailedEntriesCanBeShown() = runTest {
        val viewModel = ActivityLogViewModel(null, log, navigator)

        viewModel.onAction(ActivityLogAction.OnOnlyFailedChange(true))

        assertThat(viewModel.state.value.entries.map { it.id }).containsExactly(2L)
    }

    @Test
    fun backNavigatesBack() = runTest {
        val viewModel = ActivityLogViewModel(null, log, navigator)

        navigator.navigationActions.test {
            viewModel.onAction(ActivityLogAction.OnBackClick)
            assertThat(awaitItem()).isEqualTo(NavigationAction.NavigateBack())
        }
    }
}
