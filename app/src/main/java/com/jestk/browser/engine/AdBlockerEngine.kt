package com.jestk.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceResponse
import com.jestk.browser.domain.repository.BlockedRequestStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object AdBlockerEngine {
    private const val TAG = "AdBlockerEngine"

    val DEFAULT_HOSTS: Set<String> =
        setOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "google-analytics.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "taboola.net",
            "outbrain.com",
            "outbrainimg.com",
            "adnxs.com",
            "rubiconproject.com",
            "scorecardresearch.com",
            "quantserve.com",
            "pubmatic.com",
            "openx.net",
            "moatads.com",
            // Russian ad & tracking networks (Yandex, Mail.ru, MyTarget, ADFOX, X5 Media, Rambler, etc.)
            "adfox.ru",
            "adfox.yandex.ru",
            "ad.mail.ru",
            "ads.mail.ru",
            "target.mail.ru",
            "top-fwz1.mail.ru",
            "my.target.com",
            "target.my.com",
            "ads.x5media.ru",
            "x5media.ru",
            "an.yandex.ru",
            "yabs.yandex.ru",
            "mc.yandex.ru",
            "awaps.yandex.ru",
            "adfstat.yandex.ru",
            "bs.yandex.ru",
            "ads.vk.com",
            "ssp.rambler.ru",
            "reklama.rambler.ru",
            "relap.io",
            "buzzoola.com",
            "rutarget.ru",
            "hybrid.ai",
            "bidvol.com",
            "adhigh.net",
            "adriver.ru",
            "otclick-adv.ru",
            "videonow.ru",
            "viqeo.tv",
        )

    private val blockedHosts = HostBlocklist().apply { addAll(DEFAULT_HOSTS) }

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
        val assetFiles = listOf("adblock_hosts.txt", "EasyList.txt", "EasyListCookie.txt")
        for (fileName in assetFiles) {
            try {
                context.assets.open(fileName).use { inputStream ->
                    loadRulesFromStream(inputStream)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load rules from asset: $fileName", e)
            }
        }
        isInitialized = true
    }

    fun loadRulesFromStream(inputStream: InputStream) {
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            val buffer = mutableListOf<String>()
            lines.forEach { rawLine ->
                val domain = extractDomainFromAbpRule(rawLine) ?: extractDomainFromHostsLine(rawLine)
                if (domain != null) {
                    buffer.add(domain)
                    if (buffer.size >= 1000) {
                        blockedHosts.addAll(buffer)
                        buffer.clear()
                    }
                }
            }
            if (buffer.isNotEmpty()) {
                blockedHosts.addAll(buffer)
            }
        }
    }

    /**
     * Parses ABP domain rules:
     * - Extracts rules starting with `||` and ending with `^` or `^$...`, e.g. `||doubleclick.net^` -> `doubleclick.net`.
     * - Ignores comments (`!`, `[`), element hiding rules (`##`, `#?#`), and whitelist rules (`@@`).
     */
    fun extractDomainFromAbpRule(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() ||
            trimmed.startsWith("!") ||
            trimmed.startsWith("[") ||
            trimmed.startsWith("@@") ||
            trimmed.contains("##") ||
            trimmed.contains("#?#")
        ) {
            return null
        }

        if (!trimmed.startsWith("||")) {
            return null
        }

        val caretIndex = trimmed.indexOf('^')
        if (caretIndex == -1) return null

        val afterCaret = trimmed.substring(caretIndex + 1)
        if (afterCaret.isNotEmpty() && !afterCaret.startsWith("$")) {
            return null
        }

        val domainCandidate = trimmed.substring(2, caretIndex).trim().lowercase()
        if (domainCandidate.isEmpty()) return null

        // Domain must not contain paths, wildcards, ports, query params, or extra separators
        if (domainCandidate.contains('/') ||
            domainCandidate.contains('*') ||
            domainCandidate.contains(':') ||
            domainCandidate.contains('?') ||
            domainCandidate.contains('^')
        ) {
            return null
        }

        // Hostname characters: lowercase letters, digits, dots, hyphens, underscores
        if (!domainCandidate.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) {
            return null
        }

        // Must contain a dot and not start or end with a dot or hyphen
        if (!domainCandidate.contains('.') ||
            domainCandidate.startsWith('.') ||
            domainCandidate.endsWith('.') ||
            domainCandidate.startsWith('-') ||
            domainCandidate.endsWith('-')
        ) {
            return null
        }

        return domainCandidate
    }

    /**
     * Parses standard /etc/hosts format lines (e.g. "0.0.0.0 domain.com" or "domain.com")
     */
    fun extractDomainFromHostsLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() ||
            trimmed.startsWith("#") ||
            trimmed.startsWith("!") ||
            trimmed.startsWith("[") ||
            trimmed.startsWith("@@") ||
            trimmed.contains("##") ||
            trimmed.contains("#?#")
        ) {
            return null
        }
        val parts = trimmed.split("\\s+".toRegex())
        val hostCandidate =
            if (parts.size >= 2 && (parts[0] == "127.0.0.1" || parts[0] == "0.0.0.0")) {
                parts[1].lowercase()
            } else {
                parts[0].lowercase()
            }

        if (hostCandidate.contains('.') &&
            !hostCandidate.contains('/') &&
            !hostCandidate.contains('*') &&
            !hostCandidate.contains(':') &&
            !hostCandidate.startsWith('.') &&
            !hostCandidate.endsWith('.') &&
            !hostCandidate.startsWith('-') &&
            !hostCandidate.endsWith('-')
        ) {
            return hostCandidate
        }
        return null
    }

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmedUrl = url.trim()
        if (trimmedUrl.startsWith("data:", ignoreCase = true) ||
            trimmedUrl.startsWith("javascript:", ignoreCase = true) ||
            trimmedUrl.startsWith("about:", ignoreCase = true) ||
            trimmedUrl.startsWith("file:", ignoreCase = true) ||
            trimmedUrl.startsWith("content:", ignoreCase = true)
        ) {
            return false
        }

        val uri =
            try {
                val normalizedUrl =
                    when {
                        trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
                        !trimmedUrl.contains("://") -> "https://$trimmedUrl"
                        else -> trimmedUrl
                    }
                Uri.parse(normalizedUrl)
            } catch (_: Exception) {
                null
            } ?: return false

        val host = uri.host?.lowercase() ?: return false
        if (host.isEmpty()) return false

        if (blockedHosts.contains(host)) return true

        // Check subdomain chains: e.g. "ads.sub.example.com" -> "sub.example.com" -> "example.com".
        // The chain is walked with a moving index instead of a moving substring: every candidate
        // suffix is compared in place inside the blocklist, so no intermediate string is allocated
        // for the (overwhelmingly common) non-matching case.
        var suffixStart = 0
        while (true) {
            val dotIndex = host.indexOf('.', suffixStart)
            // A trailing dot terminates the chain: there is no further suffix left to test.
            if (dotIndex == -1 || dotIndex == host.length - 1) break
            suffixStart = dotIndex + 1
            if (blockedHosts.containsSuffix(host, suffixStart)) {
                return true
            }
        }

        // Check path rules for multi-purpose search engines/portals serving ads under subpaths
        val path = uri.path?.lowercase().orEmpty()
        if (isBlockedPath(host, path)) {
            return true
        }

        return false
    }

    fun isBlockedPath(
        host: String,
        path: String,
    ): Boolean {
        if (host.contains("yandex.")) {
            if (path.startsWith("/ads/") ||
                path.startsWith("/clck/") ||
                path.contains("context.js") ||
                path.contains("header-bidding.js")
            ) {
                return true
            }
        }

        if (host.contains("mail.ru") || host.contains("vk.com")) {
            if (path.startsWith("/static/ads") ||
                path.startsWith("/ads-async.js") ||
                path.startsWith("/ads/") ||
                path.startsWith("/ads_")
            ) {
                return true
            }
        }

        if (host.contains("rambler.ru")) {
            if (path.startsWith("/ads/") || path.startsWith("/banner")) {
                return true
            }
        }

        if (path.endsWith("/ads.js") ||
            path.endsWith("/ad.js") ||
            path.endsWith("/advert.js") ||
            path.contains("/ads/system/")
        ) {
            return true
        }

        return false
    }

    // Minimal 1x1 32-bit transparent PNG to fulfill blocked image requests cleanly
    val TRANSPARENT_1X1_PNG: ByteArray by lazy {
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
    }

    fun createBlockedResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0)),
        )

    /**
     * Creates a content-aware blocked response tailored to the resource type:
     * - Images: Returns a 1x1 transparent PNG to eliminate broken image icons and avoid layout shift.
     * - Scripts: Returns application/javascript with a comment or harmless stub to stop syntax errors.
     * - Beacons/Analytics: Returns HTTP 204 No Content.
     * - HTML: Returns an empty HTML document.
     * - Fallback: Returns empty text/plain.
     */
    fun createSmartBlockedResponse(
        url: String?,
        mimeTypeHint: String? = null,
        requestHeaders: Map<String, String>? = null,
    ): WebResourceResponse {
        val cleanUrl = url?.lowercase()?.trim().orEmpty()
        val acceptHeader =
            requestHeaders?.get("Accept")?.lowercase()
                ?: requestHeaders?.get("accept")?.lowercase()
                ?: ""

        val isImage =
            mimeTypeHint?.startsWith("image/") == true ||
                acceptHeader.contains("image/") ||
                cleanUrl.endsWith(".png") || cleanUrl.endsWith(".jpg") ||
                cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".gif") ||
                cleanUrl.endsWith(".webp") || cleanUrl.endsWith(".svg") ||
                cleanUrl.endsWith(".ico") || cleanUrl.contains("/pixel")

        if (isImage) {
            return WebResourceResponse(
                "image/png",
                null,
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(TRANSPARENT_1X1_PNG),
            )
        }

        val isScript =
            mimeTypeHint?.contains("javascript") == true ||
                acceptHeader.contains("javascript") ||
                cleanUrl.endsWith(".js") ||
                cleanUrl.contains(".js?") ||
                cleanUrl.contains("/adsbygoogle") ||
                cleanUrl.contains("/gpt.js") ||
                cleanUrl.contains("context.js") ||
                cleanUrl.contains("header-bidding") ||
                cleanUrl.contains("ads-async.js") ||
                cleanUrl.contains("sdk.standalone.js") ||
                cleanUrl.contains("adfox")

        if (isScript) {
            val stubContent =
                when {
                    cleanUrl.contains("adsbygoogle") -> {
                        "window.adsbygoogle = window.adsbygoogle || []; window.adsbygoogle.loaded = true; window.adsbygoogle.push = function(o){return 0;};"
                    }

                    cleanUrl.contains("gpt.js") || cleanUrl.contains("pubads") -> {
                        "window.googletag = window.googletag || {cmd:[]}; window.googletag.cmd.push = function(f){if(typeof f==='function'){try{f();}catch(e){}}};"
                    }

                    cleanUrl.contains("context.js") || cleanUrl.contains("header-bidding") || cleanUrl.contains("adfox") -> {
                        "window.yaContextCb = window.yaContextCb || []; window.Ya = window.Ya || {}; window.Ya.adfoxCode = window.Ya.adfoxCode || {create:function(c){if(c&&c.containerId){var e=document.getElementById(c.containerId);if(e){e.style.setProperty('display','none','important');e.style.setProperty('height','0','important');}}},createScroll:function(c){if(c&&c.containerId){var e=document.getElementById(c.containerId);if(e){e.style.setProperty('display','none','important');e.style.setProperty('height','0','important');}}},destroy:function(){},clearSession:function(){},reload:function(){},hbCallbacks:[]};"
                    }

                    cleanUrl.contains("sdk.standalone.js") || cleanUrl.contains("x5media") -> {
                        "window.x5media = window.x5media || {init:function(){return Promise.resolve();},render:function(){}};"
                    }

                    else -> {
                        "/* blocked by Prosto */"
                    }
                }
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(stubContent.toByteArray(Charsets.UTF_8)),
            )
        }

        val isBeaconOrAnalytics =
            cleanUrl.contains("/collect") ||
                cleanUrl.contains("/beacon") ||
                cleanUrl.contains("/analytics") ||
                cleanUrl.contains("/telemetry")

        if (isBeaconOrAnalytics) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                204,
                "No Content",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        val isHtml =
            mimeTypeHint?.contains("html") == true ||
                cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm")

        if (isHtml) {
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream("<!DOCTYPE html><html><body></body></html>".toByteArray(Charsets.UTF_8)),
            )
        }

        return createBlockedResponse()
    }

    fun isReady(): Boolean = isInitialized

    fun getRulesCount(): Int = blockedHosts.size

    fun containsHost(host: String): Boolean = blockedHosts.contains(host.lowercase())

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

    /**
     * Determines whether a given URL belongs to an analytics, tracker, telemetry, or beacon service.
     * Examples: google-analytics.com, mc.yandex.ru, scorecardresearch.com, quantserve.com, adfstat, metrika, beacon, pixel, etc.
     */
    fun isTracker(url: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrl = url.lowercase().trim()
        val uri =
            try {
                val normalized =
                    when {
                        cleanUrl.startsWith("//") -> "https:$cleanUrl"
                        !cleanUrl.contains("://") -> "https://$cleanUrl"
                        else -> cleanUrl
                    }
                Uri.parse(normalized)
            } catch (_: Exception) {
                null
            }

        val host = uri?.host?.lowercase().orEmpty()
        val path = uri?.path?.lowercase().orEmpty()

        // Known tracker & analytics domains
        val trackerDomains =
            listOf(
                "google-analytics.com",
                "scorecardresearch.com",
                "quantserve.com",
                "hotjar.com",
                "clarity.ms",
                "segment.io",
                "segment.com",
                "mixpanel.com",
                "amplitude.com",
                "statcounter.com",
                "counter.yadro.ru",
                "top-fwz1.mail.ru",
            )
        if (trackerDomains.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        // Host fragments specifically associated with telemetry / metrics
        if (host.contains("adfstat") ||
            host.contains("metrika") ||
            host.startsWith("mc.yandex.") ||
            host.contains(".metrika.") ||
            host.contains("analytics.") ||
            host.contains("telemetry.") ||
            host.contains("tracker.") ||
            host.contains("tracking.") ||
            host.contains("stats.")
        ) {
            return true
        }

        // Path or URL fragments indicating beacons, telemetry, pixels, collectors
        if (path.contains("/collect") ||
            path.contains("/beacon") ||
            path.contains("/analytics") ||
            path.contains("/telemetry") ||
            path.contains("/pixel") ||
            path.contains("/metrika") ||
            path.contains("/track") ||
            cleanUrl.contains("beacon") ||
            cleanUrl.contains("pixel") ||
            cleanUrl.contains("telemetry") ||
            cleanUrl.contains("adfstat") ||
            cleanUrl.contains("metrika")
        ) {
            return true
        }

        return false
    }

    @JvmName("isTrackerNullable")
    fun isTracker(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return isTracker(url as String)
    }

    fun recordBlockedRequest(url: String) {
        val stats = blockedRequestStats ?: return
        val tracker = isTracker(url)
        activeScope().launch {
            stats.recordBlockedRequest(tracker)
        }
    }

    fun resetForTests() {
        synchronized(this) {
            initJob?.cancel()
            initJob = null
            blockedHosts.clear()
            blockedHosts.addAll(DEFAULT_HOSTS)
            isInitialized = false
            blockedRequestStats = null
        }
    }
}

