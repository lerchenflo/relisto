package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account

import com.lerchenflo.willhabenupdater.utilities.UiText

data class AccountState(
    val isChecking: Boolean = true,
    val isLoggedIn: Boolean = false,
    val accountName: String? = null,
    val isLoggingIn: Boolean = false,
)

sealed interface AccountAction {
    data object OnLoginClick : AccountAction
    data object OnLogoutClick : AccountAction
}

sealed interface AccountEvent {
    data class ShowMessage(val message: UiText) : AccountEvent
}
