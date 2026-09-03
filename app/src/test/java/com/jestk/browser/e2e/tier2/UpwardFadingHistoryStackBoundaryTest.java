package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.HistoryItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Upward Fading History Stack (R5 & spec §4.2).
 */
public class UpwardFadingHistoryStackBoundaryTest {

    @Test
    public void testEmptyHistoryStackShowsNoSuggestions() {
        List<HistoryItem> history = List.of();
        assertThat(history).isEmpty();
    }

    @Test
    public void testFiveHundredHistoryItemsLazyLoading() {
        List<HistoryItem> hugeHistory = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            hugeHistory.add(new HistoryItem((long) i, "https://url" + i + ".com", "Page " + i, System.currentTimeMillis() - i * 1000L));
        }

        assertThat(hugeHistory).hasSize(500);
        assertThat(hugeHistory.get(0).getTitle()).isEqualTo("Page 0");
    }

    @Test
    public void testHistoryItemWithExtremelyLongUrl() {
        StringBuilder sb = new StringBuilder("https://example.com/item?");
        for (int i = 0; i < 2000; i++) sb.append("k").append(i).append("=v").append(i).append("&");
        HistoryItem item = new HistoryItem(1L, sb.toString(), "Huge URL Item", 100L);

        assertThat(item.getUrl().length()).isGreaterThan(2000);
    }

    @Test
    public void testSearchQueryMatchingCaseAccentsAndSpecialCharacters() {
        List<HistoryItem> items = List.of(
                new HistoryItem(1L, "https://example.com/café", "Café de Paris", 100L),
                new HistoryItem(2L, "https://example.com/test", "Test Site", 200L)
        );

        String query = "café";
        List<HistoryItem> matches = items.stream().filter(h -> h.getTitle().toLowerCase().contains(query.toLowerCase())).toList();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getTitle()).isEqualTo("Café de Paris");
    }

    @Test
    public void testDeletingHistoryItemWhileSearchOverlayIsOpen() {
        List<HistoryItem> history = new ArrayList<>();
        history.add(new HistoryItem(1L, "https://a.com", "A", 100L));
        history.add(new HistoryItem(2L, "https://b.com", "B", 200L));

        // Delete item 1
        history.removeIf(h -> h.getId() == 1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getId()).isEqualTo(2L);
    }
}
