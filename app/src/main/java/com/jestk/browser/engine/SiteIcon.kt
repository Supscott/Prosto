package com.jestk.browser.engine

import android.net.Uri

/**
 * Where a site's icon is looked up when the page reported none (PRO-184).
 *
 * The icon always comes from the site itself: `<scheme>://<host>[:port]/apple-touch-icon.png`, then
 * `/favicon.ico` (see [candidateUrls]). No third-party favicon service is contacted, in any tab.
 *
 * A reference is a plain string so it can sit in the same database columns as a file path:
 * `siteicon:https://example.com`. Rows written by older versions hold a Google `s2` favicon URL
 * instead; [originOfRef] maps those to the site chain at load time, so they need no migration and
 * the Google URL is never requested.
 */
object SiteIcon {
    private const val PREFIX = "siteicon:"
    private const val LEGACY_S2_PATH = "/s2/favicons"
    private val LEGACY_S2_HOSTS = setOf("www.google.com", "google.com")
    private val PRIVATE_SUFFIXES = listOf(".local", ".localhost", ".lan", ".internal", ".home.arpa")

    /** The reference to store for a page, or null when the page has no public http(s) origin. */
    fun refForPage(url: String?): String? = originOf(url)?.let { PREFIX + it }

    /** `scheme://host[:port]` of a public http(s) page, else null (private and LAN hosts are skipped). */
    fun originOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        if (isPrivateHost(host)) return null
        val port = uri.port
        return if (port == -1) "$scheme://$host" else "$scheme://$host:$port"
    }

    /** True for a stored value that this class resolves: a site reference or a legacy Google URL. */
    fun isRef(value: String?): Boolean = isSiteRef(value) || isLegacyS2(value)

    /** The origin a stored [value] points at, or null (also for a legacy URL of a private host). */
    fun originOfRef(value: String?): String? =
        when {
            value == null -> null
            isSiteRef(value) -> originOf(value.removePrefix(PREFIX))
            isLegacyS2(value) -> legacyS2Domain(value)?.let { originOf("https://$it") }
            else -> null
        }

    /** The URLs to try, in order: a sharp touch icon first, then the classic favicon. */
    fun candidateUrls(origin: String): List<String> = listOf("$origin/apple-touch-icon.png", "$origin/favicon.ico")

    /** The host shown on a letter tile: no scheme, port or `www.`; works for private hosts too. */
    fun displayHost(urlOrRef: String?): String {
        if (urlOrRef.isNullOrBlank()) return ""
        val raw = urlOrRef.trim().removePrefix(PREFIX)
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val host = runCatching { Uri.parse(withScheme).host }.getOrNull() ?: raw
        return host.lowercase().removePrefix("www.")
    }

    /** The one letter of a tile: the first character of [displayHost], upper-cased, or `?`. */
    fun letterFor(urlOrRef: String?): String {
        val first = displayHost(urlOrRef).firstOrNull() ?: return "?"
        return first.uppercaseChar().toString()
    }

    /** Index into a palette of [paletteSize] colours, stable for a host. */
    fun colorIndexFor(urlOrRef: String?, paletteSize: Int): Int =
        Math.floorMod(displayHost(urlOrRef).hashCode(), paletteSize)

    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.startsWith("[")) return true
        if (PRIVATE_SUFFIXES.any { host.endsWith(it) }) return true
        if (!host.contains('.')) return true
        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null }) {
            val a = octets[0]!!
            val b = octets[1]!!
            return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168)
        }
        return false
    }

    private fun isSiteRef(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    private fun isLegacyS2(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
        return uri.host?.lowercase() in LEGACY_S2_HOSTS && uri.path == LEGACY_S2_PATH
    }

    private fun legacyS2Domain(value: String): String? {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
        return uri.getQueryParameter("domain")?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("domain_url")?.let { displayHostKeepingWww(it) }
    }

    private fun displayHostKeepingWww(url: String): String? {
        val withScheme = if (url.contains("://")) url else "https://$url"
        return runCatching { Uri.parse(withScheme).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
