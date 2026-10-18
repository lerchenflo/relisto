package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.app.domain.SessionCookie
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.LoginError
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import dev.datlag.kcef.KCEFCookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.network.CefCookie
import org.jetbrains.compose.resources.getString
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.login_window_downloading
import willhabenupdater.shared.generated.resources.login_window_preparing
import willhabenupdater.shared.generated.resources.login_window_title
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * Login in an embedded Chromium (KCEF) window showing the real willhaben login page, so
 * fingerprinting, reCAPTCHA and 2FA work like in a normal browser. The Chromium bundle is
 * downloaded once into the app data folder; its cookie store is persistent, so "stay logged in"
 * lets later logins finish without typing the password.
 */
class KcefWillhabenLoginBrowser(
    appDirectories: AppDirectories,
) : WillhabenLoginBrowser {

    private val installDir = File(appDirectories.dataDir, "kcef-bundle")
    private val cacheDir = File(appDirectories.dataDir, "kcef-cache")
    private val initMutex = Mutex()
    private var initialized = false

    override suspend fun login(): Result<List<SessionCookie>, LoginError> {
        // JetBrains Runtime's own JCEF module shadows the KCEF classes and crashes the JVM on start
        if (ModuleLayer.boot().findModule("jcef").isPresent) return Result.Error(LoginError.UNSUPPORTED_RUNTIME)
        val window = withContext(Dispatchers.Main) {
            LoginWindow(getString(Res.string.login_window_title), getString(Res.string.login_window_preparing))
        }
        try {
            if (!ensureInitialized { percent -> window.showStatus(getString(Res.string.login_window_downloading, percent)) }) {
                return Result.Error(LoginError.BROWSER_UNAVAILABLE)
            }
            val loggedIn = withContext(Dispatchers.Main) { window.openBrowser(LOGIN_URL) }
            if (!loggedIn.await()) return Result.Error(LoginError.CANCELLED)

            val cookies = KCEFCookieManager.instance.getCookiesWhile()
                .filter { it.domain.trimStart('.').endsWith(WILLHABEN_DOMAIN) }
                .map { it.toSessionCookie() }
            return if (cookies.isEmpty()) Result.Error(LoginError.NOT_LOGGED_IN) else Result.Success(cookies)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            return Result.Error(LoginError.BROWSER_UNAVAILABLE)
        } finally {
            withContext(Dispatchers.Main) { window.close() }
        }
    }

    override suspend fun clearSession() {
        if (initialized) {
            KCEFCookieManager.instance.deleteAllCookies()
        } else {
            // Chromium is not running, so its stored cookies can be removed with the cache folder
            withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }
        }
    }

    /** Downloads (first time only) and starts Chromium. False when it cannot run on this system. */
    private suspend fun ensureInitialized(onDownloadProgress: suspend (Int) -> Unit): Boolean = initMutex.withLock {
        if (initialized) return@withLock true
        var failed = false
        var lastPercent = -1
        withContext(Dispatchers.IO) {
            KCEF.init(
                builder = {
                    installDir(installDir)
                    progress {
                        onDownloading { progress ->
                            val percent = progress.roundToInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                kotlinx.coroutines.runBlocking { onDownloadProgress(percent) }
                            }
                        }
                    }
                    settings {
                        cachePath = cacheDir.absolutePath
                        persistSessionCookies = true
                    }
                },
                onError = { error ->
                    error?.printStackTrace()
                    failed = true
                },
                onRestartRequired = { failed = true },
            )
        }
        initialized = !failed
        initialized
    }

    private fun CefCookie.toSessionCookie() = SessionCookie(
        name = name,
        value = value,
        domain = domain.trimStart('.'),
        hostOnly = !domain.startsWith("."),
        path = path.ifEmpty { "/" },
        secure = secure,
        httpOnly = httponly,
        expiresAt = if (hasExpires) expires?.time else null,
    )

    /** Swing window with a status line that is replaced by the browser once Chromium is ready. */
    private class LoginWindow(title: String, status: String) {
        private val frame = JFrame(title)
        private val statusLabel = JLabel(status, SwingConstants.CENTER)
        private val closed = CompletableDeferred<Boolean>()
        private var client: KCEFClient? = null
        private var browser: KCEFBrowser? = null

        init {
            frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            frame.contentPane.add(statusLabel, BorderLayout.CENTER)
            frame.size = Dimension(520, 760)
            frame.setLocationRelativeTo(null)
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    closed.complete(false)
                }
            })
            frame.isVisible = true
        }

        suspend fun showStatus(text: String) = withContext(Dispatchers.Main) {
            statusLabel.text = text
        }

        /** Shows the login page; the result is true once willhaben redirected to "my adverts". */
        fun openBrowser(url: String): CompletableDeferred<Boolean> {
            val newClient = KCEF.newClientBlocking()
            newClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                    if (url != null && url.startsWith(SUCCESS_URL_PREFIX)) closed.complete(true)
                }
            })
            val newBrowser = newClient.createBrowser(url, CefRendering.DEFAULT, false)
            client = newClient
            browser = newBrowser
            frame.contentPane.remove(statusLabel)
            frame.contentPane.add(newBrowser.uiComponent, BorderLayout.CENTER)
            frame.revalidate()
            return closed
        }

        fun close() {
            browser?.dispose()
            client?.dispose()
            frame.dispose()
        }
    }

    private companion object {
        const val WILLHABEN_DOMAIN = "willhaben.at"
        const val LOGIN_URL = "https://www.willhaben.at/iad/myprofile/login?r=/iad/myprofile/myadverts"
        const val SUCCESS_URL_PREFIX = "https://www.willhaben.at/iad/myprofile/myadverts"
    }
}
