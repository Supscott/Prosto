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
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.engine.adblock.BlockPageAction
import com.jestk.browser.engine.adblock.BlockPageActions
import com.jestk.browser.engine.adblock.GuardSettings
import com.jestk.browser.engine.adblock.GuardVerdict
import com.jestk.browser.engine.adblock.GuardedNavigation
import com.jestk.browser.engine.adblock.NetworkRequestInterceptor
import com.jestk.browser.engine.adblock.canGoBackPastBlockPages
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
    /** `$document` rules: whether [url]'s host was allowed ("Proceed anyway") in this tab. */
    private val isDocumentAllowed: ((url: String) -> Boolean)? = null,
    /** A button of the block page was tapped; the URL is the blocked page. */
    private val onBlockPageAction: ((tabId: String, action: BlockPageAction, blockedUrl: String) -> Unit)? = null,
    private val isAdRedirectProtectionEnabled: (() -> Boolean)? = null,
    /** A popunder was stopped: (tab, page the user tapped, destination). The popup pill reacts. */
    private val onPopunderBlocked: ((tabId: String, openerUrl: String?, blockedUrl: String) -> Unit)? = null,
    private val isAllowedAdNavigation: ((url: String) -> Boolean)? = null,
    private val isAutoConsentEnabled: (() -> Boolean)? = null,
    private val onRenderProcessGone: ((tabId: String) -> Unit)? = null,
    /** The page shows its first visible frame, or finished loading without reporting one. */
    private val onFirstVisibleFrame: ((tabId: String) -> Unit)? = null,
    /** Runs the PackageManager lookups for web URLs; they must not block the UI thread. */
    private val externalAppExecutor: Executor = EXTERNAL_APP_EXECUTOR,
) : WebViewClient() {

    @Volatile
    private var lastGestureNavigationAtMs = Long.MIN_VALUE / 2

    private val navigationGuard = WindowTransportHandler.navigationGuard

    /** The last URL a block page was served for; its commit must not report the stale title of the page before. */
    @Volatile
    private var blockPageUrl: String? = null

    /** The action token of that block page; see [BlockPageActions]. */
    @Volatile
    private var blockPageToken: String? = null

    private val networkInterceptor = NetworkRequestInterceptor(
        adsEnabled = isAdBlockerEnabled,
        trackersEnabled = { isTrackingProtectionEnabled?.invoke() ?: true },
        isDocumentAllowed = { isDocumentAllowed?.invoke(it) == true },
        onBlockedResource = onBlockedResource,
        onBlockedDocument = { url, token ->
            blockPageUrl = url
            blockPageToken = token
        },
    )

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

        // The block page buttons; a forged or misplaced navigation to the scheme just does nothing.
        if (BlockPageActions.isActionUrl(url)) {
            BlockPageActions.resolve(view, request, blockPageToken)?.let { (action, blockedUrl) -> onBlockPageAction?.invoke(tabId, action, blockedUrl) }
            return true
        }

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

        if (request.isForMainFrame && (PopupGateHooks.onUrl(view, url) || isBlockedAdNavigation(url, view, effectiveGesture, request.isRedirect))) return true

        // Warn before loading cleartext HTTP websites in the main frame
        if (request.isForMainFrame && checkInsecureHttpNavigation(url)) return true

        return handleExternalNavigation(
            view = view,
            url = url,
            allowFallbackNavigation = request.isForMainFrame,
            userInitiated = continuesGesture,
        )
    }

    private fun isBlockedAdNavigation(
        url: String,
        view: WebView?,
        hasGesture: Boolean,
        isServerRedirect: Boolean,
    ): Boolean {
        if (isAllowedAdNavigation?.invoke(url) == true) return false
        val nested = view as? NestedWebView
        val verdict = navigationGuard.evaluate(
            GuardedNavigation(
                tabId = tabId, url = url,
                currentUrl = nested?.loadedUrl ?: nested?.openerUrl ?: view?.url,
                hasGesture = hasGesture || nested?.hasOpenerUserGesture == true,
                isServerRedirect = isServerRedirect,
                workspaceId = nested?.workspaceId(),
            ),
            GuardSettings(
                adBlockActive = isAdBlockerEnabled(),
                redirectProtectionActive = isAdRedirectProtectionEnabled?.invoke() ?: true,
            ),
        )
        when (verdict) {
            GuardVerdict.ALLOW -> return false
            GuardVerdict.BLOCK_POPUNDER -> onPopunderBlocked?.invoke(tabId, nested?.loadedUrl ?: view?.url, url)
            GuardVerdict.BLOCK_LISTED, GuardVerdict.BLOCK_REDIRECT -> onBlockedAdNavigation?.invoke(tabId, url)
        }
        AdBlockerEngine.recordBlockedRequest(false)
        return true
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
            // Tapped app-only links ask first (DedicatedAppLinks); other web URLs load, maybe with a hint.
            if (userInitiated && allowFallbackNavigation && DedicatedAppLinks.offer(context, url, tabId, onExternalAppOffered)) return true
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
        // Runs on a worker thread: WebView.getUrl() is main-thread only, so use the volatile mirror.
        val nested = view as? NestedWebView
        return PopupGateHooks.intercept(view, request) ?: networkInterceptor.interceptForTab(request, nested, view?.context)
            ?: super.shouldInterceptRequest(view, request)
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
        navigationState = NavigationState.STARTED
        startedUrl = validUrl

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
        PageLoadTimer.onPageStarted(tabId, validUrl)
        onPageStarted(tabId, validUrl)
        view?.let { wv ->
            checkExternalAppOffer(wv, validUrl)
            onNavigationStateChanged(tabId, wv.canGoBackPastBlockPages(), wv.canGoForward())
            val isDark = isDarkTheme?.invoke() ?: false
            val fixedBar = SiteThemeOverride.barColor(validUrl, isDark)
            if (fixedBar != null) {
                notifyThemeColors(tabId, fixedBar, fixedBar)
            } else {
                wv.post {
                    extractThemeColor(wv, isPageStarted = true)
                }
            }
        }
    }

    override fun onPageCommitVisible(
        view: WebView?,
        url: String?,
    ) {
        super.onPageCommitVisible(view, url)
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return
        PageLoadTimer.onFirstVisibleFrame(tabId, validUrl)
        onFirstVisibleFrame?.invoke(tabId)
    }

    /** Where the current navigation is; see [onProgressComplete]. */
    private enum class NavigationState { IDLE, STARTED, FINISHED, FINISH_SYNTHESIZED }

    @Volatile
    private var navigationState = NavigationState.IDLE
    private var startedUrl: String? = null

    /**
     * Progress reached 100%. A page restored from the back/forward cache reports onPageStarted
     * and its first frame but never onPageFinished, so the end-of-load work (snapshot, title,
     * theme, history) would be skipped and the tab snapshot would keep showing the page the user
     * went back from. When onPageFinished has not arrived shortly after, it is run here once;
     * a late real call for the same navigation is then ignored. Main thread only.
     */
    fun onProgressComplete(view: WebView) {
        if (navigationState != NavigationState.STARTED) return
        view.postDelayed({
            if (navigationState != NavigationState.STARTED) return@postDelayed
            val url = view.url ?: return@postDelayed
            Log.d("JestkWebViewClient", "No onPageFinished for $url (back/forward cache restore), finishing it")
            navigationState = NavigationState.FINISH_SYNTHESIZED
            handlePageFinished(view, url)
        }, SYNTHESIZED_FINISH_DELAY_MS)
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (navigationState == NavigationState.FINISH_SYNTHESIZED) {
            navigationState = NavigationState.FINISHED
            return
        }
        navigationState = NavigationState.FINISHED
        handlePageFinished(view, url)
    }

    private fun handlePageFinished(
        view: WebView?,
        url: String?,
    ) {
        val validUrl = url.orEmpty()
        if (validUrl.isEmpty() || validUrl == "about:blank") return
        PageLoadTimer.onPageFinished(tabId, validUrl)
        // Some loads (errors, downloads, cached same-document pages) never report a commit.
        onFirstVisibleFrame?.invoke(tabId)
        (view as? NestedWebView)?.loadedUrl = validUrl
        (view as? NestedWebView)?.hasOpenerUserGesture = false
        val title = view?.title.orEmpty()
        onPageFinished(tabId, validUrl, title)
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBackPastBlockPages(), wv.canGoForward())
            injectPageScripts(wv, validUrl, title)
            wv.post {
                extractThemeColor(wv, isPageStarted = false)
                AdBlockerEngine.flushBlockedStats()
                SnapshotEngine.captureSnapshot(
                    webView = wv,
                    tabId = tabId,
                    context = wv.context,
                    onVisualColorsSampled = { topColor, bottomColor ->
                        if (!SiteThemeOverride.ignoresSampledColors(validUrl)) {
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
     * Injects the autoconsent script once per page, after the document finished loading (an
     * injection from onPageStarted can still run in the previous document). The cosmetic
     * bootstrap is registered for every document by ContentBlockingSetup and has no late
     * fallback. Security-challenge pages get nothing.
     */
    private fun injectPageScripts(webView: WebView, url: String, title: String) {
        if (isSecurityChallengePage(url, title)) return
        if ((isAutoConsentEnabled ?: isAdBlockerEnabled).invoke()) {
            AdBlockerScript.injectAutoConsent(webView)
        }
    }

    fun extractThemeColor(
        webView: WebView,
        isPageStarted: Boolean = false,
    ) {
        val js = ThemeColorScript.build(isPageStarted)
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
            // Only a same-document (history API) navigation already has its own title here; a
            // cross-document one still reports the previous page's title. Its onPageStarted may
            // come just before or after this callback, so decide once both have run.
            view?.post {
                val crossDocument = navigationState == NavigationState.STARTED && startedUrl == validUrl
                val title = view.title
                if (!crossDocument && !title.isNullOrBlank() && validUrl != blockPageUrl) onTitleReceived?.invoke(tabId, title)
            }
        }
        view?.let { wv ->
            onNavigationStateChanged(tabId, wv.canGoBackPastBlockPages(), wv.canGoForward())
        }
    }

    companion object {
        /** onPageFinished normally follows 100% progress within a few ms. */
        private const val SYNTHESIZED_FINISH_DELAY_MS = 300L

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
    }
}
