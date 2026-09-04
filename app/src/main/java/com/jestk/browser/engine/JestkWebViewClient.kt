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
    private val onThemeColorDetected: (tabId: String, color: Int?) -> Unit = { _, _ -> }
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
        view?.let {
            onNavigationStateChanged(tabId, it.canGoBack(), it.canGoForward())
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
            extractThemeColor(wv)
            SnapshotEngine.captureSnapshot(wv, tabId, wv.context) { path ->
                if (path != null) {
                    onSnapshotReady(tabId, path)
                }
            }
        }
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
