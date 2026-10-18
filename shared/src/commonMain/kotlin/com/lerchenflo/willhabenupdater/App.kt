package com.lerchenflo.willhabenupdater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.lerchenflo.willhabenupdater.app.navigation.NavigationAction
import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.app.navigation.ObserveAsEvents
import com.lerchenflo.willhabenupdater.app.navigation.Route
import com.lerchenflo.willhabenupdater.app.presentation.AppUpdateRoot
import com.lerchenflo.willhabenupdater.app.theme.WillhabenUpdaterTheme
import com.lerchenflo.willhabenupdater.listings.presentation.list.ListingsRoot
import com.lerchenflo.willhabenupdater.settings.presentation.SettingsRoot
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.koinInject

private val backStackConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Listings::class, Route.Listings.serializer())
            subclass(Route.Settings::class, Route.Settings.serializer())
        }
    }
}

@Composable
fun App() {
    WillhabenUpdaterTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val backStack = rememberNavBackStack(backStackConfiguration, Route.Listings)
            val navigator = koinInject<Navigator>()

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

            Column(modifier = Modifier.fillMaxSize()) {
                AppUpdateRoot()
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<Route.Listings> { ListingsRoot() }
                        entry<Route.Settings> { SettingsRoot() }
                    },
                )
            }
        }
    }
}
