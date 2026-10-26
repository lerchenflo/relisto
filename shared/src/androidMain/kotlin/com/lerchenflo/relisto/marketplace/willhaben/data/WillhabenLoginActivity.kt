package com.lerchenflo.relisto.marketplace.willhaben.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.CompletableDeferred

/**
 * Shows the real willhaben login page in a WebView, so reCAPTCHA and 2FA run in a real browser engine.
 * The WebView keeps its own cookies, so a still valid "stay logged in" session finishes without the password.
 */
class WillhabenLoginActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (url.startsWith(SUCCESS_URL_PREFIX)) {
                        pending?.complete(true)
                        finish()
                    }
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        // Keeps the page clear of the status and navigation bar (edge-to-edge on Android 15+)
        setContentView(FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(webView)
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        if (savedInstanceState == null) webView.loadUrl(LOGIN_URL)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Closed without login; a rotation recreates the activity and keeps waiting
        if (isFinishing) pending?.complete(false)
    }

    companion object {
        /** Completes with true after the redirect to "my adverts", with false when the user leaves. */
        var pending: CompletableDeferred<Boolean>? = null

        private const val LOGIN_URL = "https://www.willhaben.at/iad/myprofile/login?r=/iad/myprofile/myadverts"
        private const val SUCCESS_URL_PREFIX = "https://www.willhaben.at/iad/myprofile/myadverts"
    }
}
