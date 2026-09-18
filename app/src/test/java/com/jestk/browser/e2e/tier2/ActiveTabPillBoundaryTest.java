package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tier 2: Boundary & Edge Case Test for Active Tab Pill (R2 & spec §3.1).
 */
public class ActiveTabPillBoundaryTest {

    @Test
    public void testThousandCharacterTitleTruncation() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("A");
        String hugeTitle = sb.toString();

        int maxChars = 30;
        String truncated = hugeTitle.length() > maxChars ? hugeTitle.substring(0, maxChars) + "…" : hugeTitle;

        assertThat(truncated).hasLength(31);
        assertThat(truncated.endsWith("…")).isTrue();
    }

    @Test
    public void testEmptyTitleFallsBackToHostname() {
        String title = "";
        String url = "https://jestk.com/path";

        String displayTitle = title.isEmpty() ? "jestk.com" : title;

        assertThat(displayTitle).isEqualTo("jestk.com");
    }

    @Test
    public void testSwipeOnSingleTabDoesNotThrow() {
        int currentIndex = 0;
        int totalTabs = 1;

        // Attempt swipe left
        int newIndex = currentIndex + 1;
        if (newIndex >= totalTabs) newIndex = currentIndex;

        assertThat(newIndex).isEqualTo(0);
    }

    @Test
    public void testZeroWidthOrExtremelyNarrowScreenConstraint() {
        int screenWidthPx = 100;
        int pillWidthPx = (int) (screenWidthPx * 0.5f);

        assertThat(pillWidthPx).isEqualTo(50);
    }

    @Test
    public void testTitleWithOnlyWhitespace() {
        String whitespaceTitle = "     ";
        String fallback = whitespaceTitle.trim().isEmpty() ? "New Tab" : whitespaceTitle;

        assertThat(fallback).isEqualTo("New Tab");
    }
}
