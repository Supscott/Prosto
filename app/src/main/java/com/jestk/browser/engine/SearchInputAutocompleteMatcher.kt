package com.jestk.browser.engine

import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.Tab

/**
 * Pure domain/engine matcher for search input inline autocomplete and open tab matching.
 * Adheres to domain-first prefix completion, whitespace suppression, and strict local ranking:
 * Open Tabs > Favorites > Browsing History.
 */
class SearchInputAutocompleteMatcher {

    companion object {
        private val SCHEME_WWW_REGEX = Regex("""^(https?://)?(www\.)?""", RegexOption.IGNORE_CASE)

        /**
         * Cleans a URL string by stripping http/https schemes and www. prefix (case-insensitively).
         */
        fun cleanUrl(url: String): String {
            return url.trim().replace(SCHEME_WWW_REGEX, "")
        }

        /**
         * Extracts the clean host/domain from a URL without scheme or www.
         */
        fun extractDomain(url: String): String {
            val cleaned = cleanUrl(url)
            return cleaned.substringBefore("/").substringBefore("?").substringBefore("#")
        }
    }

    /**
     * Cleans a URL string by stripping http/https schemes and www. prefix.
     */
    fun cleanUrl(url: String): String = Companion.cleanUrl(url)

    /**
     * Extracts the clean host/domain from a URL without scheme or www.
     */
    fun extractDomain(url: String): String = Companion.extractDomain(url)

    /**
     * Finds the highest-ranked inline autocomplete candidate for [query].
     * Returns null if query is blank, contains whitespace (\s), or has no eligible candidate.
     */
    fun findInlineMatch(
        query: String,
        tabs: List<Tab>,
        favorites: List<FavoriteItem>,
        history: List<HistoryItem>,
    ): String? {
        if (query.isBlank() || query.any { it.isWhitespace() }) {
            return null
        }

        val hasSlash = query.contains("/")

        // Prioritized candidates sequence: Open Tabs -> Favorites -> Browsing History
        val candidateUrls = sequence {
            val sortedTabs = tabs.sortedByDescending { it.lastActiveTimestamp }
            for (tab in sortedTabs) {
                yield(tab.url)
            }
            for (fav in favorites) {
                yield(fav.url)
            }
            val sortedHistory = history.sortedByDescending { it.timestamp }
            for (item in sortedHistory) {
                yield(item.url)
            }
        }

        for (url in candidateUrls) {
            val candidate = getCandidate(url, hasSlash)
            val match = matchPrefix(query, candidate)
            if (match != null) return match
        }

        return null
    }

    /**
     * Returns all open tabs matching [query] (excluding the current tab if filtered by caller)
     * for display in the "Switch to Tab" carousel.
     * Matches against either clean domain/URL prefix or tab title.
     */
    fun findMatchingOpenTabs(
        query: String,
        tabs: List<Tab>,
    ): List<Tab> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }

        val hasSlash = trimmed.contains("/")
        val sortedTabs = tabs.sortedByDescending { it.lastActiveTimestamp }

        return sortedTabs.filter { tab ->
            val candidate = getCandidate(tab.url, hasSlash)
            val urlMatches = candidate.startsWith(trimmed, ignoreCase = true)
            val titleMatches = tab.title.isNotBlank() && tab.title.contains(trimmed, ignoreCase = true)
            urlMatches || titleMatches
        }
    }

    private fun getCandidate(url: String, hasSlash: Boolean): String {
        val cleaned = cleanUrl(url)
        return if (hasSlash) {
            val host = cleaned.substringBefore("/").lowercase()
            val pathAndQuery = cleaned.substringAfter("/", "")
            val path = pathAndQuery.substringBefore("?").substringBefore("#")
            if (path.isEmpty()) "$host/" else "$host/$path"
        } else {
            cleaned.substringBefore("/").substringBefore("?").substringBefore("#").lowercase()
        }
    }

    private fun matchPrefix(query: String, candidate: String): String? {
        if (candidate.startsWith(query, ignoreCase = true) && candidate.length > query.length) {
            // Preserve user-typed prefix casing and append the remainder
            return query + candidate.substring(query.length)
        }
        return null
    }
}
