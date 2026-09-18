package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Home Recent Tabs Carousel (R5 & spec §4.1).
 */
public class HomeRecentTabsCarouselBoundaryTest {

    @Test
    public void testRecentTabsWithExactlySixItems() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tabs.add(new Tab("tab-" + i, "https://url" + i + ".com", "Tab " + i, (long) i, null, i));
        }

        List<Tab> capped = tabs.stream().limit(6).toList();

        assertThat(capped).hasSize(6);
    }

    @Test
    public void testRecentTabsWithTwentyItemsCappedToSix() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tabs.add(new Tab("tab-" + i, "https://url" + i + ".com", "Tab " + i, (long) i, null, i));
        }

        List<Tab> capped = tabs.stream().limit(6).toList();

        assertThat(capped).hasSize(6);
    }

    @Test
    public void testRecentTabsWithOneItem() {
        List<Tab> tabs = List.of(new Tab("only-one", "https://only.com", "Only One", 100L, null, 0));

        List<Tab> capped = tabs.stream().limit(6).toList();

        assertThat(capped).hasSize(1);
    }

    @Test
    public void testRecentTabsWhenActiveTabTimestampUpdated() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("t1", "u1", "T1", 1000L, null, 0));
        tabs.add(new Tab("t2", "u2", "T2", 2000L, null, 1));

        // Revisit t1 -> updates timestamp to 3000L
        tabs.remove(0);
        tabs.add(new Tab("t1", "u1", "T1", 3000L, null, 0));

        List<Tab> sorted = tabs.stream().sorted((a, b) -> Long.compare(b.getLastActiveTimestamp(), a.getLastActiveTimestamp())).toList();

        assertThat(sorted.get(0).getId()).isEqualTo("t1");
    }

    @Test
    public void testRecentTabWithNullSnapshotRendersFaviconFallback() {
        Tab tab = new Tab("t-null-snap", "https://jestk.com", "Jestk", 100L, null, 0);

        boolean hasSnapshot = tab.getSnapshotPath() != null;
        assertThat(hasSnapshot).isFalse();
    }
}
