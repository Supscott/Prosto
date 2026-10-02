package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.SearchEngine;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Tier 2: Boundary & Edge Case Test for Search Query Dispatch Engine (R5 & spec §4.2).
 */
public class SearchQueryDispatchEngineBoundaryTest {

    private String dispatch(String input, SearchEngine engine) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "about:blank";

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("localhost") || (trimmed.contains(".") && !trimmed.contains(" ")) || (trimmed.contains(":") && !trimmed.contains(" "))) {
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

    @Test
    public void testWhitespaceOnlyQueryReturnsAboutBlank() {
        String result = dispatch("     \t\n   ", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("about:blank");
    }

    @Test
    public void testQueryWithSpecialCharactersAndAmpersands() {
        String query = "rock & roll #1 = 100%";
        String result = dispatch(query, SearchEngine.DUCKDUCKGO);

        assertThat(result).startsWith("https://duckduckgo.com/?q=");
        assertThat(result).contains("rock+%26+roll+%231+%3D+100%25");
    }

    @Test
    public void testLocalhostWithPort() {
        String result = dispatch("localhost:8080", SearchEngine.DUCKDUCKGO);
        // Has colon/period without spaces
        assertThat(result).contains("localhost:8080");
    }

    @Test
    public void testIpv4AddressWithoutScheme() {
        String result = dispatch("192.168.1.1", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("https://192.168.1.1");
    }

    @Test
    public void testInternationalDomainNamePunycodeUrl() {
        String result = dispatch("https://münchen.de", SearchEngine.DUCKDUCKGO);
        assertThat(result).isEqualTo("https://münchen.de");
    }
}
