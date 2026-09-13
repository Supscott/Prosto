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
}
