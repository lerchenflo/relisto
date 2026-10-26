package com.lerchenflo.relisto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.lerchenflo.relisto.activity.presentation.ActivityLogRoot
import com.lerchenflo.relisto.app.navigation.NavigationAction
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.app.navigation.ObserveAsEvents
import com.lerchenflo.relisto.app.navigation.Route
import com.lerchenflo.relisto.app.presentation.AppUpdateButtonRoot
import com.lerchenflo.relisto.app.presentation.AppUpdateRoot
import com.lerchenflo.relisto.app.presentation.AppUpdateViewModel
import com.lerchenflo.relisto.app.theme.RelistoTheme
import com.lerchenflo.relisto.listings.presentation.list.ListingsRoot
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.ObserveAccountEvents
import com.lerchenflo.relisto.settings.presentation.SettingsRoot
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val backStackConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Listings::class, Route.Listings.serializer())
            subclass(Route.Settings::class, Route.Settings.serializer())
            subclass(Route.ActivityLog::class, Route.ActivityLog.serializer())
        }
    }
}

/**
 * One root Scaffold for the whole app (same setup as SchneaggchatV3): it keeps the content clear of
 * the status and navigation bars, the camera cutout and the keyboard, and hosts the only snackbar.
 * Screens are drawn inside it and have no Scaffold of their own.
 */
@Composable
fun App() {
    RelistoTheme {
        val backStack = rememberNavBackStack(backStackConfiguration, Route.Listings)
        val navigator = koinInject<Navigator>()
        val snackbarHostState = remember { SnackbarHostState() }

        ObserveAsEvents(navigator.navigationActions) { action ->
            val options = action.navigationOptions
            if (options.exitAllPreviousScreens) {
                backStack.clear()
            } else if (options.exitPreviousScreen && backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
            when (action) {
                is NavigationAction.Navigate -> backStack.add(action.destination)
                is NavigationAction.NavigateBack -> if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
            }
        }

        // One instance for banner and header button, so GitHub is asked once per start
        val appUpdateViewModel = koinViewModel<AppUpdateViewModel>()
        // One instance for header icon and settings, so the login check and auto renew run once
        val accountViewModel = koinViewModel<AccountViewModel>()
        ObserveAccountEvents(accountViewModel, snackbarHostState)

        // Painted behind the Scaffold, so nothing bare shows while the keyboard resizes the content
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                // Content padding only (not the Scaffold itself) grows for the keyboard; no insets on desktop
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                    AppUpdateRoot(viewModel = appUpdateViewModel)
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            entry<Route.Listings> {
                                ListingsRoot(
                                    accountViewModel = accountViewModel,
                                    snackbarHostState = snackbarHostState,
                                    headerActions = { AppUpdateButtonRoot(appUpdateViewModel) },
                                )
                            }
                            entry<Route.Settings> {
                                SettingsRoot(accountViewModel = accountViewModel, snackbarHostState = snackbarHostState)
                            }
                            entry<Route.ActivityLog> { route ->
                                ActivityLogRoot(listingId = route.listingId)
                            }
                        },
                    )
                }
            }
        }
    }
}
