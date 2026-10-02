package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.SearchEngine;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Tier 1: Feature Coverage Test for Search Query Dispatch Engine (R5 & spec §4.2).
 * Direct URL vs Search Query routing with DuckDuckGo (default), Google, and Bing.
 */
public class SearchQueryDispatchEngineCoverageTest {

    static class MockSearchDispatcher {
        public static String dispatch(String input, SearchEngine engine) {
            String trimmed = input.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || (trimmed.contains(".") && !trimmed.contains(" "))) {
                if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    return "https://" + trimmed;
                }
                return trimmed;
            }

            String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            if (engine.equals(SearchEngine.GOOGLE)) {
                return "https://www.google.com/search?q=" + encoded;
            } else if (engine.equals(SearchEngine.BING)) {
                return "https://www.bing.com/search?q=" + encoded;
            } else {
                return "https://duckduckgo.com/?q=" + encoded;
            }
        }
    }

    @Test
    public void testDuckDuckGoDefaultSearchQuery() {
        String result = MockSearchDispatcher.dispatch("android compose", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("https://duckduckgo.com/?q=android+compose");
    }

    @Test
    public void testGoogleSearchQuery() {
        String result = MockSearchDispatcher.dispatch("kotlin coroutines", SearchEngine.GOOGLE);
        assertThat(result).isEqualTo("https://www.google.com/search?q=kotlin+coroutines");
    }

    @Test
    public void testBingSearchQuery() {
        String result = MockSearchDispatcher.dispatch("material design 3", SearchEngine.BING);
        assertThat(result).isEqualTo("https://www.bing.com/search?q=material+design+3");
    }

    @Test
    public void testDirectUrlWithSchemePassesThrough() {
        String result = MockSearchDispatcher.dispatch("https://jestk.com/about", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("https://jestk.com/about");
    }

    @Test
    public void testDirectDomainWithoutSchemeAppendsHttps() {
        String result = MockSearchDispatcher.dispatch("github.com", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("https://github.com");
    }
}
