package com.jestk.browser.engine

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JestkWebViewClient(
    private val tabId: String,
    private val isAdBlockerEnabled: () -> Boolean,
    private val onPageStarted: (tabId: String, url: String) -> Unit,
    private val onPageFinished: (tabId: String, url: String, title: String) -> Unit,
    private val onNavigationStateChanged: (tabId: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onSnapshotReady: (tabId: String, path: String) -> Unit,
    private val onThemeColorDetected: (tabId: String, color: Int?) -> Unit = { _, _ -> },
    private val isDarkTheme: (() -> Boolean)? = null,
    private val onFaviconReceived: ((tabId: String, icon: Bitmap) -> Unit)? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val onBlockedResource: ((url: String, isTracker: Boolean) -> Unit)? = null,
    private val onTitleReceived: ((tabId: String, title: String) -> Unit)? = null,
    private val isOpenInAppsEnabled: (() -> Boolean)? = null,
    private val onExternalAppOffered: ((tabId: String, prompt: ExternalAppPrompt) -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        if (request == null) return false
        if (!request.isForMainFrame) return false
        val url = request.url?.toString().orEmpty()
        return handleUrlLoading(view, url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val validUrl = url.orEmpty()
        if (validUrl.isBlank()) return false
        return handleUrlLoading(view, validUrl)
    }

    private fun handleUrlLoading(view: WebView?, url: String): Boolean {
        val isEnabled = isOpenInAppsEnabled?.invoke() ?: true
        if (!isEnabled) {
            val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null }
            val scheme = uri?.scheme?.lowercase().orEmpty()
            return if (scheme == "http" || scheme == "https") {
                false
            } else {
                true
            }
        }

        val context = view?.context ?: return false
        return when (val resolution = ExternalAppResolver.resolve(context, url, tabId)) {
            is ExternalAppResolution.Offer -> {
                onExternalAppOffered?.invoke(tabId, resolution.prompt)
                resolution.isCustomScheme
            }
            is ExternalAppResolution.UnresolvableScheme -> {
                true
            }
            is ExternalAppResolution.NoApp -> {
                false
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        if (isAdBlockerEnabled()) {
            val url = request.url?.toString()
            if (AdBlockerEngine.shouldBlock(url)) {
                val validUrl = url.orEmpty()
                val isTracker = AdBlockerEngine.isTracker(validUrl)
                onBlockedResource?.invoke(validUrl, isTracker)
                if (settingsRepository != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isTracker) {
                            settingsRepository.incrementBlockedTrackers()
                        } else {
                            settingsRepository.incrementBlockedAds()
                        }
                    }
                } else {
                    AdBlockerEngine.recordBlockedRequest(validUrl)
                }
                return AdBlockerEngine.createSmartBlockedResponse(
                    url = url,
                    requestHeaders = request.requestHeaders
                )
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun checkExternalAppOffer(view: WebView?, url: String) {
        if (isOpenInAppsEnabled?.invoke() == false) return
        val context = view?.context ?: return
        val resolution = ExternalAppResolver.resolve(context, url, tabId)
        if (resolution is ExternalAppResolution.Offer && !resolution.isCustomScheme) {
            onExternalAppOffered?.invoke(tabId, resolution.prompt)
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return
        (view as? NestedWebView)?.loadedUrl = validUrl
        favicon?.let { onFaviconReceived?.invoke(tabId, it) }
        onPageStarted(tabId, validUrl)
        view?.let { wv ->
            checkExternalAppOffer(wv, validUrl)
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            val isDark = isDarkTheme?.invoke() ?: false
            injectColorScheme(wv, isDark)
            if (isAdBlockerEnabled()) {
                AdBlockerScript.injectAdBlocker(wv)
            }
            if (validUrl.contains("apple.com", ignoreCase = true)) {
                onThemeColorDetected(tabId, android.graphics.Color.parseColor("#161617"))
            } else if (validUrl.contains("youtube.com", ignoreCase = true) && isDark) {
                onThemeColorDetected(tabId, android.graphics.Color.parseColor("#0f0f0f"))
            } else {
                extractThemeColor(wv, isPageStarted = true)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return
        (view as? NestedWebView)?.loadedUrl = validUrl
        val title = view?.title.orEmpty()
        view?.favicon?.let { onFaviconReceived?.invoke(tabId, it) }
        onPageFinished(tabId, validUrl, title)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            isDarkTheme?.invoke()?.let { isDark -> injectColorScheme(wv, isDark) }
            if (isAdBlockerEnabled()) {
                AdBlockerScript.injectAdBlocker(wv)
            }
            extractThemeColor(wv, isPageStarted = false)
            SnapshotEngine.captureSnapshot(
                webView = wv,
                tabId = tabId,
                context = wv.context,
                onVisualColorsSampled = { topColor, _ ->
                    if (!validUrl.contains("apple.com", ignoreCase = true)) {
                        onThemeColorDetected(tabId, topColor)
                    }
                }
            ) { path ->
                if (path != null) {
                    onSnapshotReady(tabId, path)
                }
            }
        }
    }

    private fun injectColorScheme(webView: WebView, isDark: Boolean) {
        val js = buildColorSchemeScript(isDark)
        try {
            webView.evaluateJavascript(js, null)
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to evaluate color scheme script in client", t)
        }
    }

    fun extractThemeColor(webView: WebView, isPageStarted: Boolean = false) {
        val js = """
            (function() {
                try {
                    var host = location.hostname || '';
                    if (host.indexOf('apple.com') !== -1) {
                        return '#161617';
                    }
                    if (host.indexOf('youtube.com') !== -1) {
                        var isDark = document.documentElement.hasAttribute('dark') || 
                                     document.documentElement.getAttribute('dark') === 'true' ||
                                     document.querySelector('ytd-app[dark], ytm-app[dark]');
                        return isDark ? '#0f0f0f' : '#ffffff';
                    }

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

                    var headerSelectors = [
                        '#globalnav',
                        '#ac-globalnav',
                        'header',
                        'nav',
                        '[role="banner"]',
                        '.navbar',
                        '#navbar',
                        '.header',
                        '#header',
                        '.top-bar',
                        '#top-bar',
                        '[data-header]'
                    ];
                    for (var i = 0; i < headerSelectors.length; i++) {
                        var headerEl = document.querySelector(headerSelectors[i]);
                        var headerBg = getBg(headerEl);
                        if (headerBg) return headerBg;
                    }

                    try {
                        var topEl = document.elementFromPoint(window.innerWidth / 2, 10);
                        var curr = topEl;
                        while (curr && curr !== document.documentElement && curr !== document.body) {
                            var topBg = getBg(curr);
                            if (topBg) return topBg;
                            curr = curr.parentElement;
                        }
                    } catch(e) {}

                    var appBg = getBg(document.querySelector('ytm-app')) ||
                                getBg(document.querySelector('ytd-app')) ||
                                getBg(document.querySelector('#app')) ||
                                getBg(document.querySelector('#root')) ||
                                getBg(document.querySelector('main'));
                    if (appBg) return appBg;

                    if (!$isPageStarted) {
                        var bodyBg = getBg(document.body) || getBg(document.documentElement);
                        if (bodyBg) return bodyBg;
                    }
                    return '';
                } catch(e) {
                    return '';
                }
            })()
        """.trimIndent()
        webView.evaluateJavascript(js) { rawResult ->
            val parsedColor = parseCssColor(rawResult)
            if (parsedColor != null) {
                onThemeColorDetected(tabId, parsedColor)
            }
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        val validUrl = url.orEmpty()
        if (validUrl.isNotBlank() && validUrl != "about:blank") {
            (view as? NestedWebView)?.loadedUrl = validUrl
            onPageStarted(tabId, validUrl)
            checkExternalAppOffer(view, validUrl)
            val title = view?.title
            if (!title.isNullOrBlank()) {
                onTitleReceived?.invoke(tabId, title)
            }
        }
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
                            if (a < 25) null else android.graphics.Color.rgb(r, g, b)
                        }
                        6 -> android.graphics.Color.parseColor(clean)
                        8 -> {
                            // CSS hex #RRGGBBAA -> Android Color expects #AARRGGBB
                            val rr = clean.substring(1, 3).toInt(16)
                            val gg = clean.substring(3, 5).toInt(16)
                            val bb = clean.substring(5, 7).toInt(16)
                            val aa = clean.substring(7, 9).toInt(16)
                            if (aa < 25) null else android.graphics.Color.rgb(rr, gg, bb)
                        }
                        else -> null
                    }
                } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to parse CSS hex color: $clean", t)
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
                        if (a < 25) null else android.graphics.Color.rgb(r, g, b)
                    } else null
                } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to parse CSS rgb color: $clean", t)
                    null
                }
            }
            return null
        }
    }
}
