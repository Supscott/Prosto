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

        val braveUrl = SearchEngine.BRAVE.buildSearchUrl("hori browser")
        assertThat(braveUrl).isEqualTo("https://search.brave.com/search?q=hori+browser")

        val kagiUrl = SearchEngine.KAGI.buildSearchUrl("kotlin coroutines")
        assertThat(kagiUrl).isEqualTo("https://kagi.com/search?q=kotlin+coroutines")

        val ecosiaUrl = SearchEngine.ECOSIA.buildSearchUrl("tree planting")
        assertThat(ecosiaUrl).isEqualTo("https://www.ecosia.org/search?q=tree+planting")

        val startpageUrl = SearchEngine.STARTPAGE.buildSearchUrl("privacy search")
        assertThat(startpageUrl).isEqualTo("https://www.startpage.com/sp/search?query=privacy+search")
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

        assertThat(SearchEngine.BRAVE.matchesUrl("https://search.brave.com/search?q=test")).isTrue()
        assertThat(SearchEngine.BRAVE.matchesUrl("https://brave.com/search")).isTrue()
        assertThat(SearchEngine.BRAVE.matchesUrl("https://yahoo.com")).isFalse()

        assertThat(SearchEngine.KAGI.matchesUrl("https://kagi.com/search?q=test")).isTrue()
        assertThat(SearchEngine.KAGI.matchesUrl("https://google.com")).isFalse()

        assertThat(SearchEngine.ECOSIA.matchesUrl("https://www.ecosia.org/search?q=test")).isTrue()
        assertThat(SearchEngine.ECOSIA.matchesUrl("https://ecosia.org")).isTrue()
        assertThat(SearchEngine.ECOSIA.matchesUrl("https://bing.com")).isFalse()

        assertThat(SearchEngine.STARTPAGE.matchesUrl("https://www.startpage.com/sp/search?query=test")).isTrue()
        assertThat(SearchEngine.STARTPAGE.matchesUrl("https://startpage.com")).isTrue()
        assertThat(SearchEngine.STARTPAGE.matchesUrl("https://google.com")).isFalse()
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
    fun extractQuery_brave() {
        assertThat(SearchEngine.BRAVE.extractQuery("https://search.brave.com/search?q=apple")).isEqualTo("apple")
        assertThat(SearchEngine.BRAVE.extractQuery("https://search.brave.com/search?source=web&q=jetpack+compose")).isEqualTo("jetpack compose")
        assertThat(SearchEngine.BRAVE.extractQuery("https://search.brave.com/")).isNull()
    }

    @Test
    fun extractQuery_kagi() {
        assertThat(SearchEngine.KAGI.extractQuery("https://kagi.com/search?q=apple")).isEqualTo("apple")
        assertThat(SearchEngine.KAGI.extractQuery("https://kagi.com/search?q=kotlin+coroutines")).isEqualTo("kotlin coroutines")
        assertThat(SearchEngine.KAGI.extractQuery("https://kagi.com/")).isNull()
    }

    @Test
    fun extractQuery_ecosia() {
        assertThat(SearchEngine.ECOSIA.extractQuery("https://www.ecosia.org/search?q=trees")).isEqualTo("trees")
        assertThat(SearchEngine.ECOSIA.extractQuery("https://www.ecosia.org/search?addon=chrome&q=green+energy")).isEqualTo("green energy")
        assertThat(SearchEngine.ECOSIA.extractQuery("https://www.ecosia.org/")).isNull()
    }

    @Test
    fun extractQuery_startpage() {
        assertThat(SearchEngine.STARTPAGE.extractQuery("https://www.startpage.com/sp/search?query=privacy")).isEqualTo("privacy")
        assertThat(SearchEngine.STARTPAGE.extractQuery("https://www.startpage.com/do/dsearch?query=secure+browsing")).isEqualTo("secure browsing")
        assertThat(SearchEngine.STARTPAGE.extractQuery("https://www.startpage.com/sp/search?q=alt+param")).isEqualTo("alt param")
        assertThat(SearchEngine.STARTPAGE.extractQuery("https://www.startpage.com/")).isNull()
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

        val braveMatch = SearchEngine.findMatchingEngineAndQuery("https://search.brave.com/search?q=apple")
        assertThat(braveMatch).isNotNull()
        assertThat(braveMatch?.first).isEqualTo(SearchEngine.BRAVE)
        assertThat(braveMatch?.second).isEqualTo("apple")

        val kagiMatch = SearchEngine.findMatchingEngineAndQuery("https://kagi.com/search?q=apple")
        assertThat(kagiMatch).isNotNull()
        assertThat(kagiMatch?.first).isEqualTo(SearchEngine.KAGI)
        assertThat(kagiMatch?.second).isEqualTo("apple")

        val ecosiaMatch = SearchEngine.findMatchingEngineAndQuery("https://www.ecosia.org/search?q=apple")
        assertThat(ecosiaMatch).isNotNull()
        assertThat(ecosiaMatch?.first).isEqualTo(SearchEngine.ECOSIA)
        assertThat(ecosiaMatch?.second).isEqualTo("apple")

        val startpageMatch = SearchEngine.findMatchingEngineAndQuery("https://www.startpage.com/sp/search?query=apple")
        assertThat(startpageMatch).isNotNull()
        assertThat(startpageMatch?.first).isEqualTo(SearchEngine.STARTPAGE)
        assertThat(startpageMatch?.second).isEqualTo("apple")

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
            orderPosition = 6,
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
    fun defaultEngines_haveExpectedShortcutsAndOrder() {
        assertThat(SearchEngine.DEFAULT_ENGINES).hasSize(6)
        assertThat(SearchEngine.DEFAULT_ENGINES.map { it.id }).containsExactly(
            "google", "duckduckgo", "brave", "kagi", "ecosia", "startpage"
        ).inOrder()

        assertThat(SearchEngine.GOOGLE.shortcut).isEqualTo("g")
        assertThat(SearchEngine.DUCKDUCKGO.shortcut).isEqualTo("d")
        assertThat(SearchEngine.BRAVE.shortcut).isEqualTo("b")
        assertThat(SearchEngine.KAGI.shortcut).isEqualTo("k")
        assertThat(SearchEngine.ECOSIA.shortcut).isEqualTo("e")
        assertThat(SearchEngine.STARTPAGE.shortcut).isEqualTo("s")
    }

    @Test
    fun valueOf_bingFallsBackGracefully() {
        val fallback = SearchEngine.valueOf("BING")
        assertThat(fallback).isEqualTo(SearchEngine.DUCKDUCKGO)
    }
}
