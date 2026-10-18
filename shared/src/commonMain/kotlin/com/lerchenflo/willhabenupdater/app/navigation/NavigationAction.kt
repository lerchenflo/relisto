package com.lerchenflo.willhabenupdater.app.navigation

sealed interface NavigationAction {
    val navigationOptions: Navigator.NavigationOptions

    data class Navigate(
        val destination: Route,
        override val navigationOptions: Navigator.NavigationOptions = Navigator.NavigationOptions()
    ) : NavigationAction

    data class NavigateBack(
        override val navigationOptions: Navigator.NavigationOptions = Navigator.NavigationOptions()
    ) : NavigationAction
}
