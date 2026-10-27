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

    /**
     * Cleans a URL string by stripping http/https schemes and www. prefix.
     */
    fun cleanUrl(url: String): String {
        return url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
    }

    /**
     * Extracts the clean host/domain from a URL without scheme or www.
     */
    fun extractDomain(url: String): String {
        val cleaned = cleanUrl(url)
        return cleaned.substringBefore("/").substringBefore("?").substringBefore("#")
    }

    /**
     * Finds the highest-ranked inline autocomplete candidate for [query].
     * Returns null if query is blank, contains spaces, or has no eligible candidate.
     */
    fun findInlineMatch(
        query: String,
        tabs: List<Tab>,
        favorites: List<FavoriteItem>,
        history: List<HistoryItem>,
    ): String? {
        if (query.isBlank() || query.contains(" ")) {
            return null
        }

        val hasSlash = query.contains("/")

        // 1. Check Open Tabs (sorted by lastActiveTimestamp descending)
        val sortedTabs = tabs.sortedByDescending { it.lastActiveTimestamp }
        for (tab in sortedTabs) {
            val candidate = getCandidate(tab.url, hasSlash)
            val match = matchPrefix(query, candidate)
            if (match != null) return match
        }

        // 2. Check Favorites
        for (favorite in favorites) {
            val candidate = getCandidate(favorite.url, hasSlash)
            val match = matchPrefix(query, candidate)
            if (match != null) return match
        }

        // 3. Check Browsing History (sorted by timestamp descending)
        val sortedHistory = history.sortedByDescending { it.timestamp }
        for (item in sortedHistory) {
            val candidate = getCandidate(item.url, hasSlash)
            val match = matchPrefix(query, candidate)
            if (match != null) return match
        }

        return null
    }

    /**
     * Returns all open tabs matching [query] for display in the "Switch to Tab" carousel.
     */
    fun findMatchingOpenTabs(
        query: String,
        tabs: List<Tab>,
    ): List<Tab> {
        if (query.isBlank() || query.contains(" ")) {
            return emptyList()
        }

        val hasSlash = query.contains("/")
        val sortedTabs = tabs.sortedByDescending { it.lastActiveTimestamp }

        return sortedTabs.filter { tab ->
            val candidate = getCandidate(tab.url, hasSlash)
            candidate.startsWith(query, ignoreCase = true)
        }
    }

    private fun getCandidate(url: String, hasSlash: Boolean): String {
        val cleaned = cleanUrl(url)
        return if (hasSlash) {
            cleaned.substringBefore("?").substringBefore("#")
        } else {
            cleaned.substringBefore("/").substringBefore("?").substringBefore("#")
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
