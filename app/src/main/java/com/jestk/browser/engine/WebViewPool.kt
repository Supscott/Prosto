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

    private fun syncConfigurationUiMode(context: Context, isDark: Boolean) {
        try {
            val res = context.resources
            val config = res.configuration
            val targetNight = if (isDark) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            } else {
                android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            if ((config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != targetNight) {
                val updatedConfig = android.content.res.Configuration(config).apply {
                    uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or targetNight
                }
                @Suppress("DEPRECATION")
                res.updateConfiguration(updatedConfig, res.displayMetrics)
            }
        } catch (_: Throwable) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String, context: Context? = null): WebView {
        val targetContext = context?.findActivity() ?: context ?: applicationContext
        currentIsDark?.let { syncConfigurationUiMode(targetContext, it) }
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

            injectColorSchemeScript(webView, isDark)
        } catch (_: Throwable) {}
    }

    fun injectColorSchemeScript(webView: WebView, isDark: Boolean) {
        val js = """
            (function() {
                try {
                    var dark = $isDark;
                    window.__jestkIsDark = dark;
                    if (!window.__jestkThemeListeners) {
                        window.__jestkThemeListeners = [];
                    }

                    if (!window.__jestkOrigMatchMedia) {
                        window.__jestkOrigMatchMedia = window.matchMedia;
                    }
                    var origMatchMedia = window.__jestkOrigMatchMedia;

                    window.matchMedia = function(q) {
                        if (q && q.indexOf('prefers-color-scheme') !== -1) {
                            var isDarkQuery = q.indexOf('dark') !== -1;
                            var currentDark = window.__jestkIsDark;
                            var matches = isDarkQuery ? currentDark : !currentDark;
                            return {
                                matches: matches,
                                media: q,
                                onchange: null,
                                addListener: function(fn) {
                                    if (typeof fn === 'function' && window.__jestkThemeListeners.indexOf(fn) === -1) {
                                        window.__jestkThemeListeners.push(fn);
                                    }
                                },
                                removeListener: function(fn) {
                                    var idx = window.__jestkThemeListeners.indexOf(fn);
                                    if (idx !== -1) window.__jestkThemeListeners.splice(idx, 1);
                                },
                                addEventListener: function(type, fn) {
                                    if (type === 'change' && typeof fn === 'function' && window.__jestkThemeListeners.indexOf(fn) === -1) {
                                        window.__jestkThemeListeners.push(fn);
                                    }
                                },
                                removeEventListener: function(type, fn) {
                                    if (type === 'change') {
                                        var idx = window.__jestkThemeListeners.indexOf(fn);
                                        if (idx !== -1) window.__jestkThemeListeners.splice(idx, 1);
                                    }
                                },
                                dispatchEvent: function() { return false; }
                            };
                        }
                        return origMatchMedia ? origMatchMedia.call(window, q) : null;
                    };

                    var evt = { matches: dark, media: '(prefers-color-scheme: dark)' };
                    window.__jestkThemeListeners.forEach(function(fn) {
                        try { fn(evt); } catch(e) {}
                    });

                    if (location.hostname.indexOf('youtube.com') !== -1) {
                        if (dark) {
                            if (document.documentElement) document.documentElement.setAttribute('dark', 'true');
                            var app = document.querySelector('ytd-app, ytm-app');
                            if (app) app.setAttribute('dark', 'true');
                        } else {
                            if (document.documentElement) document.documentElement.removeAttribute('dark');
                            var app = document.querySelector('ytd-app, ytm-app');
                            if (app) app.removeAttribute('dark');
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {}
    }

    fun updateThemeMode(isDark: Boolean, context: Context? = null) {
        currentIsDark = isDark
        context?.let { syncConfigurationUiMode(it, isDark) }
        syncConfigurationUiMode(applicationContext, isDark)
        pool.values.forEach { webView ->
            val wrapper = webView.context as? MutableContextWrapper
            wrapper?.baseContext?.let { syncConfigurationUiMode(it, isDark) }
            try {
                webView.dispatchConfigurationChanged(webView.context.resources.configuration)
            } catch (_: Throwable) {}
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