/**
 * Blocklist of hostnames that supports allocation-free suffix matching.
 *
 * Exact lookups use a plain hash set. Matching a subdomain ("ads.example.com" against the blocked
 * host "example.com") needs to test every suffix of a host, which used to allocate one substring
 * per candidate. To avoid that, the hosts are additionally bucketed by their own
 * [String.hashCode]: a candidate suffix is hashed over its character range with the very same
 * algorithm [String.hashCode] uses, so the bucket can be found without allocating a string, and
 * the few candidates of that bucket are then compared in place with [String.regionMatches].
 *
 * The instance is safe for concurrent use: rules are loaded on a background thread while the
 * matching runs on the main thread.
 */
private class HostBlocklist {
    private val exactHosts = ConcurrentHashMap.newKeySet<String>()
    private val hashBuckets = ConcurrentHashMap<Int, List<String>>()

    val size: Int get() = exactHosts.size

    fun add(host: String) {
        if (!exactHosts.add(host)) return
        hashBuckets.merge(host.hashCode(), listOf(host)) { existing, added -> existing + added }
    }

    fun addAll(hosts: Collection<String>) {
        for (host in hosts) {
            add(host)
        }
    }

    fun clear() {
        exactHosts.clear()
        hashBuckets.clear()
    }

    /** Returns true when [host] itself is blocked. */
    fun contains(host: String): Boolean = exactHosts.contains(host)

    /**
     * Returns true when the region of [host] that starts at [fromIndex] is a blocked host.
     * The region is compared in place, so no substring is allocated.
     */
    fun containsSuffix(
        host: String,
        fromIndex: Int,
    ): Boolean {
        if (fromIndex < 0 || fromIndex >= host.length) return false
        val candidates = hashBuckets[hashRegion(host, fromIndex)] ?: return false
        val length = host.length - fromIndex
        for (candidate in candidates) {
            if (candidate.length == length && host.regionMatches(fromIndex, candidate, 0, length)) {
                return true
            }
        }
        return false
    }

    /**
     * Hash of the region of [host] that starts at [fromIndex], computed exactly like
     * [String.hashCode] of that substring, so a region hash always matches the hash of the equal
     * string stored in the blocklist.
     */
    private fun hashRegion(
        host: String,
        fromIndex: Int,
    ): Int {
        var hash = 0
        for (index in fromIndex until host.length) {
            hash = 31 * hash + host[index].code
        }
        return hash
    }
}
