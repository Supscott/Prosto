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
        return when (this) {
            DUCKDUCKGO -> url.contains("duckduckgo.com")
            GOOGLE -> url.contains("google.com")
            BING -> url.contains("bing.com")
        }
    }

    fun extractQuery(url: String): String? {
        val pattern = when (this) {
            DUCKDUCKGO -> "duckduckgo.com/?q="
            GOOGLE -> "google.com/search?q="
            BING -> "bing.com/search?q="
        }
        if (!url.contains(pattern)) return null
        val queryStart = url.indexOf(pattern) + pattern.length
        val queryEnd = url.indexOf('&', queryStart).let { if (it == -1) url.length else it }
        val rawQuery = url.substring(queryStart, queryEnd)
        return try {
            java.net.URLDecoder.decode(rawQuery, "UTF-8")
        } catch (_: Exception) {
            rawQuery
        }
    }
}
