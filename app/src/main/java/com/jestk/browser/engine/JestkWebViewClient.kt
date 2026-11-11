package com.jestk.browser.engine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewFeature
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.util.UrlSecurityUtils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class JestkWebViewClient(
    private val tabId: String,
    private val isAdBlockerEnabled: () -> Boolean,
    private val onPageStarted: (tabId: String, url: String) -> Unit,
    private val onPageFinished: (tabId: String, url: String, title: String) -> Unit,
    private val onNavigationStateChanged: (tabId: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onSnapshotReady: (tabId: String, path: String) -> Unit,
    private val onThemeColorDetected: (tabId: String, color: Int?) -> Unit = { _, _ -> },
    private val onThemeColorsDetected: ((tabId: String, topColor: Int?, bottomColor: Int?) -> Unit)? = null,
    private val isDarkTheme: (() -> Boolean)? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val onBlockedResource: ((url: String, isTracker: Boolean) -> Unit)? = null,
    private val onTitleReceived: ((tabId: String, title: String) -> Unit)? = null,
    private val isOpenInAppsEnabled: (() -> Boolean)? = null,
    private val onExternalAppOffered: ((tabId: String, prompt: ExternalAppPrompt, userInitiated: Boolean) -> Unit)? = null,
    private val isTrackingProtectionEnabled: (() -> Boolean)? = null,
    private val onInsecureHttpWarning: ((tabId: String, url: String) -> Unit)? = null,
    private val isInsecureHostAllowed: ((host: String) -> Boolean)? = null,
    private val isCleanPlayerEnabled: (() -> Boolean)? = null,
    private val onCleanPlayerRequested: ((url: String) -> Boolean)? = null,
    private val onBlockedAdNavigation: ((tabId: String, url: String) -> Unit)? = null,
    private val isAdRedirectProtectionEnabled: (() -> Boolean)? = null,
    private val isAllowedAdNavigation: ((url: String) -> Boolean)? = null,
    private val isAutoConsentEnabled: (() -> Boolean)? = null,
    private val onRenderProcessGone: ((tabId: String) -> Unit)? = null,
    /** Runs the PackageManager lookups for web URLs; they must not block the UI thread. */
    private val externalAppExecutor: Executor = EXTERNAL_APP_EXECUTOR,
) : WebViewClient() {

    @Volatile
    private var lastGestureNavigationAtMs = Long.MIN_VALUE / 2

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        Log.e("JestkWebViewClient", "Chromium render process gone for tab $tabId (didCrash=${detail?.didCrash()})")
        // The WebView is unusable now. The pool destroys it and forgets it, so the next
        // activation of the tab creates a fresh WebView and reloads the tab URL.
        val handler = onRenderProcessGone
        if (handler != null) {
            handler(tabId)
        } else {
            (view?.parent as? android.view.ViewGroup)?.removeView(view)
            view?.destroy()
        }
        return true
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        if (request == null) return false
        val url = request.url?.toString().orEmpty()
        if (url.isBlank()) return false

        // YouTube Clean Player interceptor for main frame navigations
        if (request.isForMainFrame && isCleanPlayerEnabled?.invoke() == true) {
            val videoId =
                com.jestk.browser.util.YouTubeUrlHelper
                    .extractVideoId(url)
            if (videoId != null && onCleanPlayerRequested?.invoke(url) == true) {
                return true
            }
        }

        // Subframe web navigations must load normally; subframe custom schemes are the classic
        // hidden-iframe "Open in app" technique and still need to reach the resolver.
        if (!request.isForMainFrame && isWebUrl(url)) return false

        val hasOpenerGesture = (view as? NestedWebView)?.hasOpenerUserGesture == true
        val effectiveGesture = request.hasGesture() || hasOpenerGesture

        // "Open app" buttons usually bounce through tracking redirects (Reddit: onelink.me) whose
        // later hops no longer carry the tap's gesture flag. Hops shortly after a tap still count
        // as that tap, so the final intent:// handoff is recognised as deliberate.
        val now = android.os.SystemClock.elapsedRealtime()
        if (request.isForMainFrame && effectiveGesture) lastGestureNavigationAtMs = now
        val continuesGesture = effectiveGesture ||
            (request.isForMainFrame && now - lastGestureNavigationAtMs <= GESTURE_CHAIN_WINDOW_MS)

        if (request.isForMainFrame && isBlockedAdNavigation(url, view, effectiveGesture, request.isRedirect)) return true

        // Warn before loading cleartext HTTP websites in the main frame
        if (request.isForMainFrame && checkInsecureHttpNavigation(url)) return true

        return handleExternalNavigation(
            view = view,
            url = url,
            allowFallbackNavigation = request.isForMainFrame,
            userInitiated = continuesGesture,
        )
    }

    private fun isStaticAsset(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
            clean.endsWith(".gif") || clean.endsWith(".webp") || clean.endsWith(".svg") ||
            clean.endsWith(".ico") || clean.endsWith(".mp4") || clean.endsWith(".webm") ||
            clean.endsWith(".mp3") || clean.endsWith(".ogg") || clean.endsWith(".m4a") ||
            clean.endsWith(".m3u8") || clean.endsWith(".ts") || clean.endsWith(".css") ||
            clean.endsWith(".js") || clean.contains("/image/") || clean.contains("/covers/") ||
            clean.contains("/assets/")
    }

    private fun isBlockedAdNavigation(
        url: String,
        view: WebView?,
        hasGesture: Boolean = true,
        isServerRedirect: Boolean = false,
    ): Boolean {
        if (!isWebUrl(url)) return false
        if (isStaticAsset(url)) return false
        if (isAllowedAdNavigation?.invoke(url) == true) return false

        val adBlockActive = isAdBlockerEnabled()
        val redirectProtectionActive = isAdRedirectProtectionEnabled?.invoke() ?: true
        if (!adBlockActive && !redirectProtectionActive) return false

        // 1. Direct blocklist match (always blocked when ad blocker active)
        if (adBlockActive && AdBlockerEngine.shouldBlockNavigation(url)) {
            onBlockedAdNavigation?.invoke(tabId, url)
            AdBlockerEngine.recordBlockedRequest(false)
            return true
        }

        // 2. Cross-domain ad redirect / popup check
        val nestedView = view as? NestedWebView
        val hasOpenerGesture = nestedView?.hasOpenerUserGesture == true
        val effectiveGesture = hasGesture || hasOpenerGesture

        // A server redirect continues a navigation that was already allowed; only script-driven
        // cross-site jumps without a gesture are treated as ad hijacks.
        if (redirectProtectionActive && !effectiveGesture && !isServerRedirect) {
            val openerUrl = nestedView?.openerUrl
            val currentUrl = nestedView?.loadedUrl ?: openerUrl ?: view?.url
            if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank") {
                val currentHost = UrlSecurityUtils.extractHost(currentUrl)
                val targetHost = UrlSecurityUtils.extractHost(url)
                if (currentHost.isNotEmpty() && targetHost.isNotEmpty()) {
                    val sameSite = UrlSecurityUtils.isSameSite(currentUrl, url)
                    val isSearch = UrlSecurityUtils.isSearchEngineHost(currentHost)
                    // Leaving a login page (OAuth callback) is part of the user's sign-in.
                    val isAuth = UrlSecurityUtils.isAuthOrSecurityHost(targetHost) || isLoginPage(currentUrl)

                    if (!sameSite && !isSearch && !isAuth) {
                        onBlockedAdNavigation?.invoke(tabId, url)
                        AdBlockerEngine.recordBlockedRequest(false)
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun checkInsecureHttpNavigation(url: String): Boolean {
        if (!isHttpUrl(url)) return false
        val host = extractHost(url)
        val isAllowed = isInsecureHostAllowed?.invoke(host) == true
        if (!isAllowed) {
            onInsecureHttpWarning?.invoke(tabId, url)
            return true
        }
        return false
    }

    /**
     * Resolves [url] and offers the native app when one can handle it.
     *
     * @param allowFallbackNavigation when `false` (e.g. a subframe request), the site-provided
     *        `browser_fallback_url` is not loaded into the top-level WebView.
     * @param userInitiated whether the navigation came from a user gesture (a tap on a link) as
     *        opposed to a script redirect or a page load. A deliberate tap must be able to re-offer
     *        the app even after an earlier hint timed out.
     * @return `true` when the WebView must not navigate to [url] itself.
     */
    fun handleExternalNavigation(
        view: WebView?,
        url: String,
        allowFallbackNavigation: Boolean = true,
        userInitiated: Boolean = false,
    ): Boolean {
        val isEnabled = isOpenInAppsEnabled?.invoke() ?: true
        if (!isEnabled) {
            return if (isWebUrl(url)) false else true
        }

        val context = view?.context ?: return false
        if (isWebUrl(url)) {
            // A web URL always loads in the WebView; the lookup only decides whether to show the
            // "Open in app" hint, so it runs in the background.
            offerAppInBackground(context, url, userInitiated)
            return false
        }
        return when (val resolution = ExternalAppResolver.resolve(context, url, tabId)) {
            is ExternalAppResolution.Offer -> {
                // A deliberate tap on the site's own "Open app" control asks for confirmation
                // instead of showing the passive hint that disappears after a few seconds.
                val prompt = if (isDeliberateAppHandoff(resolution, userInitiated)) {
                    resolution.prompt.copy(requiresConfirmation = true)
                } else {
                    resolution.prompt
                }
                onExternalAppOffered?.invoke(tabId, prompt, userInitiated)
                resolution.isCustomScheme
            }

            is ExternalAppResolution.BrowserFallback -> {
                if (allowFallbackNavigation) {
                    view?.loadUrl(resolution.url)
                }
                true
            }

            is ExternalAppResolution.UnresolvableScheme -> {
                true
            }

            is ExternalAppResolution.NoApp -> {
                false
            }
        }
    }

    private fun isDeliberateAppHandoff(
        resolution: ExternalAppResolution.Offer,
        userInitiated: Boolean,
    ): Boolean =
        userInitiated &&
            resolution.isCustomScheme &&
            resolution.prompt.isInstalled &&
            !resolution.prompt.intentUri.startsWith("market:")

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (request == null) return null
        // The top-level document is never blocked here: navigations are decided in
        // shouldOverrideUrlLoading, and blocking the document only produces a blank page.
        if (request.isForMainFrame) return super.shouldInterceptRequest(view, request)
        val adBlockerActive = isAdBlockerEnabled()
        val trackingActive = isTrackingProtectionEnabled?.invoke() ?: true
        if (adBlockerActive || trackingActive) {
            val url = request.url?.toString()
            val parsedUrl = AdBlockerEngine.parseUrl(url)
            // Runs on a worker thread: WebView.getUrl() is main-thread only, so use the volatile mirror.
            val pageUrl = (view as? NestedWebView)?.loadedUrl
            if (parsedUrl != null && AdBlockerEngine.shouldBlock(parsedUrl, pageUrl)) {
                val validUrl = url.orEmpty()
                val isTracker = AdBlockerEngine.isTracker(parsedUrl, pageUrl)
                val shouldBlock = (isTracker && trackingActive) || (!isTracker && adBlockerActive)
                if (shouldBlock) {
                    onBlockedResource?.invoke(validUrl, isTracker)
                    AdBlockerEngine.recordBlockedRequest(isTracker)
                    return AdBlockerEngine.createSmartBlockedResponse(
                        url = url,
                        requestHeaders = request.requestHeaders,
                    )
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun checkExternalAppOffer(
        view: WebView?,
        url: String,
    ) {
        if (isOpenInAppsEnabled?.invoke() == false) return
        val context = view?.context ?: return
        offerAppInBackground(context, url, userInitiated = false)
    }

    private fun offerAppInBackground(
        context: android.content.Context,
        url: String,
        userInitiated: Boolean,
    ) {
        externalAppExecutor.execute {
            val resolution = ExternalAppResolver.resolve(context, url, tabId)
            if (resolution !is ExternalAppResolution.Offer) return@execute
            val deliver = { onExternalAppOffered?.invoke(tabId, resolution.prompt, userInitiated) }
            if (Looper.myLooper() == Looper.getMainLooper()) deliver() else mainHandler.post { deliver() }
        }
    }

    private fun notifyThemeColors(
        tabId: String,
        topColor: Int?,
        bottomColor: Int? = null,
    ) {
        if (onThemeColorsDetected != null) {
            onThemeColorsDetected.invoke(tabId, topColor, bottomColor)
        } else {
            onThemeColorDetected(tabId, topColor)
        }
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return

        if (isCleanPlayerEnabled?.invoke() == true) {
            val videoId =
                com.jestk.browser.util.YouTubeUrlHelper
                    .extractVideoId(validUrl)
            if (videoId != null && onCleanPlayerRequested?.invoke(validUrl) == true) {
                view?.stopLoading()
                return
            }
        }

        (view as? NestedWebView)?.loadedUrl = validUrl
        onPageStarted(tabId, validUrl)
        view?.let { wv ->
            checkExternalAppOffer(wv, validUrl)
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            val isDark = isDarkTheme?.invoke() ?: false
            if (validUrl.contains("apple.com", ignoreCase = true)) {
                notifyThemeColors(tabId, android.graphics.Color.parseColor("#161617"), android.graphics.Color.parseColor("#161617"))
            } else if (validUrl.contains("youtube.com", ignoreCase = true) && isDark) {
                notifyThemeColors(tabId, android.graphics.Color.parseColor("#0f0f0f"), android.graphics.Color.parseColor("#0f0f0f"))
            } else {
                wv.post {
                    extractThemeColor(wv, isPageStarted = true)
                }
            }
        }
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return
        (view as? NestedWebView)?.loadedUrl = validUrl
        (view as? NestedWebView)?.hasOpenerUserGesture = false
        val title = view?.title.orEmpty()
        onPageFinished(tabId, validUrl, title)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBack(), wv.canGoForward())
            injectPageScripts(wv, validUrl, title)
            wv.post {
                extractThemeColor(wv, isPageStarted = false)
                AdBlockerEngine.flushBlockedStats()
                SnapshotEngine.captureSnapshot(
                    webView = wv,
                    tabId = tabId,
                    context = wv.context,
                    onVisualColorsSampled = { topColor, bottomColor ->
                        if (!validUrl.contains("apple.com", ignoreCase = true)) {
                            notifyThemeColors(tabId, topColor, bottomColor)
                        }
                    },
                ) { path ->
                    if (path != null) {
                        onSnapshotReady(tabId, path)
                    }
                }
            }
        }
    }

    /**
     * Injects the page scripts once per page, after the document finished loading (an injection
     * from onPageStarted can still run in the previous document). With DOCUMENT_START_SCRIPT the
     * ad-block script is already registered for every document by WebViewPool, so it is only
     * evaluated here as a fallback. Security-challenge pages get nothing.
     */
    private fun injectPageScripts(webView: WebView, url: String, title: String) {
        if (isSecurityChallengePage(url, title)) return
        if (isAdBlockerEnabled() && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            AdBlockerScript.injectAdBlocker(webView)
        }
        if ((isAutoConsentEnabled ?: isAdBlockerEnabled).invoke()) {
            AdBlockerScript.injectAutoConsent(webView)
        }
    }

    fun extractThemeColor(
        webView: WebView,
        isPageStarted: Boolean = false,
    ) {
        val js =
            """
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
                notifyThemeColors(tabId, parsedColor)
            }
        }
    }

    override fun doUpdateVisitedHistory(
        view: WebView?,
        url: String?,
        isReload: Boolean,
    ) {
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
        /** How long after a tap later main-frame hops still count as part of that tap. */
        private const val GESTURE_CHAIN_WINDOW_MS = 3_000L

        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

        private val EXTERNAL_APP_EXECUTOR: Executor by lazy {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "external-app-resolver").apply { isDaemon = true }
            }
        }

        /**
         * Loads [url] into [webView], routing custom schemes and intent URLs through the
         * configured [JestkWebViewClient] when applicable.
         */
        fun loadUrlOrHandleExternal(
            webView: WebView,
            url: String,
        ) {
            val client = webView.webViewClient as? JestkWebViewClient
            val handledAsExternal =
                client != null &&
                    !isWebUrl(url) &&
                    client.handleExternalNavigation(webView, url)
            if (!handledAsExternal) {
                webView.loadUrl(url)
            }
        }

        fun isWebUrl(url: String): Boolean {
            val scheme =
                try {
                    android.net.Uri
                        .parse(url)
                        .scheme
                        ?.lowercase()
                } catch (t: Throwable) {
                    null
                }
            return scheme == "http" || scheme == "https"
        }

        fun isHttpUrl(url: String): Boolean = UrlSecurityUtils.isHttpUrl(url)

        private val IDENTITY_PROVIDER_HOSTS = setOf(
            "accounts.google.com", "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
            "oauth.vk.com", "id.vk.com", "passport.yandex.ru", "passport.yandex.com", "id.rambler.ru",
            "auth.mail.ru", "account.mail.ru",
        )

        /** Whether [url] is a sign-in page whose script redirect back to the site must not be blocked. */
        internal fun isLoginPage(url: String): Boolean {
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
            if (host in IDENTITY_PROVIDER_HOSTS) return true
            val path = uri.path.orEmpty().lowercase()
            val isMultiPurposeHost = host == "github.com" || host == "facebook.com" || host == "m.facebook.com"
            return isMultiPurposeHost && (path.startsWith("/login") || path.contains("/oauth"))
        }

        /**
         * Cloudflare serves its "Just a moment..." interstitial on the protected site's own
         * domain, so a host allowlist alone does not recognise it.
         */
        internal fun isSecurityChallengePage(url: String, title: String?): Boolean {
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            return AdBlockerEngine.isAllowlistedAuthDomain(host) ||
                url.contains("/cdn-cgi/challenge-platform", ignoreCase = true) ||
                title?.trim().equals("Just a moment...", ignoreCase = true)
        }

        fun extractHost(url: String): String = UrlSecurityUtils.extractHost(url)

        fun parseCssColor(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().removeSurrounding("\"").trim()
            if (clean.isEmpty() || clean == "null" || clean.equals("transparent", ignoreCase = true) ||
                clean == "rgba(0, 0, 0, 0)" || clean == "rgba(0,0,0,0)"
            ) {
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

                        6 -> {
                            android.graphics.Color.parseColor(clean)
                        }

                        8 -> {
                            // CSS hex #RRGGBBAA -> Android Color expects #AARRGGBB
                            val rr = clean.substring(1, 3).toInt(16)
                            val gg = clean.substring(3, 5).toInt(16)
                            val bb = clean.substring(5, 7).toInt(16)
                            val aa = clean.substring(7, 9).toInt(16)
                            if (aa < 25) null else android.graphics.Color.rgb(rr, gg, bb)
                        }

                        else -> {
                            null
                        }
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
                        val a =
                            if (parts.size >= 4) {
                                val alphaFloat = parts[3].toFloatOrNull() ?: 1f
                                if (alphaFloat <= 1.0f) {
                                    (alphaFloat * 255).toInt().coerceIn(0, 255)
                                } else {
                                    alphaFloat.toInt().coerceIn(0, 255)
                                }
                            } else {
                                255
                            }
                        if (a < 25) null else android.graphics.Color.rgb(r, g, b)
                    } else {
                        null
                    }
                } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to parse CSS rgb color: $clean", t)
                    null
                }
            }
            return null
        }
    }
}
