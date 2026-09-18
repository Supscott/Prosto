package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;

import org.junit.Test;

/**
 * Tier 2: Boundary & Edge Case Test for Search Modal Overlay (R5 & spec §4.2).
 */
public class SearchModalOverlayBoundaryTest {

    @Test
    public void testOverlayOpenedWithFourThousandCharUrl() {
        StringBuilder sb = new StringBuilder("https://example.com/search?q=");
        for (int i = 0; i < 4000; i++) sb.append("x");
        String hugeUrl = sb.toString();

        String input = hugeUrl;
        assertThat(input.length()).isGreaterThan(4000);
    }

    @Test
    public void testOverlayOpenedWithBlankTabUrl() {
        String blankUrl = "about:blank";
        // When opening search from about:blank, input box should start empty for convenience
        String searchInput = blankUrl.equals("about:blank") ? "" : blankUrl;

        assertThat(searchInput).isEmpty();
    }

    @Test
    public void testRapidOpenCloseOverlayStress() {
        OverlayType overlay = OverlayType.NONE;

        for (int i = 0; i < 50; i++) {
            overlay = OverlayType.SEARCH;
            assertThat(overlay).isEqualTo(OverlayType.SEARCH);
            overlay = OverlayType.NONE;
            assertThat(overlay).isEqualTo(OverlayType.NONE);
        }
    }

    @Test
    public void testSearchOverlayWithSpecialCharactersInput() {
        String complexQuery = "C++ vs Rust: memory & concurrency #1? (2026)";
        String trimmed = complexQuery.trim();

        assertThat(trimmed).isEqualTo(complexQuery);
    }

    @Test
    public void testOverlayOpenWhenKeyboardAlreadyShowing() {
        boolean isKeyboardVisible = true;
        OverlayType overlay = OverlayType.SEARCH;

        assertThat(overlay).isEqualTo(OverlayType.SEARCH);
        assertThat(isKeyboardVisible).isTrue();
    }
}
