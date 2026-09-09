package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.ConcurrentHashMap

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class WebViewPool(private val applicationContext: Context) {

    private val pool = ConcurrentHashMap<String, WebView>()
    private var currentIsDark: Boolean? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String, context: Context? = null): WebView {
        val targetContext = context?.findActivity() ?: context ?: applicationContext
        val existing = pool[tabId]
        if (existing != null) {
            (existing.context as? MutableContextWrapper)?.baseContext = targetContext
            currentIsDark?.let { applyThemeMode(existing, it) }
            return existing
        }

        // Initialize with MutableContextWrapper using Activity context so Chromium can attach to Window
        // and display the soft keyboard when input fields are focused.
        val contextWrapper = MutableContextWrapper(targetContext)
        val webView = NestedWebView(contextWrapper).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
            }
            // Do NOT call pauseTimers() or onPause() when tabs are backgrounded,
            // keeping audio and video playback alive across tab switches.
        }

        currentIsDark?.let { applyThemeMode(webView, it) }

        pool[tabId] = webView
        return webView
    }

    fun applyThemeMode(webView: WebView, isDark: Boolean) {
        val settings = webView.settings
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                )
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    settings,
                    if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
        } catch (_: Throwable) {}
    }

    fun updateThemeMode(isDark: Boolean) {
        currentIsDark = isDark
        pool.values.forEach { webView ->
            applyThemeMode(webView, isDark)
        }
    }

    fun detachFromParent(tabId: String) {
        pool[tabId]?.let { detachFromParent(it) }
    }

    fun detachFromParent(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    fun getWebView(tabId: String): WebView? = pool[tabId]

    fun removeWebView(tabId: String) {
        val webView = pool.remove(tabId) ?: return
        detachFromParent(webView)
        (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView as? NestedWebView)?.loadedUrl = null
            webView.clearHistory()
            webView.destroy()
        } catch (_: Exception) {}
    }

    fun retainTabs(activeTabIds: Set<String>) {
        val toRemove = pool.keys().toList().filter { it !in activeTabIds }
        toRemove.forEach { removeWebView(it) }
    }

    fun clearAll() {
        pool.keys().toList().forEach { removeWebView(it) }
    }

    fun size(): Int = pool.size
}
