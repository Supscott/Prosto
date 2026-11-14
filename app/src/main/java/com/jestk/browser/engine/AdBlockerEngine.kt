package com.jestk.browser.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.WebResourceResponse
import com.jestk.browser.domain.repository.BlockedRequestStats
import com.jestk.browser.engine.adblock.AuthDomainAllowlist
import com.jestk.browser.engine.adblock.AdblockLatency
import com.jestk.browser.engine.adblock.BlockedResponses
import com.jestk.browser.engine.adblock.CosmeticResources
import com.jestk.browser.engine.adblock.Decision
import com.jestk.browser.engine.adblock.EngineDecider
import com.jestk.browser.engine.adblock.EngineStore
import com.jestk.browser.engine.adblock.HostListMatcher
import com.jestk.browser.engine.adblock.NativeBackend
import com.jestk.browser.engine.adblock.ParsedUrl
import com.jestk.browser.engine.adblock.RequestType
import com.jestk.browser.engine.adblock.UrlParser
import com.jestk.browser.util.UrlSecurityUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.InputStream

object AdBlockerEngine {
    private const val TAG = "AdBlockerEngine"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var initJob: Job? = null

    @Volatile
    internal var backend: NativeBackend = NativeBackend.DEFAULT
        set(value) {
            field = value
            engineStore = EngineStore(value, log = ::debugLog)
        }

    @Volatile
    internal var engineStore: EngineStore = EngineStore(NativeBackend.DEFAULT, log = ::debugLog)

    private fun debugLog(message: String) {
        Log.i(TAG, message)
    }

