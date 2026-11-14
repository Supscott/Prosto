package com.jestk.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import com.jestk.browser.domain.repository.BlockedRequestStats
import com.jestk.browser.engine.adblock.AuthDomainAllowlist
import com.jestk.browser.engine.adblock.BlockedResponses
import com.jestk.browser.engine.adblock.HostListMatcher
import com.jestk.browser.engine.adblock.ParsedUrl
import com.jestk.browser.engine.adblock.UrlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream

object AdBlockerEngine {
    private const val TAG = "AdBlockerEngine"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var initJob: Job? = null

    /**
     * Initializes the ad blocker engine asynchronously on Dispatchers.IO.
     * Cold start remains instant because blockedHosts is initially populated with DEFAULT_HOSTS.
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
                        loadRulesFromAssets(appContext)
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

    fun shouldBlock(url: String?, documentUrl: String? = null): Boolean = HostListMatcher.shouldBlock(url, documentUrl)

    fun shouldBlock(parsed: ParsedUrl, documentUrl: String? = null): Boolean =
        HostListMatcher.shouldBlock(parsed, documentUrl)

    fun shouldBlockNavigation(url: String?): Boolean = HostListMatcher.shouldBlockNavigation(url)

    fun isBlockedPath(host: String, path: String): Boolean = HostListMatcher.isBlockedPath(host, path)

    internal fun isAllowlistedAuthDomain(host: String): Boolean = AuthDomainAllowlist.isAllowlistedAuthDomain(host)

    val TRANSPARENT_1X1_PNG: ByteArray get() = BlockedResponses.TRANSPARENT_1X1_PNG

    fun createBlockedResponse() = BlockedResponses.createBlockedResponse()

    fun createSmartBlockedResponse(
        url: String?,
        mimeTypeHint: String? = null,
        requestHeaders: Map<String, String>? = null,
    ) = BlockedResponses.createSmartBlockedResponse(url, mimeTypeHint, requestHeaders)

    fun getRulesCount(): Int = HostListMatcher.getRulesCount()

    fun containsHost(host: String): Boolean = HostListMatcher.containsHost(host)

    fun isTracker(url: String, documentUrl: String? = null): Boolean = HostListMatcher.isTracker(url, documentUrl)

    @JvmName("isTrackerNullable")
    fun isTracker(url: String?, documentUrl: String? = null): Boolean = HostListMatcher.isTracker(url, documentUrl)

    fun isTracker(parsed: ParsedUrl, documentUrl: String? = null): Boolean =
        HostListMatcher.isTracker(parsed, documentUrl)

    fun isReady(): Boolean = isInitialized

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
            isInitialized = false
            blockedRequestStats = null
            UrlParser.reset()
        }
    }
}
