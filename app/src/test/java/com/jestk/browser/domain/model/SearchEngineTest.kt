package com.jestk.browser.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchEngineTest {

    @Test
    fun buildSearchUrl_formatsQueryCorrectly() {
        val googleUrl = SearchEngine.GOOGLE.buildSearchUrl("apple")
        assertThat(googleUrl).isEqualTo("https://www.google.com/search?q=apple")

        val ddgUrl = SearchEngine.DUCKDUCKGO.buildSearchUrl("apple pie")
        assertThat(ddgUrl).isEqualTo("https://duckduckgo.com/?q=apple+pie")

        val bingUrl = SearchEngine.BING.buildSearchUrl("hori browser")
        assertThat(bingUrl).isEqualTo("https://www.bing.com/search?q=hori+browser")
    }

    @Test
    fun matchesUrl_identifiesEngines() {
        assertThat(SearchEngine.GOOGLE.matchesUrl("https://www.google.com/search?q=test")).isTrue()
        assertThat(SearchEngine.GOOGLE.matchesUrl("https://www.google.ru/search?q=test")).isTrue()
        assertThat(SearchEngine.GOOGLE.matchesUrl("https://google.co.uk/search?q=test")).isTrue()
        assertThat(SearchEngine.GOOGLE.matchesUrl("https://duckduckgo.com/?q=test")).isFalse()

        assertThat(SearchEngine.DUCKDUCKGO.matchesUrl("https://duckduckgo.com/?q=test")).isTrue()
        assertThat(SearchEngine.DUCKDUCKGO.matchesUrl("https://html.duckduckgo.com/html/?q=test")).isTrue()
        assertThat(SearchEngine.DUCKDUCKGO.matchesUrl("https://google.com")).isFalse()

        assertThat(SearchEngine.BING.matchesUrl("https://www.bing.com/search?q=test")).isTrue()
        assertThat(SearchEngine.BING.matchesUrl("https://bing.com")).isTrue()
        assertThat(SearchEngine.BING.matchesUrl("https://yahoo.com")).isFalse()
    }

    @Test
    fun extractQuery_google() {
        // Standard query
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/search?q=apple")).isEqualTo("apple")
        // Multiple params
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/search?hl=en&q=apple%20pie&source=hp")).isEqualTo("apple pie")
        // Plus-separated words
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/search?q=apple+pie")).isEqualTo("apple pie")
        // Regional domain
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.ru/search?q=android")).isEqualTo("android")
        // Non-search page
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/")).isNull()
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/about")).isNull()
        // Empty query
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/search?q=")).isNull()
        assertThat(SearchEngine.GOOGLE.extractQuery("https://www.google.com/search?q=+")).isNull()
    }

    @Test
    fun extractQuery_duckduckgo() {
        assertThat(SearchEngine.DUCKDUCKGO.extractQuery("https://duckduckgo.com/?q=apple")).isEqualTo("apple")
        assertThat(SearchEngine.DUCKDUCKGO.extractQuery("https://duckduckgo.com/?t=h_&q=kotlin+coroutines")).isEqualTo("kotlin coroutines")
        assertThat(SearchEngine.DUCKDUCKGO.extractQuery("https://duckduckgo.com/about")).isNull()
    }

    @Test
    fun extractQuery_bing() {
        assertThat(SearchEngine.BING.extractQuery("https://www.bing.com/search?q=apple")).isEqualTo("apple")
        assertThat(SearchEngine.BING.extractQuery("https://www.bing.com/search?form=QBLH&q=jetpack+compose")).isEqualTo("jetpack compose")
        assertThat(SearchEngine.BING.extractQuery("https://www.bing.com/")).isNull()
    }

    @Test
    fun findMatchingEngineAndQuery_detectsCorrectEngineAndQuery() {
        val googleMatch = SearchEngine.findMatchingEngineAndQuery("https://www.google.com/search?q=apple")
        assertThat(googleMatch).isNotNull()
        assertThat(googleMatch?.first).isEqualTo(SearchEngine.GOOGLE)
        assertThat(googleMatch?.second).isEqualTo("apple")

        val ddgMatch = SearchEngine.findMatchingEngineAndQuery("https://duckduckgo.com/?q=apple")
        assertThat(ddgMatch).isNotNull()
        assertThat(ddgMatch?.first).isEqualTo(SearchEngine.DUCKDUCKGO)
        assertThat(ddgMatch?.second).isEqualTo("apple")

        val bingMatch = SearchEngine.findMatchingEngineAndQuery("https://www.bing.com/search?q=apple")
        assertThat(bingMatch).isNotNull()
        assertThat(bingMatch?.first).isEqualTo(SearchEngine.BING)
        assertThat(bingMatch?.second).isEqualTo("apple")

        assertThat(SearchEngine.findMatchingEngineAndQuery("https://example.com")).isNull()
        assertThat(SearchEngine.findMatchingEngineAndQuery("https://www.google.com/")).isNull()
        assertThat(SearchEngine.findMatchingEngineAndQuery("")).isNull()
        assertThat(SearchEngine.findMatchingEngineAndQuery(null)).isNull()
    }

    @Test
    fun customSearchEngine_buildSearchUrlAndMatchesUrl() {
        val youtube = SearchEngine(
            id = "custom_youtube",
            displayName = "YouTube",
            searchUrlTemplate = "https://www.youtube.com/results?search_query=%s",
            iconUrl = "https://www.youtube.com/favicon.ico",
            shortcut = "y",
            orderPosition = 3,
            isBuiltIn = false
        )

        assertThat(youtube.buildSearchUrl("lofi hip hop")).isEqualTo("https://www.youtube.com/results?search_query=lofi+hip+hop")
        assertThat(youtube.matchesUrl("https://www.youtube.com/results?search_query=lofi")).isTrue()
        assertThat(youtube.extractQuery("https://www.youtube.com/results?search_query=lofi+hip+hop")).isEqualTo("lofi hip hop")

        val engines = SearchEngine.DEFAULT_ENGINES + youtube
        val match = SearchEngine.findMatchingEngineAndQuery("https://www.youtube.com/results?search_query=lofi+music", engines)
        assertThat(match).isNotNull()
        assertThat(match?.first?.displayName).isEqualTo("YouTube")
        assertThat(match?.second).isEqualTo("lofi music")
    }

    @Test
    fun defaultEngines_haveExpectedShortcuts() {
        assertThat(SearchEngine.GOOGLE.shortcut).isEqualTo("g")
        assertThat(SearchEngine.DUCKDUCKGO.shortcut).isEqualTo("d")
        assertThat(SearchEngine.BING.shortcut).isEqualTo("b")
    }
}
