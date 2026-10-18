package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.ImportSummary
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.SessionState
import com.lerchenflo.willhabenupdater.testutil.FakeImporter
import com.lerchenflo.willhabenupdater.testutil.FakeLoginBrowser
import com.lerchenflo.willhabenupdater.testutil.FakeSessionRepository
import com.lerchenflo.willhabenupdater.testutil.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AccountViewModelTest : MainDispatcherTest() {

    private val sessions = FakeSessionRepository()
    private val browser = FakeLoginBrowser()
    private val importer = FakeImporter()

    private fun viewModel() = AccountViewModel(sessions, browser, importer)

    @Test
    fun storedSessionIsCheckedOnStart() = runTest {
        sessions.refreshResult = SessionState.LoggedIn(displayName = "Flo")

        val state = viewModel().state.value

        assertThat(state.isChecking).isFalse()
        assertThat(state.isLoggedIn).isTrue()
        assertThat(state.accountName).isEqualTo("Flo")
    }

    @Test
    fun missingSessionShowsLoggedOut() = runTest {
        val state = viewModel().state.value

        assertThat(state.isLoggedIn).isFalse()
    }

    @Test
    fun successfulBrowserLoginLogsIn() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(AccountAction.OnLoginClick)

        assertThat(sessions.completedLogins.single().single().name).isEqualTo("BBX_JSESSIONID")
        assertThat(viewModel.state.value.isLoggedIn).isTrue()
        assertThat(viewModel.state.value.isLoggingIn).isFalse()
    }

    @Test
    fun loginIsInProgressUntilBrowserReturns() = runTest {
        browser.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(AccountAction.OnLoginClick)
        assertThat(viewModel.state.value.isLoggingIn).isTrue()
        viewModel.onAction(AccountAction.OnLoginClick)

        browser.pending?.complete(Unit)
        assertThat(browser.loginCalls).isEqualTo(1)
        assertThat(viewModel.state.value.isLoggingIn).isFalse()
    }

    @Test
    fun closingTheBrowserIsNoError() = runTest {
        browser.result = Result.Error(LoginError.CANCELLED)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnLoginClick)
            expectNoEvents()
        }
        assertThat(viewModel.state.value.isLoggedIn).isFalse()
    }

    @Test
    fun browserThatCannotStartShowsMessage() = runTest {
        browser.result = Result.Error(LoginError.BROWSER_UNAVAILABLE)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnLoginClick)
            assertThat(awaitItem()).isInstanceOf(AccountEvent.ShowMessage::class)
        }
    }

    @Test
    fun loginNotConfirmedByWillhabenShowsMessage() = runTest {
        sessions.completeLoginResult = Result.Error(LoginError.NOT_LOGGED_IN)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnLoginClick)
            assertThat(awaitItem()).isInstanceOf(AccountEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.isLoggedIn).isFalse()
    }

    @Test
    fun logoutEndsSessionAndBrowserLogin() = runTest {
        sessions.refreshResult = SessionState.LoggedIn(displayName = null)
        val viewModel = viewModel()

        viewModel.onAction(AccountAction.OnLogoutClick)

        assertThat(sessions.logoutCalls).isEqualTo(1)
        assertThat(browser.clearCalls).isEqualTo(1)
        assertThat(viewModel.state.value.isLoggedIn).isFalse()
    }

    @Test
    fun importReportsFoundListings() = runTest {
        importer.result = Result.Success(ImportSummary(found = 7, created = 7))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnImportClick)
            assertThat(awaitItem()).isInstanceOf(AccountEvent.ShowMessage::class)
        }
        assertThat(importer.calls).isEqualTo(1)
        assertThat(viewModel.state.value.isImporting).isFalse()
    }

    @Test
    fun importIsRunningUntilFinished() = runTest {
        importer.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(AccountAction.OnImportClick)
        assertThat(viewModel.state.value.isImporting).isTrue()
        viewModel.onAction(AccountAction.OnImportClick)
        importer.pending?.complete(Unit)

        assertThat(importer.calls).isEqualTo(1)
        assertThat(viewModel.state.value.isImporting).isFalse()
    }

    @Test
    fun failedImportShowsMessage() = runTest {
        importer.result = Result.Error(DataError.Network.UNAUTHORIZED)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnImportClick)
            assertThat(awaitItem()).isInstanceOf(AccountEvent.ShowMessage::class)
        }
    }
}
