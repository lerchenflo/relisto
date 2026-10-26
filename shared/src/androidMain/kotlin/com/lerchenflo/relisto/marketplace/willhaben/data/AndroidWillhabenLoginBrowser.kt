package com.lerchenflo.relisto.marketplace.willhaben.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.app.domain.SessionCookie
import com.lerchenflo.relisto.marketplace.willhaben.domain.LoginError
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLoginBrowser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Login in [WillhabenLoginActivity]; the WebView's willhaben cookies become the session cookies. */
class AndroidWillhabenLoginBrowser(private val context: Context) : WillhabenLoginBrowser {

    override suspend fun login(): Result<List<SessionCookie>, LoginError> {
        val loggedIn = CompletableDeferred<Boolean>()
        WillhabenLoginActivity.pending?.complete(false)
        WillhabenLoginActivity.pending = loggedIn
        try {
            val intent = Intent(context, WillhabenLoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(intent) }
        } catch (_: ActivityNotFoundException) {
            return Result.Error(LoginError.BROWSER_UNAVAILABLE)
        }
        if (!loggedIn.await()) return Result.Error(LoginError.CANCELLED)
        val header = withContext(Dispatchers.Main) {
            CookieManager.getInstance().run {
                flush()
                getCookie(COOKIE_URL)
            }
        }
        val cookies = parseCookieHeader(header, HOST)
        return if (cookies.isEmpty()) Result.Error(LoginError.NOT_LOGGED_IN) else Result.Success(cookies)
    }

    override suspend fun clearSession() {
        withContext(Dispatchers.Main) {
            WebStorage.getInstance().deleteAllData()
            suspendCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
            }
        }
    }

    private companion object {
        const val HOST = "www.willhaben.at"

        /** Deep enough that cookies limited to a path below /iad are included too. */
        const val COOKIE_URL = "https://www.willhaben.at/iad/myprofile/myadverts"
    }
}
