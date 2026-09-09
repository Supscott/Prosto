package com.jestk.browser.engine

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class JestkWebViewClient(
    private val tabId: String,
    private val isAdBlockerEnabled: () -> Boolean,
    private val onPageStarted: (tabId: String, url: String) -> Unit,
    private val onPageFinished: (tabId: String, url: String, title: String) -> Unit,
    private val onNavigationStateChanged: (tabId: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onSnapshotReady: (tabId: String, path: String) -> Unit,
    private val onThemeColorDetected: (tabId: String, color: Int?) -> Unit = { _, _ -> },
    private val isDarkTheme: (() -> Boolean)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        if (isAdBlockerEnabled()) {
            val url = request.url?.toString()
            if (AdBlockerEngine.shouldBlock(url)) {
                return AdBlockerEngine.createBlockedResponse()
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val validUrl = url.orEmpty()
        (view as? NestedWebView)?.loadedUrl = validUrl
        onPageStarted(tabId, validUrl)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            isDarkTheme?.invoke()?.let { isDark -> injectColorScheme(wv, isDark) }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val validUrl = url.orEmpty()
        (view as? NestedWebView)?.loadedUrl = validUrl
        val title = view?.title.orEmpty()
        onPageFinished(tabId, validUrl, title)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            isDarkTheme?.invoke()?.let { isDark -> injectColorScheme(wv, isDark) }
            extractThemeColor(wv)
            SnapshotEngine.captureSnapshot(wv, tabId, wv.context) { path ->
                if (path != null) {
                    onSnapshotReady(tabId, path)
                }
            }
        }
    }

    private fun injectColorScheme(webView: WebView, isDark: Boolean) {
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

    private fun extractThemeColor(webView: WebView) {
        val js = """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="theme-color"]');
                    if (meta && meta.content) {
                        var c = meta.content.trim();
                        if (c) return c;
                    }
                    function getBg(el) {
                        if (!el) return null;
                        var style = window.getComputedStyle(el);
                        var bg = style ? style.backgroundColor : null;
                        if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'rgba(0,0,0,0)') {
                            return bg.trim();
                        }
                        return null;
                    }
                    return getBg(document.body) || getBg(document.documentElement) || '';
                } catch(e) {
                    return '';
                }
            })()
        """.trimIndent()
        webView.evaluateJavascript(js) { rawResult ->
            val parsedColor = parseCssColor(rawResult)
            onThemeColorDetected(tabId, parsedColor)
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
        }
    }

    companion object {
        fun parseCssColor(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().removeSurrounding("\"").trim()
            if (clean.isEmpty() || clean == "null" || clean.equals("transparent", ignoreCase = true) ||
                clean == "rgba(0, 0, 0, 0)" || clean == "rgba(0,0,0,0)") {
                return null
            }
            if (clean.startsWith("#")) {
                return try {
                    val hex = clean.substring(1)
                    when (hex.length) {
                        3 -> {
                            val r = hex[0].toString().repeat(2).toInt(16)
                            val g = hex[1].toString().repeat(2).toInt(16)
                            val b = hex[2].toString().repeat(2).toInt(16)
                            android.graphics.Color.rgb(r, g, b)
                        }
                        4 -> {
                            val r = hex[0].toString().repeat(2).toInt(16)
                            val g = hex[1].toString().repeat(2).toInt(16)
                            val b = hex[2].toString().repeat(2).toInt(16)
                            val a = hex[3].toString().repeat(2).toInt(16)
                            android.graphics.Color.argb(a, r, g, b)
                        }
                        6 -> android.graphics.Color.parseColor(clean)
                        8 -> {
                            // CSS hex #RRGGBBAA -> Android Color expects #AARRGGBB
                            val rr = clean.substring(1, 3).toInt(16)
                            val gg = clean.substring(3, 5).toInt(16)
                            val bb = clean.substring(5, 7).toInt(16)
                            val aa = clean.substring(7, 9).toInt(16)
                            android.graphics.Color.argb(aa, rr, gg, bb)
                        }
                        else -> null
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (clean.startsWith("rgb", ignoreCase = true)) {
                return try {
                    val content = clean.substringAfter("(").substringBefore(")")
                    val parts = content.split(",").map { it.trim() }
                    if (parts.size >= 3) {
                        val r = parts[0].toInt().coerceIn(0, 255)
                        val g = parts[1].toInt().coerceIn(0, 255)
                        val b = parts[2].toInt().coerceIn(0, 255)
                        val a = if (parts.size >= 4) {
                            val alphaFloat = parts[3].toFloatOrNull() ?: 1f
                            if (alphaFloat <= 1.0f) {
                                (alphaFloat * 255).toInt().coerceIn(0, 255)
                            } else {
                                alphaFloat.toInt().coerceIn(0, 255)
                            }
                        } else 255
                        if (a == 0) null else android.graphics.Color.argb(a, r, g, b)
                    } else null
                } catch (_: Throwable) {
                    null
                }
            }
            return null
        }
    }
}
