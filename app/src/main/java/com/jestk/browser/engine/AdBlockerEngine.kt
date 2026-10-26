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
        try {
            val assetList = context.assets.list("")
            if (assetList?.contains("adblock_domains.txt") == true) {
                context.assets.open("adblock_domains.txt").use { inputStream ->
                    loadPrecompiledDomainsFromStream(inputStream)
                }
                isInitialized = true
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load precompiled rules, falling back to raw assets", e)
        }

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

    fun loadPrecompiledDomainsFromStream(inputStream: InputStream) {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
            val buffer = ArrayList<String>(1000)
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    buffer.add(trimmed)
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
    private fun isValidHostnameChar(c: Char): Boolean =
        (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '-' || c == '_'

    private fun validateAndFormatDomain(line: String, start: Int, end: Int): String? {
        if (start >= end) return null
        val firstChar = line[start]
        if (firstChar == '.' || firstChar == '-') return null
        val lastChar = line[end - 1]
        if (lastChar == '.' || lastChar == '-') return null

        var hasDot = false
        for (i in start until end) {
            val c = line[i]
            if (c == '.') {
                hasDot = true
            } else if (!isValidHostnameChar(c)) {
                return null
            }
        }
        if (!hasDot) return null
        return line.substring(start, end).lowercase()
    }

    /**
     * Parses ABP domain rules:
     * - Extracts rules starting with `||` and ending with `^` or `^$...`, e.g. `||doubleclick.net^` -> `doubleclick.net`.
     * - Ignores comments (`!`, `[`), element hiding rules (`##`, `#?#`), and whitelist rules (`@@`).
     */
    fun extractDomainFromAbpRule(line: String): String? {
        var start = 0
        val len = line.length
        while (start < len && line[start].isWhitespace()) start++
        var end = len
        while (end > start && line[end - 1].isWhitespace()) end--
        if (start >= end) return null

        val firstChar = line[start]
        if (firstChar == '!' || firstChar == '[' || firstChar == '@') return null
        if (end - start < 4 || line[start] != '|' || line[start + 1] != '|') return null

        val caretIndex = line.indexOf('^', start + 2)
        if (caretIndex == -1 || caretIndex >= end) return null

        val afterCaretStart = caretIndex + 1
        if (afterCaretStart < end && line[afterCaretStart] != '$') return null

        var domainStart = start + 2
        var domainEnd = caretIndex
        while (domainStart < domainEnd && line[domainStart].isWhitespace()) domainStart++
        while (domainEnd > domainStart && line[domainEnd - 1].isWhitespace()) domainEnd--

        return validateAndFormatDomain(line, domainStart, domainEnd)
    }

    /**
     * Parses standard /etc/hosts format lines (e.g. "0.0.0.0 domain.com" or "domain.com")
     */
    fun extractDomainFromHostsLine(line: String): String? {
        var start = 0
        val len = line.length
        while (start < len && line[start].isWhitespace()) start++
        var end = len
        while (end > start && line[end - 1].isWhitespace()) end--
        if (start >= end) return null

        val firstChar = line[start]
        if (firstChar == '#' || firstChar == '!' || firstChar == '[' || firstChar == '@') {
            return null
        }

        var i = start
        while (i < end) {
            if (line[i] == '#' && i + 1 < end && (line[i + 1] == '#' || (i + 2 < end && line[i + 1] == '?' && line[i + 2] == '#'))) {
                return null
            }
            i++
        }

        var token0End = start
        while (token0End < end && !line[token0End].isWhitespace()) {
            token0End++
        }

        val isIp = (token0End - start == 9 && line.regionMatches(start, "127.0.0.1", 0, 9)) ||
            (token0End - start == 7 && line.regionMatches(start, "0.0.0.0", 0, 7))

        val domainStart: Int
        val domainEnd: Int

        if (isIp) {
            var token1Start = token0End
            while (token1Start < end && line[token1Start].isWhitespace()) {
                token1Start++
            }
            if (token1Start >= end) return null
            var token1End = token1Start
            while (token1End < end && !line[token1End].isWhitespace()) {
                token1End++
            }
            domainStart = token1Start
            domainEnd = token1End
        } else {
            domainStart = start
            domainEnd = token0End
        }

        return validateAndFormatDomain(line, domainStart, domainEnd)
    }

    data class ParsedUrl(
        val host: String,
        val path: String,
        val rawUrl: String,
    )

    private val lastParsedUrl = ThreadLocal<ParsedUrl?>()

    fun parseUrl(url: String?): ParsedUrl? {
        if (url.isNullOrBlank()) return null
        val cached = lastParsedUrl.get()
        if (cached != null && (cached.rawUrl === url || cached.rawUrl == url)) {
            return cached
        }
        val parsed = extractHostAndPath(url)
        lastParsedUrl.set(parsed)
        return parsed
    }

    internal fun extractHostAndPath(url: String): ParsedUrl? {
        var start = 0
        val len = url.length
        while (start < len && url[start] <= ' ') {
            start++
        }
        var end = len
        while (end > start && url[end - 1] <= ' ') {
            end--
        }
        if (start >= end) return null

        if (url.regionMatches(start, "data:", 0, 5, ignoreCase = true) ||
            url.regionMatches(start, "javascript:", 0, 11, ignoreCase = true) ||
            url.regionMatches(start, "about:", 0, 6, ignoreCase = true) ||
            url.regionMatches(start, "file:", 0, 5, ignoreCase = true) ||
            url.regionMatches(start, "content:", 0, 8, ignoreCase = true) ||
            url.regionMatches(start, "mailto:", 0, 7, ignoreCase = true) ||
            url.regionMatches(start, "tel:", 0, 4, ignoreCase = true) ||
            url.regionMatches(start, "sms:", 0, 4, ignoreCase = true) ||
            url.regionMatches(start, "geo:", 0, 4, ignoreCase = true)
        ) {
            return ParsedUrl(host = "", path = "", rawUrl = url)
        }

        var hostStart = -1
        if (start + 1 < end && url[start] == '/' && url[start + 1] == '/') {
            hostStart = start + 2
        } else {
            val schemeDelim = url.indexOf("://", start)
            if (schemeDelim != -1 && schemeDelim < end) {
                var hasBreakBeforeScheme = false
                for (i in start until schemeDelim) {
                    val c = url[i]
                    if (c == '/' || c == '?' || c == '#') {
                        hasBreakBeforeScheme = true
                        break
                    }
                }
                if (!hasBreakBeforeScheme) {
                    hostStart = schemeDelim + 3
                }
            }
        }

        if (hostStart == -1) {
            if (url[start] == '/') {
                return ParsedUrl(host = "", path = "", rawUrl = url)
            }
            hostStart = start
        }

        for (i in hostStart until end) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') break
            if (c == '@') {
                hostStart = i + 1
                break
            }
        }

        var hostEnd = end
        var pathStart = -1

        if (hostStart < end && url[hostStart] == '[') {
            val closeBracket = url.indexOf(']', hostStart)
            if (closeBracket != -1 && closeBracket < end) {
                hostEnd = closeBracket + 1
            }
        } else {
            for (i in hostStart until end) {
                val c = url[i]
                if (c == ':' || c == '/' || c == '?' || c == '#') {
                    hostEnd = i
                    break
                }
            }
        }

        if (hostEnd <= hostStart) {
            return ParsedUrl(host = "", path = "", rawUrl = url)
        }

        for (i in hostEnd until end) {
            val c = url[i]
            if (c == '/') {
                pathStart = i
                break
            } else if (c == '?' || c == '#') {
                break
            }
        }

        val host = run {
            var hasUpper = false
            for (i in hostStart until hostEnd) {
                val c = url[i]
                if (c in 'A'..'Z') {
                    hasUpper = true
                    break
                }
            }
            if (hasUpper) {
                url.substring(hostStart, hostEnd).lowercase()
            } else {
                url.substring(hostStart, hostEnd)
            }
        }

        val path = if (pathStart != -1) {
            var pathEnd = end
            for (i in pathStart until end) {
                val c = url[i]
                if (c == '?' || c == '#') {
                    pathEnd = i
                    break
                }
            }
            var hasUpper = false
            for (i in pathStart until pathEnd) {
                val c = url[i]
                if (c in 'A'..'Z') {
                    hasUpper = true
                    break
                }
            }
            if (hasUpper) {
                url.substring(pathStart, pathEnd).lowercase()
            } else {
                url.substring(pathStart, pathEnd)
            }
        } else {
            ""
        }

        return ParsedUrl(host = host, path = path, rawUrl = url)
    }

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val parsed = parseUrl(url) ?: return false
        return shouldBlock(parsed)
    }

    fun shouldBlock(parsed: ParsedUrl): Boolean {
        val host = parsed.host
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
        val path = parsed.path
        if (path.isNotEmpty() && isBlockedPath(host, path)) {
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

    private val TRACKER_DOMAINS = arrayOf(
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

    /**
     * Determines whether a given URL belongs to an analytics, tracker, telemetry, or beacon service.
     * Examples: google-analytics.com, mc.yandex.ru, scorecardresearch.com, quantserve.com, adfstat, metrika, beacon, pixel, etc.
     */
    fun isTracker(url: String): Boolean {
        if (url.isBlank()) return false
        val parsed = parseUrl(url) ?: return false
        return isTracker(parsed)
    }

    @JvmName("isTrackerNullable")
    fun isTracker(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val parsed = parseUrl(url) ?: return false
        return isTracker(parsed)
    }

    fun isTracker(parsed: ParsedUrl): Boolean {
        return isTracker(parsed.host, parsed.path, parsed.rawUrl)
    }

    fun isTracker(
        host: String,
        path: String,
        rawUrl: String = "",
    ): Boolean {
        if (host.isEmpty() && path.isEmpty() && rawUrl.isEmpty()) return false

        // Known tracker & analytics domains
        if (host.isNotEmpty()) {
            for (td in TRACKER_DOMAINS) {
                if (host == td || host.endsWith(".$td")) {
                    return true
                }
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
        }

        // Path or URL fragments indicating beacons, telemetry, pixels, collectors
        if (path.isNotEmpty()) {
            if (path.contains("/collect") ||
                path.contains("/beacon") ||
                path.contains("/analytics") ||
                path.contains("/telemetry") ||
                path.contains("/pixel") ||
                path.contains("/metrika") ||
                path.contains("/track")
            ) {
                return true
            }
        }

        if (rawUrl.isNotEmpty()) {
            if (rawUrl.contains("beacon", ignoreCase = true) ||
                rawUrl.contains("pixel", ignoreCase = true) ||
                rawUrl.contains("telemetry", ignoreCase = true) ||
                rawUrl.contains("adfstat", ignoreCase = true) ||
                rawUrl.contains("metrika", ignoreCase = true)
            ) {
                return true
            }
        }

        return false
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
            blockedHosts.clear()
            blockedHosts.addAll(DEFAULT_HOSTS)
            isInitialized = false
            blockedRequestStats = null
            lastParsedUrl.remove()
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
private sealed interface HostBucket {
    fun matches(host: String, fromIndex: Int, length: Int): Boolean
    fun withHost(newHost: String): HostBucket

    class Single(val host: String) : HostBucket {
        override fun matches(host: String, fromIndex: Int, length: Int): Boolean =
            this.host.length == length && host.regionMatches(fromIndex, this.host, 0, length)

        override fun withHost(newHost: String): HostBucket =
            Multi(arrayOf(host, newHost))
    }

    class Multi(val hosts: Array<String>) : HostBucket {
        override fun matches(host: String, fromIndex: Int, length: Int): Boolean {
            for (h in hosts) {
                if (h.length == length && host.regionMatches(fromIndex, h, 0, length)) {
                    return true
                }
            }
            return false
        }

        override fun withHost(newHost: String): HostBucket {
            val newArr = java.util.Arrays.copyOf(hosts, hosts.size + 1)
            newArr[hosts.size] = newHost
            return Multi(newArr)
        }
    }
}

private class HostBlocklist {
    private val exactHosts = ConcurrentHashMap.newKeySet<String>(65536)
    private val hashBuckets = ConcurrentHashMap<Int, HostBucket>(65536)

    val size: Int get() = exactHosts.size

    fun add(host: String) {
        if (!exactHosts.add(host)) return
        hashBuckets.compute(host.hashCode()) { _, existing ->
            existing?.withHost(host) ?: HostBucket.Single(host)
        }
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
        val bucket = hashBuckets[hashRegion(host, fromIndex)] ?: return false
        return bucket.matches(host, fromIndex, host.length - fromIndex)
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
