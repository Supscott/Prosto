package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.content.pm.ActivityInfo
import com.jestk.browser.R
import com.jestk.browser.util.findActivity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.DownloadFileNameResolver
import com.jestk.browser.engine.download.HoriDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class WebViewPool(
    private val applicationContext: Context,
    var downloadRepository: DownloadRepository? = null,
    var downloadManager: HoriDownloadManager? = null
) {

    companion object {
        private const val TAG = "WebViewPool"
    }

    var onDownloadStarted: ((DownloadItem) -> Unit)? = null
    var isAdBlockerEnabled: (() -> Boolean)? = null
    var isCleanPlayerEnabled: (() -> Boolean)? = null
    var onCleanPlayerRequested: ((url: String) -> Boolean)? = null
    var onWebViewCreated: ((tabId: String, webView: WebView) -> Unit)? = null

    internal var isUserAgentMetadataSupported: () -> Boolean = {
        WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
    }

    internal var userAgentMetadataGetter: (WebSettings) -> UserAgentMetadata? = { settings ->
        try {
            WebSettingsCompat.getUserAgentMetadata(settings)
        } catch (_: Throwable) {
            null
        }
    }

    internal var userAgentMetadataApplier: (WebSettings, UserAgentMetadata) -> Unit = { settings, metadata ->
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    }

    internal var isMultiProfileSupported: () -> Boolean = {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }

    internal var profileProvider: (String) -> Profile? = { profileName ->
        try {
            ProfileStore.getInstance().getOrCreateProfile(profileName)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to get or create profile $profileName", t)
            null
        }
    }

    internal var profileApplier: (WebView, String) -> Unit = { webView, profileName ->
        WebViewCompat.setProfile(webView, profileName)
    }

    internal var profileDeleter: (String) -> Boolean = { profileName ->
        try {
            ProfileStore.getInstance().deleteProfile(profileName)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to delete profile $profileName", t)
            false
        }
    }

    class AdBlockBridge(
        private val isEnabledProvider: () -> Boolean,
        private val onCleanPlayerRequested: ((url: String) -> Boolean)? = null,
        private val isCleanPlayerEnabledProvider: (() -> Boolean)? = null,
        private val onOrientationLockRequested: ((orientation: String?) -> Unit)? = null,
        private val onOrientationUnlockRequested: (() -> Unit)? = null,
    ) {
        constructor(isEnabledProvider: () -> Boolean) : this(isEnabledProvider, null, null, null, null)

        @JavascriptInterface
        fun isEnabled(): Boolean = isEnabledProvider()

        @JavascriptInterface
        fun isCleanPlayerEnabled(): Boolean = isCleanPlayerEnabledProvider?.invoke() ?: false

        @JavascriptInterface
        fun openCleanPlayer(url: String): Boolean {
            if (isCleanPlayerEnabledProvider?.invoke() != true) return false
            return onCleanPlayerRequested?.invoke(url) ?: false
        }

        @JavascriptInterface
        fun lockOrientation(orientation: String?) {
            onOrientationLockRequested?.invoke(orientation)
        }

        @JavascriptInterface
        fun unlockOrientation() {
            onOrientationUnlockRequested?.invoke()
        }
    }

    /**
     * Receives the content of a `blob:` download. The bridge is visible to every frame, so only
     * blob URLs that WebView itself reported to the DownloadListener are accepted: a page cannot
     * start a download of arbitrary data through it (review finding BOT-3).
     */
    class BlobDownloadBridge(
        private val pendingBlobUrls: MutableSet<String>,
        private val onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit
    ) {
        @JavascriptInterface
        fun onBlobDownloaded(blobUrl: String, dataUrl: String, contentDisposition: String?, mimeType: String?) {
            if (!pendingBlobUrls.remove(blobUrl)) {
                Log.w(TAG, "Ignoring a blob download that was not requested by the DownloadListener")
                return
            }
            onBlobReceived(dataUrl, contentDisposition, mimeType)
        }
    }

    private val pendingBlobUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pool = ConcurrentHashMap<String, WebView>()
    private val tabProfiles = ConcurrentHashMap<String, String>()
    private val _loadedTabIds = MutableStateFlow<Set<String>>(emptySet())
    val loadedTabIds: StateFlow<Set<String>> = _loadedTabIds.asStateFlow()

    fun getProfileForTab(tabId: String): String? = tabProfiles[tabId]

    private fun updateLoadedTabIds() {
        _loadedTabIds.value = pool.keys.toSet()
    }

    /**
     * Tabs whose WebView shows no page yet for the current navigation: a freshly created
     * WebView, or a navigation that started from Home. The UI keeps a placeholder (Home or the
     * tab snapshot) over such a tab until Chromium commits a visible frame, like Chrome's
     * paint holding, so the user never sees an empty viewport.
     */
    private val _awaitingFirstPaintTabIds = MutableStateFlow<Set<String>>(emptySet())
    val awaitingFirstPaintTabIds: StateFlow<Set<String>> = _awaitingFirstPaintTabIds.asStateFlow()

    fun markAwaitingFirstPaint(tabId: String) {
        _awaitingFirstPaintTabIds.update { it + tabId }
    }

    /** Chromium committed a visible frame for [tabId] (`onPageCommitVisible`) or finished loading. */
    fun markFirstPaint(tabId: String) {
        _awaitingFirstPaintTabIds.update { it - tabId }
    }

    /** Live WebViews kept before the least recently used one is evicted (see [WebViewPoolSizing]). */
    internal var maxWebViews: Int = WebViewPoolSizing.maxWebViews(applicationContext)

    /**
     * Under memory pressure keeps only the active tab and tabs playing media; the others are
     * evicted with their history saved and come back from their snapshot. Main thread only.
     */
    fun onTrimMemory(level: Int) {
        if (!WebViewPoolSizing.isMemoryPressure(level)) return
        spareWebView.clear()
        val keep = mediaPlayingTabs + listOfNotNull(activeTabId)
        val evicted = pool.keys.filter { it !in keep }
        evicted.forEach { removeWebView(it) }
        Log.d(TAG, "Memory pressure (level $level): evicted ${evicted.size} WebViews")
    }

    private val spareWebView = SpareWebView { context, profileName -> createConfiguredWebView(context, profileName) }

    /**
     * Warms the engine after the first frame: spawns the renderer process for [profileName] so
     * the first navigation from Home does not wait for it, and prepares a spare WebView for the
     * next tab. Main thread only.
     *
     * Starting Chromium in the background before the first frame was measured and rejected: in
     * release builds the first frame is ready in ~250 ms and deferring the first WebView delayed
     * the page by ~300 ms.
     */
    @OptIn(markerClass = [Profile.ExperimentalWarmUpRendererProcess::class])
    fun warmUp(activity: Context, profileName: String?) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS) && isMultiProfileSupported()) {
            try {
                profileProvider(profileName ?: Profile.DEFAULT_PROFILE_NAME)?.warmUpRendererProcess()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to warm up the renderer process", t)
            }
        }
        spareWebView.schedule(activity, profileName)
    }

    /**
     * Opens DNS + TCP + TLS to [url]'s origin in [profileName]'s network context, so a following
     * navigation skips the handshake. No HTTP request is sent. Main thread only.
     */
    @OptIn(markerClass = [Profile.ExperimentalPreconnect::class])
    fun preconnect(url: String, profileName: String?) {
        if (!isMultiProfileSupported() || !WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)) return
        try {
            profileProvider(profileName ?: Profile.DEFAULT_PROFILE_NAME)?.preconnect(url)
            Log.d(TAG, "Preconnect $url")
        } catch (t: Throwable) {
            Log.w(TAG, "Preconnect failed for $url", t)
        }
    }

    /** Saves the history of every live tab, so it survives the process being killed in the background. */
    fun saveAllStates() {
        pool.forEach { (tabId, webView) -> if (hasPage(webView)) WebViewStateStore.save(tabId, webView) }
    }

    /**
     * Restores the saved history of [tabId] into its fresh [webView] when the saved current page
     * is [url]. Returns true when that restore already loads [url]; otherwise the caller loads
     * [url] itself (after a mismatching restore the new page lands on top of the restored history).
     */
    fun restoreSavedState(tabId: String, webView: WebView, url: String): Boolean {
        if (!WebViewStateStore.restore(tabId, webView)) return false
        val restoredUrl = webView.url ?: return false
        if (!isSameDocument(restoredUrl, url)) return false
        (webView as? NestedWebView)?.loadedUrl = restoredUrl
        PageLoadTimer.onLoadRequested(tabId, restoredUrl)
        Log.d(TAG, "Restored saved history of tab $tabId")
        return true
    }

    private fun hasPage(webView: WebView): Boolean {
        val url = (webView as? NestedWebView)?.loadedUrl ?: webView.url
        return !url.isNullOrBlank() && url != "about:blank"
    }

    private fun isSameDocument(a: String, b: String): Boolean {
        fun normalize(url: String) = url.substringBefore('#').trimEnd('/')
        return normalize(a) == normalize(b)
    }

    fun isTabLoaded(tabId: String): Boolean = pool.containsKey(tabId)
    fun getLoadedTabIds(): Set<String> = pool.keys.toSet()

    private val lruOrder = ConcurrentLinkedDeque<String>()
    private val mediaPlayingTabs = ConcurrentHashMap.newKeySet<String>()
    private var activeTabId: String? = null
    private var currentIsDark: Boolean? = null

    private val scopeLock = Any()

    @Volatile
    private var coroutineScope: CoroutineScope = createScope()

    private fun createScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Returns the scope that runs the background work of the pool, recreating it when it was
     * cancelled by [shutdown].
     *
     * The pool outlives the activities it renders into, so a shutdown must never leave it unable
     * to handle downloads: the scope is recreated lazily instead of staying dead.
     */
    private fun activeScope(): CoroutineScope =
        synchronized(scopeLock) {
            val current = coroutineScope
            if (current.coroutineContext[Job]?.isActive == true) {
                current
            } else {
                createScope().also { coroutineScope = it }
            }
        }

    /**
     * Reports whether the background scope of the pool is running.
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun isScopeActive(): Boolean = coroutineScope.coroutineContext[Job]?.isActive == true

    /**
     * Makes sure the pool owns a running scope, recreating it after a [shutdown].
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun ensureScope(): Boolean = activeScope().coroutineContext[Job]?.isActive == true

    /**
     * Destroys the pool: every pooled WebView is released and the background scope is cancelled.
     *
     * The WebViews hold their Activity through a [MutableContextWrapper]; destroying them resets
     * the base context to the application context, so no Activity is leaked.
     *
     * The call is idempotent and safe for a live app: the scope is recreated lazily on the next
     * use, so the pool keeps working after a shutdown.
     */
    fun shutdown() {
        synchronized(scopeLock) {
            coroutineScope.cancel()
        }
        clearAll()
    }

    private fun touchLru(tabId: String) {
        lruOrder.remove(tabId)
        lruOrder.addFirst(tabId)
    }

    private fun trimPool(keepTabId: String) {
        while (pool.size > maxWebViews) {
            val iterator = lruOrder.descendingIterator()
            var evictedId: String? = null
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate != keepTabId && !mediaPlayingTabs.contains(candidate)) {
                    evictedId = candidate
                    break
                }
            }
            if (evictedId == null) {
                val fallbackIterator = lruOrder.descendingIterator()
                while (fallbackIterator.hasNext()) {
                    val candidate = fallbackIterator.next()
                    if (candidate != keepTabId) {
                        evictedId = candidate
                        break
                    }
                }
            }
            if (evictedId != null) {
                removeWebView(evictedId)
            } else {
                break
            }
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    fun setMediaPlaying(tabId: String, isPlaying: Boolean) {
        if (isPlaying) {
            mediaPlayingTabs.add(tabId)
            runOnMainThread {
                pool[tabId]?.let { wv ->
                    try { wv.onResume() } catch (t: Throwable) {
                        Log.w(TAG, "Failed to resume WebView for tab $tabId", t)
                    }
                }
            }
        } else {
            mediaPlayingTabs.remove(tabId)
        }
    }

    fun onTabActivated(tabId: String, isMediaPlaying: Boolean = false) {
        activeTabId = tabId
        touchLru(tabId)
        Log.d(TAG, "Activated tab $tabId (mediaPlaying=$isMediaPlaying, poolSize=${pool.size})")
        if (isMediaPlaying) {
            mediaPlayingTabs.add(tabId)
        }
        runOnMainThread {
            pool[tabId]?.let { webView ->
                try {
                    webView.onResume()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to resume WebView for active tab $tabId", t)
                }
            }
        }
    }

    fun deleteProfile(profileName: String): Boolean {
        val tabsToRemove = tabProfiles.entries
            .filter { it.value == profileName }
            .map { it.key }
        tabsToRemove.forEach { removeWebView(it) }

        if (!isMultiProfileSupported()) {
            return false
        }

        return try {
            profileDeleter(profileName)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to delete profile $profileName", t)
            false
        }
    }

    internal fun configureProfileAndCookies(webView: WebView, profileName: String?) {
        if (profileName != null && isMultiProfileSupported()) {
            try {
                val profile = profileProvider(profileName)
                profileApplier(webView, profileName)
                val cm = try {
                    profile?.cookieManager ?: CookieManager.getInstance()
                } catch (_: Throwable) {
                    CookieManager.getInstance()
                }
                try {
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(webView, true)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to set cookie acceptance on profile $profileName", t)
                }
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to assign profile $profileName", t)
            }
        }
        CookieManager.getInstance().let { cm ->
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        tabId: String,
        context: Context? = null,
        profileName: String? = null
    ): WebView {
        touchLru(tabId)
        val activity = context?.findActivity()
        val isActivityDestroyed = activity != null && (activity.isDestroyed || activity.isFinishing)
        val targetContext = if (isActivityDestroyed) {
            applicationContext
        } else {
            activity ?: context ?: applicationContext
        }

        val existing = pool[tabId]
        if (existing != null) {
            val existingProfile = tabProfiles[tabId]
            if (profileName == existingProfile) {
                try {
                    val wrapper = existing.context as? MutableContextWrapper
                    if (wrapper != null && wrapper.baseContext != targetContext) {
                        wrapper.baseContext = targetContext
                    }
                    return existing
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to reuse existing WebView for tab $tabId, recreating", t)
                    removeWebView(tabId)
                }
            } else {
                Log.d(TAG, "Profile mismatch for tab $tabId ($existingProfile != $profileName), recreating")
                removeWebView(tabId)
            }
        }

        // Initialize with MutableContextWrapper using Activity context so Chromium can attach to Window
        // and display the soft keyboard when input fields are focused.
        val createStartedAt = android.os.SystemClock.elapsedRealtime()
        val spare = spareWebView.take(targetContext, profileName)
        val contextWrapper = MutableContextWrapper(targetContext)
        val webView = spare ?: try {
            createConfiguredWebView(contextWrapper, profileName)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize WebView with Activity context, falling back to Application context", t)
            contextWrapper.baseContext = applicationContext
            createConfiguredWebView(contextWrapper, profileName)
        }

        currentIsDark?.let { applyThemeMode(webView, it) }
        webView.tag = tabId
        pool[tabId] = webView
        markAwaitingFirstPaint(tabId)
        if (profileName != null) {
            tabProfiles[tabId] = profileName
        } else {
            tabProfiles.remove(tabId)
        }
        updateLoadedTabIds()
        trimPool(tabId)
        val source = if (spare != null) "spare" else "new"
        Log.d(TAG, "Created WebView for tab $tabId ($source, ${android.os.SystemClock.elapsedRealtime() - createStartedAt}ms)")
        onWebViewCreated?.invoke(tabId, webView)
        if (activity != null && !isActivityDestroyed) spareWebView.schedule(activity, profileName)
        return webView
    }

    /**
     * Creates and configures a WebView. A WebView that fails during configuration is destroyed
     * before the error propagates, so the fallback attempt never leaks a half-initialized instance.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context, profileName: String?): WebView {
        val webView = NestedWebView(context)
        try {
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            val rawUserAgent = webView.settings.userAgentString
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                userAgentString = UserAgentSanitizer.sanitize(rawUserAgent)
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
            }
            // Back/forward cache keeps the previous page alive in memory, so Back is instant
            // instead of a reload, like Chrome.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
                WebSettingsCompat.setBackForwardCacheEnabled(webView.settings, true)
            }
            configureProfileAndCookies(webView, profileName)
            try {
                val blobBridge = BlobDownloadBridge(pendingBlobUrls) { dataUrl, contentDisposition, mimeType ->
                    activeScope().launch(Dispatchers.Main) {
                        handleDataUrlDownload(dataUrl, contentDisposition, mimeType, webView.context)
                    }
                }
                webView.addJavascriptInterface(blobBridge, "JestkBlobBridge")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to add BlobDownloadBridge interface", t)
            }
            setupDownloadListener(webView)
            setupAdBlocker(webView)
            applyUserAgentMetadata(webView.settings, rawUserAgent)
            return webView
        } catch (t: Throwable) {
            runCatching { webView.destroy() }
            throw t
        }
    }

    private fun setupAdBlocker(webView: WebView) {
        try {
            val bridge = AdBlockBridge(
                isEnabledProvider = { isAdBlockerEnabled?.invoke() ?: true },
                onCleanPlayerRequested = { url ->
                    // Only YouTube pages may open the clean player, and only for a video URL.
                    isYouTubeHost((webView as? NestedWebView)?.loadedUrl) &&
                        com.jestk.browser.util.YouTubeUrlHelper.extractVideoId(url) != null &&
                        (onCleanPlayerRequested?.invoke(url) ?: false)
                },
                isCleanPlayerEnabledProvider = { isCleanPlayerEnabled?.invoke() ?: true },
                onOrientationLockRequested = { orientation ->
                    val act = webView.context.findActivity() ?: return@AdBlockBridge
                    act.runOnUiThread {
                        // A page may rotate the screen only while it shows fullscreen content.
                        val isFullscreen = (webView.webChromeClient as? JestkWebChromeClient)?.isCustomViewShowing() == true
                        if (!isFullscreen) return@runOnUiThread
                        val target = when (orientation) {
                            "portrait", "portrait-primary" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            "portrait-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            "landscape-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        act.requestedOrientation = target
                    }
                },
                onOrientationUnlockRequested = {
                    val act = webView.context.findActivity() ?: return@AdBlockBridge
                    act.runOnUiThread {
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            )
            webView.addJavascriptInterface(bridge, "HoriAdBlockBridge")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to add HoriAdBlockBridge interface", t)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    AdBlockerScript.buildDocumentStartScript(),
                    setOf("*")
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to add document start script for ad blocking", t)
            }
        }
    }

    internal fun applyUserAgentMetadata(settings: WebSettings, rawUserAgent: String? = null) {
        if (isUserAgentMetadataSupported()) {
            try {
                val currentMetadata = userAgentMetadataGetter(settings)
                val metadata = UserAgentMetadataProvider.buildOrUpdateMetadata(
                    currentMetadata = currentMetadata,
                    userAgent = settings.userAgentString,
                    rawUserAgent = rawUserAgent
                )
                userAgentMetadataApplier(settings, metadata)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to apply user agent metadata", t)
            }
        }
    }

    private fun setupDownloadListener(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimeType, contentLength, webView.context, webView)
        }
    }

    fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        context: Context,
        webView: WebView? = null
    ) {
        val targetContext = context.findActivity() ?: applicationContext

        // Check for data: URI scheme
        if (url.startsWith("data:", ignoreCase = true)) {
            handleDataUrlDownload(url, contentDisposition, mimeType, targetContext)
            return
        }

        // Check for blob: URI scheme
        if (url.startsWith("blob:", ignoreCase = true)) {
            handleBlobDownload(url, contentDisposition, mimeType, targetContext, webView)
            return
        }

        try {
            val fileName = DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
            Log.d(TAG, "Download requested: $fileName from $url")
            val downloadMgr = downloadManager ?: downloadRepository?.let {
                HoriDownloadManager.getInstance(applicationContext, it)
            }
            val refererUrl = try {
                webView?.url
            } catch (t: Throwable) {
                Log.d(TAG, "Best-effort read of referer URL failed, continuing without referer", t)
                null
            }

            Toast.makeText(targetContext, targetContext.getString(R.string.download_in_progress_notification, fileName), Toast.LENGTH_SHORT).show()

            if (downloadMgr != null) {
                activeScope().launch {
                    try {
                        val downloadId = downloadMgr.enqueueDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            contentLength = contentLength,
                            referer = refererUrl,
                            customFileName = fileName
                        )
                        val item = downloadRepository?.getDownloadById(downloadId)
                        if (item != null) {
                            withContext(Dispatchers.Main) {
                                onDownloadStarted?.invoke(item)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to enqueue download", t)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed for $url", e)
            val targetContext = context.findActivity() ?: applicationContext
            try {
                Toast.makeText(targetContext, downloadFailedText(targetContext, e), Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to show download error toast", t)
            }
        }
    }

    private fun handleDataUrlDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context
    ) {
        val targetContext = context.findActivity() ?: applicationContext
        val downloadMgr = downloadManager ?: downloadRepository?.let {
            HoriDownloadManager.getInstance(applicationContext, it)
        }

        if (downloadMgr != null) {
            activeScope().launch {
                try {
                    val downloadId = downloadMgr.handleDataUrl(url, contentDisposition, mimeType)
                    val item = downloadRepository?.getDownloadById(downloadId)
                    if (item != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(targetContext, targetContext.getString(R.string.download_in_progress_notification, item.fileName), Toast.LENGTH_SHORT).show()
                            onDownloadStarted?.invoke(item)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Data URL download failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(targetContext, downloadFailedText(targetContext, e), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    internal fun handleBlobDownload(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context,
        webView: WebView?
    ) {
        val targetContext = context.findActivity() ?: applicationContext
        if (webView == null) {
            Log.w(TAG, "Cannot handle blob download without originating WebView reference")
            Toast.makeText(targetContext, R.string.downloads_failed, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(targetContext, R.string.toast_preparing_download, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Preparing blob download: $blobUrl")

        pendingBlobUrls.add(blobUrl)
        val js = buildBlobDownloadScript(blobUrl, contentDisposition, mimeType)

        try {
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to evaluate blob download script", t)
            Toast.makeText(targetContext, downloadFailedText(targetContext, t), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun buildBlobDownloadScript(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val jsonUrl = org.json.JSONObject.quote(blobUrl)
        val jsonDisposition = org.json.JSONObject.quote(contentDisposition ?: "")
        val jsonMime = org.json.JSONObject.quote(mimeType ?: "")

        return """
            (function() {
                try {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', $jsonUrl, true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        if (this.status === 200 || this.status === 0) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                if (window.JestkBlobBridge) {
                                    window.JestkBlobBridge.onBlobDownloaded($jsonUrl, reader.result, $jsonDisposition, $jsonMime);
                                }
                            };
                            reader.readAsDataURL(this.response);
                        }
                    };
                    xhr.onerror = function() {
                        fetch($jsonUrl)
                            .then(function(r) { return r.blob(); })
                            .then(function(b) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.JestkBlobBridge) {
                                        window.JestkBlobBridge.onBlobDownloaded($jsonUrl, reader.result, $jsonDisposition, $jsonMime);
                                    }
                                };
                                reader.readAsDataURL(b);
                            })
                            .catch(function(err) {});
                    };
                    xhr.send();
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Applies the app theme to [webView]. Pages see `prefers-color-scheme` through the hosting
     * Activity theme (see `MainActivity.applyAppNightMode`), so no script is injected here: a
     * patched `window.matchMedia` is a bot-detection signal (review finding BOT-1).
     */
    private fun isYouTubeHost(url: String?): Boolean {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    }

    fun applyThemeMode(webView: WebView, isDark: Boolean) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDark)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply theme mode", t)
        }
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
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to detach WebView from parent", t)
        }
    }

    fun getWebView(tabId: String): WebView? = pool[tabId]

    /**
     * Destroys the WebView of [tabId]. With [keepState] (eviction, workspace switch, move) its
     * history is saved first, so the tab comes back where it was (see [WebViewStateStore]).
     */
    fun removeWebView(tabId: String, keepState: Boolean = true) {
        lruOrder.remove(tabId)
        mediaPlayingTabs.remove(tabId)
        if (activeTabId == tabId) {
            activeTabId = null
        }
        tabProfiles.remove(tabId)
        markFirstPaint(tabId)
        val webView = pool.remove(tabId) ?: return
        updateLoadedTabIds()
        if (keepState && hasPage(webView)) WebViewStateStore.save(tabId, webView)
        Log.d(TAG, "Removing WebView for tab $tabId")
        detachFromParent(webView)
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView as? NestedWebView)?.loadedUrl = null
            webView.clearHistory()
            (webView as? ViewGroup)?.removeAllViews()
            webView.webChromeClient = null
            webView.webViewClient = android.webkit.WebViewClient()
            (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
            webView.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to destroy WebView for tab $tabId", t)
            try {
                (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
            } catch (t2: Throwable) {
                Log.w(TAG, "Failed to reset baseContext for tab $tabId", t2)
            }
        }
    }

    /**
     * Forgets and destroys the WebView of [tabId] after its renderer died
     * (`onRenderProcessGone`). Such a WebView may only be destroyed, so unlike [removeWebView]
     * no navigation or history call is made on it.
     */
    fun discardCrashedWebView(tabId: String) {
        lruOrder.remove(tabId)
        mediaPlayingTabs.remove(tabId)
        if (activeTabId == tabId) activeTabId = null
        tabProfiles.remove(tabId)
        markFirstPaint(tabId)
        val webView = pool.remove(tabId) ?: return
        updateLoadedTabIds()
        detachFromParent(webView)
        try {
            (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
            webView.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to destroy crashed WebView for tab $tabId", t)
        }
    }

    fun retainTabs(activeTabIds: Set<String>) {
        val toRemove = pool.keys().toList().filter { it !in activeTabIds }
        toRemove.forEach { removeWebView(it) }
    }

    fun clearAll() {
        spareWebView.clear()
        lruOrder.clear()
        mediaPlayingTabs.clear()
        activeTabId = null
        tabProfiles.clear()
        val keys = pool.keys().toList()
        keys.forEach { removeWebView(it, keepState = false) }
        pool.clear()
        updateLoadedTabIds()
    }

    fun size(): Int = pool.size

    private fun downloadFailedText(context: Context, error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.toast_download_failed_reason, it) }
            ?: context.getString(R.string.downloads_failed)
}
