package com.lerchenflo.relisto.marketplace.willhaben.presentation.account

import com.lerchenflo.relisto.utilities.UiText

data class AccountState(
    val isChecking: Boolean = true,
    val isLoggedIn: Boolean = false,
    val accountName: String? = null,
    val isLoggingIn: Boolean = false,
    val isImporting: Boolean = false,
)

sealed interface AccountAction {
    data object OnLoginClick : AccountAction
    data object OnLogoutClick : AccountAction
    data object OnImportClick : AccountAction
}

sealed interface AccountEvent {
    data class ShowMessage(val message: UiText) : AccountEvent
}
