package com.jestk.browser.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class SearchEngineHostsTest {

    @Test
    fun isSearchEngineHost_identifiesKnownEngines() {
        listOf(
            "google.com", "www.google.com", "google.co.uk", "google.com.br", "yandex.ru", "yandex.kz",
            "ya.ru", "duckduckgo.com", "html.duckduckgo.com", "bing.com", "search.yahoo.com",
            "kagi.com", "search.brave.com", "www.ecosia.org", "startpage.com",
        ).forEach { assertWithMessage(it).that(SearchEngineHosts.isSearchEngineHost(it)).isTrue() }
    }

    @Test
    fun isSearchEngineHost_rejectsOtherHosts() {
        listOf(
            "youtube.com", "reddit.com", "", "notgoogle.com", "google.example.com", "mygoogle.com",
        ).forEach { assertWithMessage(it).that(SearchEngineHosts.isSearchEngineHost(it)).isFalse() }
    }

    @Test
    fun matchesUrl_usesHostNotSubstring() {
        assertThat(SearchEngine.GOOGLE.matchesUrl("https://example.com/?ref=google.com")).isFalse()
        assertThat(SearchEngine.DUCKDUCKGO.matchesUrl("https://example.com/duckduckgo.com")).isFalse()
        assertThat(SearchEngine.GOOGLE.extractQuery("https://example.com/search?q=x&from=google.com")).isNull()
    }
}
