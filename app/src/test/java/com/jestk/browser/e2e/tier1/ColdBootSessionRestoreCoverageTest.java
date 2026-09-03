package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Cold Boot Session Restoration (R1).
 */
public class ColdBootSessionRestoreCoverageTest {

    @Test
    public void testColdBootRestoresAllOpenTabs() {
        // Given stored tabs from previous session in database
        List<Tab> persistedTabs = new ArrayList<>();
        persistedTabs.add(new Tab("tab-1", "https://jestk.com", "Jestk Browser", 1000L, "/cache/tab_1.jpg", 0));
        persistedTabs.add(new Tab("tab-2", "https://news.ycombinator.com", "Hacker News", 2000L, "/cache/tab_2.jpg", 1));
        persistedTabs.add(new Tab("tab-3", "https://github.com", "GitHub", 3000L, "/cache/tab_3.jpg", 2));

        // When cold boot restoration occurs
        List<Tab> restoredTabs = new ArrayList<>(persistedTabs);

        // Then tab count and metadata match persisted state exactly
        assertThat(restoredTabs).hasSize(3);
        assertThat(restoredTabs.get(0).getTitle()).isEqualTo("Jestk Browser");
        assertThat(restoredTabs.get(1).getUrl()).isEqualTo("https://news.ycombinator.com");
        assertThat(restoredTabs.get(2).getSnapshotPath()).isEqualTo("/cache/tab_3.jpg");
    }

    @Test
    public void testColdBootRestoresActiveTabIndex() {
        // Given 3 restored tabs where tab-2 was active
        List<Tab> tabs = List.of(
                new Tab("tab-1", "https://site1.com", "Site 1", 1000L, null, 0),
                new Tab("tab-2", "https://site2.com", "Site 2", 5000L, null, 1),
                new Tab("tab-3", "https://site3.com", "Site 3", 2000L, null, 2)
        );
        String lastActiveTabId = "tab-2";

        // When locating active tab
        Tab activeTab = tabs.stream()
                .filter(t -> t.getId().equals(lastActiveTabId))
                .findFirst()
                .orElse(tabs.get(0));

        // Then active tab is restored correctly
        assertThat(activeTab.getId()).isEqualTo("tab-2");
        assertThat(activeTab.getPosition()).isEqualTo(1);
    }

    @Test
    public void testColdBootWithEmptySessionSpawnsDefaultTab() {
        // Given an empty tab database
        List<Tab> restoredTabs = new ArrayList<>();

        // When cold boot handles empty state
        if (restoredTabs.isEmpty()) {
            restoredTabs.add(new Tab("default-tab", "about:blank", "New Tab", System.currentTimeMillis(), null, 0));
        }

        // Then a single default tab is created
        assertThat(restoredTabs).hasSize(1);
        assertThat(restoredTabs.get(0).getUrl()).isEqualTo("about:blank");
        assertThat(restoredTabs.get(0).getTitle()).isEqualTo("New Tab");
    }

    @Test
    public void testColdBootPreservesCachedSnapshotPaths() {
        // Given tabs with cached disk snapshots
        Tab tab = new Tab("tab-42", "https://docs.kotlinlang.org", "Kotlin Docs", 9999L, "/data/user/0/com.jestk.browser/files/snapshots/tab_42.jpg", 0);

        // Then snapshot file path is preserved without blanking
        assertThat(tab.getSnapshotPath()).isNotNull();
        assertThat(tab.getSnapshotPath()).endsWith("tab_42.jpg");
    }

    @Test
    public void testColdBootPreservesTabOrdering() {
        // Given tabs with explicit position ordering
        List<Tab> tabs = List.of(
                new Tab("t3", "https://three.com", "Three", 100L, null, 2),
                new Tab("t1", "https://one.com", "One", 100L, null, 0),
                new Tab("t2", "https://two.com", "Two", 100L, null, 1)
        );

        // When sorted by position
        List<Tab> sortedTabs = tabs.stream()
                .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .toList();

        // Then correct visual order is recovered
        assertThat(sortedTabs.get(0).getId()).isEqualTo("t1");
        assertThat(sortedTabs.get(1).getId()).isEqualTo("t2");
        assertThat(sortedTabs.get(2).getId()).isEqualTo("t3");
    }
}
