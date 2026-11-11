package com.jestk.browser.ui.viewmodel

import java.net.URI

/**
 * Whether address bar input is a URL rather than a search query. The single rule shared by
 * navigation and preconnect, so both agree on what the user is about to open.
 */
internal fun looksLikeUrlInput(trimmed: String): Boolean =
    trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("localhost") ||
        (trimmed.contains(".") && !trimmed.contains(" ")) ||
        (trimmed.contains(":") && !trimmed.contains(" "))

/**
 * Picks the origin worth preconnecting to while the user types in the address bar, like Chrome's
 * omnibox: the typed host when the input looks like a URL, otherwise the search engine. A
 * preconnect only opens DNS + TCP + TLS; no request, cookie or page content is sent.
 *
 * Each origin is preconnected at most once per [minIntervalMs]: an idle socket stays open for a
 * while, and repeating it on every keystroke would only add load.
 */
internal class PreconnectPlanner(
    private val now: () -> Long,
    private val minIntervalMs: Long = 30_000L,
) {
    private val lastPreconnectAt = HashMap<String, Long>()

    fun originToPreconnect(query: String, searchUrlFor: (String) -> String): String? {
        val trimmed = query.trim()
        if (trimmed.length < 2) return null
        val isUrl = looksLikeUrlInput(trimmed)
        val target = when {
            !isUrl -> searchUrlFor(trimmed)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
        // Only the origin matters; parsing just scheme://authority tolerates unencoded queries.
        val schemeEnd = target.indexOf("://")
        if (schemeEnd <= 0) return null
        val authority = target.substring(schemeEnd + 3).takeWhile { it !in "/?# " }.substringAfterLast('@')
        val uri = runCatching { URI(target.substring(0, schemeEnd) + "://" + authority) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase() ?: return null
        if (isUrl && !isCompletePublicHost(host)) return null
        val origin = if (uri.port > 0) "$scheme://$host:${uri.port}" else "$scheme://$host"

        val at = now()
        val last = lastPreconnectAt[origin]
        if (last != null && at - last < minIntervalMs) return null
        lastPreconnectAt[origin] = at
        return origin
    }

    /** "github.com" yes; "github.c", "localhost" and IP literals no (half-typed or local). */
    private fun isCompletePublicHost(host: String): Boolean {
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return false
        val tld = labels.last()
        return tld.length >= 2 && tld.all { it.isLetter() }
    }
}
