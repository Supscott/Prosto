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
            injectVideoGestureHandler(wv)
            SnapshotEngine.captureSnapshot(wv, tabId, wv.context) { path ->
                if (path != null) {
                    onSnapshotReady(tabId, path)
                }
            }
        }
    }

    private fun injectVideoGestureHandler(webView: WebView) {
        val js = """
            (function() {
                if (window.__jestkVideoGestureInjected) return;
                window.__jestkVideoGestureInjected = true;

                var startX = 0;
                var startY = 0;
                var lastX = 0;
                var lastY = 0;
                var startTime = 0;
                var gestureTriggered = false;

                function triggerFullscreen() {
                    if (gestureTriggered) return;
                    gestureTriggered = true;

                    // 1. Try YouTube's dedicated fullscreen button if available
                    var ytBtn = document.querySelector(
                        'button.fullscreen-icon, button.fullscreen-button, button.ytp-fullscreen-button, ' +
                        '.ytp-fullscreen-button, .fullscreen-icon, ' +
                        'button[aria-label*="Fullscreen" i], button[aria-label*="fullscreen" i], ' +
                        'button[aria-label*="Полноэкранный" i], button[aria-label*="во весь экран" i], ' +
                        'button[aria-keyshortcuts="f"]'
                    );
                    if (ytBtn) {
                        try {
                            ytBtn.click();
                            return;
                        } catch(e) {}
                    }

                    // 2. Try player container or video element
                    var player = document.getElementById('player') ||
                                 document.querySelector('#movie_player') ||
                                 document.querySelector('.player-container') ||
                                 document.querySelector('#player-control-overlay');
                    var video = document.querySelector('video.html5-main-video') ||
                                document.querySelector('video');
                    var target = player || video;

                    if (target) {
                        var req = target.requestFullscreen ||
                                  target.webkitRequestFullscreen ||
                                  target.webkitEnterFullscreen;
                        if (req) {
                            try {
                                req.call(target);
                                return;
                            } catch(e) {}
                        }
                    }

                    if (video && target !== video) {
                        var reqV = video.requestFullscreen ||
                                   video.webkitRequestFullscreen ||
                                   video.webkitEnterFullscreen;
                        if (reqV) {
                            try {
                                reqV.call(video);
                            } catch(e) {}
                        }
                    }
                }

                function isUnderVideo(y) {
                    var video = document.querySelector('video.html5-main-video') ||
                                document.querySelector('video');
                    if (!video) return false;
                    var rect = video.getBoundingClientRect();
                    return y >= rect.top;
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches && e.touches.length === 1) {
                        startX = e.touches[0].clientX;
                        startY = e.touches[0].clientY;
                        lastX = startX;
                        lastY = startY;
                        startTime = Date.now();
                        gestureTriggered = false;
                    }
                }, { passive: true });

                window.addEventListener('touchmove', function(e) {
                    if (gestureTriggered) return;
                    if (e.touches && e.touches.length === 1) {
                        lastX = e.touches[0].clientX;
                        lastY = e.touches[0].clientY;
                        var dx = lastX - startX;
                        var dy = lastY - startY;
                        var dt = Date.now() - startTime;

                        if (dy > 60 && Math.abs(dx) < dy * 1.5 && dt < 1000) {
                            var isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
                            if (!isFullscreen && isUnderVideo(startY)) {
                                if (e.cancelable) {
                                    try { e.preventDefault(); } catch(_) {}
                                }
                                triggerFullscreen();
                            }
                        }
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (gestureTriggered) return;
                    if (e.changedTouches && e.changedTouches.length === 1) {
                        var dx = e.changedTouches[0].clientX - startX;
                        var dy = e.changedTouches[0].clientY - startY;
                        var dt = Date.now() - startTime;

                        if (dy > 60 && Math.abs(dx) < dy * 1.5 && dt < 1000) {
                            var isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
                            if (!isFullscreen && isUnderVideo(startY)) {
                                triggerFullscreen();
                            }
                        }
                    }
                }, { passive: true });

                window.addEventListener('touchcancel', function(e) {
                    if (gestureTriggered) return;
                    var dx = lastX - startX;
                    var dy = lastY - startY;
                    var dt = Date.now() - startTime;

                    if (dy > 60 && Math.abs(dx) < dy * 1.5 && dt < 1000) {
                        var isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
                        if (!isFullscreen && isUnderVideo(startY)) {
                            triggerFullscreen();
                        }
                    }
                }, { passive: true });
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {}
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
            injectVideoGestureHandler(wv)
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
