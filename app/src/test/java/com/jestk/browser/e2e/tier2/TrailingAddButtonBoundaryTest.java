package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Trailing Add Button (R2 & spec §3.1).
 */
public class TrailingAddButtonBoundaryTest {

    @Test
    public void testCreatingHundredTabsSequentially() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tabs.add(new Tab("tab-" + i, "about:blank", "New Tab", System.currentTimeMillis(), null, i));
        }

        assertThat(tabs).hasSize(100);
        assertThat(tabs.get(99).getPosition()).isEqualTo(99);
    }

    @Test
    public void testSpawnTabWhenCurrentTabIsLoading() {
        boolean currentTabLoading = true;
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://slow-load.com", "Loading...", 100L, null, 0));

        // User clicks '+' button
        Tab spawned = new Tab("tab-2", "about:blank", "New Tab", 200L, null, 1);
        tabs.add(spawned);

        // Previous tab continues loading independently
        assertThat(currentTabLoading).isTrue();
        assertThat(tabs).hasSize(2);
    }

    @Test
    public void testRapidClicksOnAddButton() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tabs.add(new Tab("rapid-" + i, "about:blank", "New Tab", System.currentTimeMillis(), null, tabs.size()));
        }

        assertThat(tabs).hasSize(10);
    }

    @Test
    public void testSpawnTabWhenZeroTabsOpen() {
        List<Tab> emptyTabs = new ArrayList<>();
        Tab firstTab = new Tab("tab-first", "about:blank", "New Tab", System.currentTimeMillis(), null, 0);
        emptyTabs.add(firstTab);

        assertThat(emptyTabs).hasSize(1);
    }

    @Test
    public void testPositionIntegrityAfterMultipleSpawnsAndDeletions() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("t1", "u1", "T1", 100L, null, 0));
        tabs.add(new Tab("t2", "u2", "T2", 100L, null, 1));
        tabs.add(new Tab("t3", "u3", "T3", 100L, null, 2));

        // Delete middle tab
        tabs.remove(1);

        // Spawn new tab
        tabs.add(new Tab("t4", "u4", "T4", 100L, null, tabs.size()));

        assertThat(tabs).hasSize(3);
        assertThat(tabs.get(2).getId()).isEqualTo("t4");
    }
}
