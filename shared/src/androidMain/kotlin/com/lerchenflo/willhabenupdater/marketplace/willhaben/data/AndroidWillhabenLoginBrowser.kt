package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLoginBrowser

/** No willhaben login on Android yet (desktop first, see PLAN.md). */
class AndroidWillhabenLoginBrowser : WillhabenLoginBrowser {
    override suspend fun login(): Result<List<SessionCookie>, LoginError> = Result.Error(LoginError.BROWSER_UNAVAILABLE)
    override suspend fun clearSession() = Unit
}
