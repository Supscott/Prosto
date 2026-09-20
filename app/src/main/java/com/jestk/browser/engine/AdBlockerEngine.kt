package com.jestk.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object AdBlockerEngine {

    private const val TAG = "AdBlockerEngine"

    val DEFAULT_HOSTS: Set<String> = setOf(
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
        "moatads.com"
    )

    private val blockedHosts = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(DEFAULT_HOSTS)
    }

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
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Job {
        val currentJob = initJob
        if (currentJob != null) return currentJob

        return synchronized(this) {
            initJob ?: run {
                val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
                val job = CoroutineScope(dispatcher + SupervisorJob()).launch {
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
        val hostCandidate = if (parts.size >= 2 && (parts[0] == "127.0.0.1" || parts[0] == "0.0.0.0")) {
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

        val host = try {
            val normalizedUrl = when {
                trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
                !trimmedUrl.contains("://") -> "https://$trimmedUrl"
                else -> trimmedUrl
            }
            Uri.parse(normalizedUrl).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false

        if (host.isEmpty()) return false

        if (blockedHosts.contains(host)) return true

        // Check subdomain chains: e.g. "ads.sub.example.com" -> "sub.example.com" -> "example.com"
        var currentHost = host
        while (currentHost.contains('.')) {
            val nextDotIndex = currentHost.indexOf('.')
            if (nextDotIndex == -1 || nextDotIndex == currentHost.lastIndex) break
            currentHost = currentHost.substring(nextDotIndex + 1)
            if (blockedHosts.contains(currentHost)) {
                return true
            }
        }

        return false
    }

    // Minimal 1x1 32-bit transparent PNG to fulfill blocked image requests cleanly
    val TRANSPARENT_1X1_PNG: ByteArray by lazy {
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            Base64.DEFAULT
        )
    }

    fun createBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

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
        requestHeaders: Map<String, String>? = null
    ): WebResourceResponse {
        val cleanUrl = url?.lowercase()?.trim().orEmpty()
        val acceptHeader = requestHeaders?.get("Accept")?.lowercase()
            ?: requestHeaders?.get("accept")?.lowercase()
            ?: ""

        val isImage = mimeTypeHint?.startsWith("image/") == true ||
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
                ByteArrayInputStream(TRANSPARENT_1X1_PNG)
            )
        }

        val isScript = mimeTypeHint?.contains("javascript") == true ||
            acceptHeader.contains("javascript") ||
            cleanUrl.endsWith(".js") ||
            cleanUrl.contains(".js?") ||
            cleanUrl.contains("/adsbygoogle") ||
            cleanUrl.contains("/gpt.js")

        if (isScript) {
            val stubContent = when {
                cleanUrl.contains("adsbygoogle") -> {
                    "window.adsbygoogle = window.adsbygoogle || []; window.adsbygoogle.loaded = true; window.adsbygoogle.push = function(o){return 0;};"
                }
                cleanUrl.contains("gpt.js") || cleanUrl.contains("pubads") -> {
                    "window.googletag = window.googletag || {cmd:[]}; window.googletag.cmd.push = function(f){if(typeof f==='function'){try{f();}catch(e){}}};"
                }
                else -> "/* blocked by Hori */"
            }
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(stubContent.toByteArray(Charsets.UTF_8))
            )
        }

        val isBeaconOrAnalytics = cleanUrl.contains("/collect") ||
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
                ByteArrayInputStream(ByteArray(0))
            )
        }

        val isHtml = mimeTypeHint?.contains("html") == true ||
            cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm")

        if (isHtml) {
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream("<!DOCTYPE html><html><body></body></html>".toByteArray(Charsets.UTF_8))
            )
        }

        return createBlockedResponse()
    }

    fun isReady(): Boolean = isInitialized

    fun getRulesCount(): Int = blockedHosts.size

    fun containsHost(host: String): Boolean = blockedHosts.contains(host.lowercase())

    fun resetForTests() {
        synchronized(this) {
            initJob?.cancel()
            initJob = null
            blockedHosts.clear()
            blockedHosts.addAll(DEFAULT_HOSTS)
            isInitialized = false
        }
    }
}
