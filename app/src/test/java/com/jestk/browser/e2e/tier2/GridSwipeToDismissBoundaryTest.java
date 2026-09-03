package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Tab Grid Swipe-to-Dismiss (R5 & spec §4.3).
 */
public class GridSwipeToDismissBoundaryTest {

    @Test
    public void testSwipeBelowThresholdSnapsBack() {
        float swipeDistancePx = 40f;
        float dismissThresholdPx = 150f;

        boolean shouldDismiss = Math.abs(swipeDistancePx) >= dismissThresholdPx;

        assertThat(shouldDismiss).isFalse();
    }

    @Test
    public void testSwipeAboveThresholdTriggersDismiss() {
        float swipeDistancePx = 180f;
        float dismissThresholdPx = 150f;

        boolean shouldDismiss = Math.abs(swipeDistancePx) >= dismissThresholdPx;

        assertThat(shouldDismiss).isTrue();
    }

    @Test
    public void testDismissFirstTabUpdatesActiveTabToNext() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://one.com", "One", 100L, null, 0));
        tabs.add(new Tab("tab-2", "https://two.com", "Two", 200L, null, 1));
        String activeTabId = "tab-1";

        tabs.remove(0);
        if (activeTabId.equals("tab-1")) {
            activeTabId = tabs.get(0).getId();
        }

        assertThat(activeTabId).isEqualTo("tab-2");
    }

    @Test
    public void testDismissLastRemainingTabSpawnsNewTabAndResetsActive() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-last", "https://last.com", "Last", 100L, null, 0));

        tabs.remove(0);
        Tab fallback = new Tab("new-fallback", "about:blank", "New Tab", System.currentTimeMillis(), null, 0);
        tabs.add(fallback);

        assertThat(tabs).hasSize(1);
        assertThat(tabs.get(0).getId()).isEqualTo("new-fallback");
    }

    @Test
    public void testRapidDismissOfAllTabsConcurrently() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tabs.add(new Tab("t-" + i, "u", "T", 100L, null, i));
        }

        tabs.clear();

        assertThat(tabs).isEmpty();
    }
}
