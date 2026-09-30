package com.jestk.browser.domain.model

import java.net.URLEncoder

enum class SearchEngine(
    val displayName: String,
    val searchUrlTemplate: String,
    val iconAssetUrl: String
) {
    DUCKDUCKGO(
        "DuckDuckGo",
        "https://duckduckgo.com/?q=%s",
        "file:///android_asset/icons/duckduckgo.svg"
    ),
    GOOGLE(
        "Google",
        "https://www.google.com/search?q=%s",
        "file:///android_asset/icons/google.svg"
    ),
    BING(
        "Bing",
        "https://www.bing.com/search?q=%s",
        "file:///android_asset/icons/bing.svg"
    );

    fun buildSearchUrl(query: String): String {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        return String.format(searchUrlTemplate, encoded)
    }

    fun matchesUrl(url: String): Boolean {
        val lower = url.lowercase()
        return when (this) {
            DUCKDUCKGO -> lower.contains("duckduckgo.com")
            GOOGLE -> lower.contains("google.com") || lower.contains(".google.") || lower.contains("://google.")
            BING -> lower.contains("bing.com")
        }
    }

    fun extractQuery(url: String): String? {
        if (!matchesUrl(url)) return null
        val lower = url.lowercase()
        val isSearchPage = when (this) {
            DUCKDUCKGO -> lower.contains("duckduckgo.com") && (lower.contains("?q=") || lower.contains("&q="))
            GOOGLE -> (lower.contains("google.") || lower.contains("google.com")) &&
                lower.contains("/search") &&
                (lower.contains("?q=") || lower.contains("&q="))
            BING -> lower.contains("bing.com") && lower.contains("/search") && (lower.contains("?q=") || lower.contains("&q="))
        }
        if (!isSearchPage) return null

        val regex = Regex("""[?&]q=([^&#]*)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url) ?: return null
        val rawQuery = match.groupValues[1]
        if (rawQuery.isBlank()) return null
        return try {
            java.net.URLDecoder.decode(rawQuery.replace("+", " "), "UTF-8").trim()
        } catch (_: Exception) {
            rawQuery.replace("+", " ").trim()
        }.takeIf { it.isNotBlank() }
    }

    companion object {
        fun findMatchingEngineAndQuery(url: String?): Pair<SearchEngine, String>? {
            if (url.isNullOrBlank()) return null
            for (engine in entries) {
                val query = engine.extractQuery(url)
                if (query != null) {
                    return Pair(engine, query)
                }
            }
            return null
        }
    }
}
