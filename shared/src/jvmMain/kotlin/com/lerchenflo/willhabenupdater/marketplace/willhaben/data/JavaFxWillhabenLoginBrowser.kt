package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.datasource.network.PersistentCookiesStorage
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.login_window_title
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * Login in a JavaFX WebView window showing the real willhaben login page, so fingerprinting,
 * reCAPTCHA and 2FA run in a real browser engine. The stored session cookies are preloaded, so a
 * still valid "stay logged in" session finishes the login without typing the password.
 */
class JavaFxWillhabenLoginBrowser(
    appDirectories: AppDirectories,
    private val cookiesStorage: PersistentCookiesStorage,
    private val timeProvider: TimeProvider,
) : WillhabenLoginBrowser {

    private val browserDataDir = File(appDirectories.dataDir, "login-browser")

    override suspend fun login(): Result<List<SessionCookie>, LoginError> {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        cookieManager.cookieStore.addSessionCookies(cookiesStorage.allCookies(), timeProvider.nowEpochMillis())
        // WebKit in JavaFX reads and writes all cookies through the default handler
        CookieHandler.setDefault(cookieManager)

        val loggedIn = CompletableDeferred<Boolean>()
        val title = getString(Res.string.login_window_title)
        val window = try {
            withContext(Dispatchers.Main) { LoginWindow(title, browserDataDir, loggedIn) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            return Result.Error(LoginError.BROWSER_UNAVAILABLE)
        }
        try {
            if (!loggedIn.await()) return Result.Error(LoginError.CANCELLED)
            val cookies = cookieManager.cookieStore.willhabenSessionCookies(timeProvider.nowEpochMillis())
            return if (cookies.isEmpty()) Result.Error(LoginError.NOT_LOGGED_IN) else Result.Success(cookies)
        } finally {
            withContext(Dispatchers.Main) { window.close() }
        }
    }

    override suspend fun clearSession() {
        withContext(Dispatchers.IO) { browserDataDir.deleteRecursively() }
    }

    /** Swing window hosting the WebView; [loggedIn] completes with true after the redirect to "my adverts". */
    private class LoginWindow(
        title: String,
        dataDir: File,
        private val loggedIn: CompletableDeferred<Boolean>,
    ) {
        private val frame = JFrame(title)
        private val panel = JFXPanel()

        init {
            Platform.setImplicitExit(false)
            frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            frame.contentPane.add(panel, BorderLayout.CENTER)
            frame.size = Dimension(520, 780)
            frame.setLocationRelativeTo(null)
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    loggedIn.complete(false)
                }
            })
            Platform.runLater {
                val webView = WebView()
                val engine = webView.engine
                engine.userDataDirectory = dataDir
                engine.locationProperty().addListener { _, _, url ->
                    if (url != null && url.startsWith(SUCCESS_URL_PREFIX)) loggedIn.complete(true)
                }
                panel.scene = Scene(webView)
                engine.load(LOGIN_URL)
            }
            frame.isVisible = true
        }

        fun close() {
            frame.dispose()
        }
    }

    private companion object {
        const val LOGIN_URL = "https://www.willhaben.at/iad/myprofile/login?r=/iad/myprofile/myadverts"
        const val SUCCESS_URL_PREFIX = "https://www.willhaben.at/iad/myprofile/myadverts"
    }
}