    /**
     * Initializes the ad blocker engine asynchronously on [dispatcher].
     * Cold start remains instant because the host list is initially populated with its default
     * hosts. The host list loads in parallel with the native engines (cached blob or compile);
     * until the engines are published, and when the native library is missing, the host list decides.
     */
    fun initialize(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Job {
        val currentJob = initJob
        if (currentJob != null) return currentJob

        return synchronized(this) {
            initJob ?: run {
                val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
                // The rule loading runs on the engine's own scope so that shutdown() cancels it
                // together with the rest of the engine's background work.
                val job =
                    activeScope().launch(dispatcher) {
                        configureLatencyLog(appContext)
                        coroutineScope {
                            launch { loadRulesFromAssets(appContext) }
                            if (backend.load()) engineStore.initialize(appContext, this, dispatcher)
                        }
                    }
                initJob = job
                job
            }
        }
    }

    suspend fun awaitInitialization() {
        initJob?.join()
    }

    private fun loadRulesFromAssets(context: Context) {
        try {
            context.assets.open(HostListMatcher.PRECOMPILED_ASSET).use { inputStream ->
                loadPrecompiledDomainsFromStream(inputStream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load ${HostListMatcher.PRECOMPILED_ASSET}, only the default hosts are blocked", e)
        }
        isInitialized = true
    }

    fun loadPrecompiledDomainsFromStream(inputStream: InputStream) =
        HostListMatcher.loadPrecompiledDomainsFromStream(inputStream)

    fun parseUrl(url: String?): ParsedUrl? = UrlParser.parseUrl(url)

    private fun configureLatencyLog(context: Context) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        AdblockLatency.enabled = debuggable
    }

    /**
     * The primary network decision (CB-06). Empty, non-http(s) and auth/captcha URLs are always
     * allowed. With the native engines ready, the privacy engine is checked first (a block there is
     * a tracker), then the ads engine; otherwise the host list decides with its ad/tracker split.
     */
    fun decide(
        url: String,
        sourceUrl: String,
        type: RequestType,
        adsEnabled: Boolean,
        trackersEnabled: Boolean,
    ): Decision {
        if (!adsEnabled && !trackersEnabled) return Decision.ALLOW
        if (!isHttpUrl(url)) return Decision.ALLOW
        val timed = AdblockLatency.enabled
        val started = if (timed) System.nanoTime() else 0L
        try {
            val parsed = UrlParser.parseUrl(url) ?: return Decision.ALLOW
            if (parsed.host.isEmpty() || AuthDomainAllowlist.isAllowlistedAuthDomain(parsed.host)) return Decision.ALLOW
            val set = engineStore.current()
            if (set != null) {
                return EngineDecider.decide(set, url, sourceUrl.ifEmpty { url }, type, adsEnabled, trackersEnabled)
            }
            return hostDecision(parsed, sourceUrl, adsEnabled, trackersEnabled)
        } finally {
            if (timed) AdblockLatency.record(System.nanoTime() - started)
        }
    }

    private fun hostDecision(parsed: ParsedUrl, sourceUrl: String, adsEnabled: Boolean, trackersEnabled: Boolean): Decision {
        val document = sourceUrl.ifEmpty { null }
        if (!HostListMatcher.shouldBlock(parsed, document)) return Decision.ALLOW
        val tracker = HostListMatcher.isTracker(parsed, document)
        val enabled = if (tracker) trackersEnabled else adsEnabled
        return if (enabled) Decision(block = true, isTracker = tracker, redirectDataUrl = null) else Decision.ALLOW
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    fun shouldBlock(url: String?, documentUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        if (engineStore.current() == null) return HostListMatcher.shouldBlock(url, documentUrl)
        return decide(url, documentUrl.orEmpty(), RequestType.OTHER, adsEnabled = true, trackersEnabled = true).block
    }

    fun shouldBlock(parsed: ParsedUrl, documentUrl: String? = null): Boolean {
        if (engineStore.current() == null) return HostListMatcher.shouldBlock(parsed, documentUrl)
        return decide(parsed.rawUrl, documentUrl.orEmpty(), RequestType.OTHER, adsEnabled = true, trackersEnabled = true).block
    }

    /**
     * Decides whether a top-level navigation to [url] is an ad click that must not open: a
     * `DOCUMENT` check against the ads engine, or the host list before the engines are ready.
     */
    fun shouldBlockNavigation(url: String?): Boolean {
        val set = engineStore.current() ?: return HostListMatcher.shouldBlockNavigation(url)
        if (url.isNullOrBlank() || !isHttpUrl(url)) return false
        val host = UrlParser.parseUrl(url)?.host.orEmpty()
        if (host.isEmpty() || AuthDomainAllowlist.isAllowlistedAuthDomain(host)) return false
        return EngineDecider.blocks(set.ads.check(url, url, RequestType.DOCUMENT))
    }

    internal fun isAllowlistedAuthDomain(host: String): Boolean = AuthDomainAllowlist.isAllowlistedAuthDomain(host)

    val TRANSPARENT_1X1_PNG: ByteArray get() = BlockedResponses.TRANSPARENT_1X1_PNG

    fun createBlockedResponse() = BlockedResponses.createBlockedResponse()

    fun createSmartBlockedResponse(
        url: String?,
        mimeTypeHint: String? = null,
        requestHeaders: Map<String, String>? = null,
    ) = BlockedResponses.createSmartBlockedResponse(url, mimeTypeHint, requestHeaders)

    /** Native: rules in the compiled lists, counted at compile time; otherwise the host list size. */
    fun getRulesCount(): Int = engineStore.current()?.rulesCount ?: HostListMatcher.getRulesCount()

    fun containsHost(host: String): Boolean = HostListMatcher.containsHost(host)

    fun isTracker(url: String, documentUrl: String? = null): Boolean {
        if (url.isBlank()) return false
        return isTracker(UrlParser.parseUrl(url) ?: return false, documentUrl)
    }

    @JvmName("isTrackerNullable")
    fun isTracker(url: String?, documentUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        return isTracker(UrlParser.parseUrl(url) ?: return false, documentUrl)
    }

    /** A same-site request is never a tracker; otherwise the privacy engine (or the host heuristic) decides. */
    fun isTracker(parsed: ParsedUrl, documentUrl: String? = null): Boolean {
        val set = engineStore.current() ?: return HostListMatcher.isTracker(parsed, documentUrl)
        if (!documentUrl.isNullOrBlank() && UrlSecurityUtils.isSameSite(documentUrl, parsed.rawUrl)) return false
        if (!isHttpUrl(parsed.rawUrl) || AuthDomainAllowlist.isAllowlistedAuthDomain(parsed.host)) return false
        return EngineDecider.blocks(set.privacy.check(parsed.rawUrl, documentUrl ?: parsed.rawUrl, RequestType.OTHER))
    }

    /**
     * Cosmetic resources for [url] (CB-07): the ads engine's when ads are on, with the privacy
     * engine's scriptlets merged in when trackers are on. [CosmeticResources.EMPTY] until the
     * native engines are ready, so cosmetic filtering degrades to nothing, never to a guess.
     */
    fun cosmeticResources(url: String, adsEnabled: Boolean, trackersEnabled: Boolean): CosmeticResources {
        if (!adsEnabled && !trackersEnabled) return CosmeticResources.EMPTY
        val set = engineStore.current() ?: return CosmeticResources.EMPTY
        val base = if (adsEnabled) set.ads.cosmeticResources(url) else CosmeticResources.EMPTY
        if (!trackersEnabled) return base
        val extra = set.privacy.cosmeticResources(url).scriptlets.filter { it !in base.scriptlets }
        return if (extra.isEmpty()) base else base.copy(scriptlets = base.scriptlets + extra)
    }

    fun isReady(): Boolean = isInitialized

    /** True once the native engines are compiled or loaded and published. */
    fun isNativeReady(): Boolean = engineStore.current() != null

    @Volatile
    private var blockedRequestStats: BlockedRequestStats? = null

    /**
     * Installs the sink that collects the blocked-request statistics.
     *
     * The engine depends on the narrow [BlockedRequestStats] contract instead of a settings
     * repository, which keeps the Room-backed data layer out of the engine.
     */
    fun setBlockedRequestStats(stats: BlockedRequestStats?) {
        blockedRequestStats = stats
    }

    private val scopeLock = Any()

    @Volatile
    private var engineScope: CoroutineScope = createEngineScope()

    private fun createEngineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Returns the scope used for background bookkeeping, recreating it when it was cancelled by
     * [shutdown]. The engine is an app-scoped singleton, so a shutdown must never leave it unable
     * to record blocked requests.
     */
    private fun activeScope(): CoroutineScope =
        synchronized(scopeLock) {
            val current = engineScope
            if (current.coroutineContext[Job]?.isActive == true) {
                current
            } else {
                createEngineScope().also { engineScope = it }
            }
        }

    /**
     * Reports whether the background scope of the engine is running.
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun isScopeActive(): Boolean = engineScope.coroutineContext[Job]?.isActive == true

    /**
     * Makes sure the engine owns a running scope, recreating it after a [shutdown].
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun ensureScope(): Boolean = activeScope().coroutineContext[Job]?.isActive == true

    /**
     * Cancels the background work of the engine and the pending rule loading job.
     *
     * The call is idempotent and safe for a live app: the scope is recreated lazily on the next
     * use, so the engine keeps working after a shutdown.
     */
    fun shutdown() {
        synchronized(scopeLock) {
            engineScope.cancel()
        }
        synchronized(this) {
            initJob?.cancel()
            initJob = null
        }
    }

    fun recordBlockedRequest(isTracker: Boolean) {
        val stats = blockedRequestStats ?: return
        activeScope().launch {
            stats.recordBlockedRequest(isTracker)
        }
    }

    fun recordBlockedRequest(url: String) {
        val stats = blockedRequestStats ?: return
        val tracker = isTracker(url)
        activeScope().launch {
            stats.recordBlockedRequest(tracker)
        }
    }

    fun flushBlockedStats() {
        val stats = blockedRequestStats ?: return
        activeScope().launch {
            stats.flush()
        }
    }

    fun resetForTests() {
        synchronized(this) {
            initJob?.cancel()
            initJob = null
            HostListMatcher.reset()
            engineStore.swap(null)
            backend = NativeBackend.DEFAULT
            AdblockLatency.enabled = false
            AdblockLatency.reset()
            isInitialized = false
            blockedRequestStats = null
            UrlParser.reset()
        }
    }
}
