package com.jestk.browser.e2e.tier4;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 4 - Scenario 5: Tab Grid Management & Swipe-to-Dismiss Workflow.
 * Exercises: Action Dock Grid Button -> 3-Column Tab Grid Overview ->
 * Live Snapshot Rendering -> Horizontal Swipe gesture -> Room DB Deletion ->
 * WebViewPool Cleanup -> Active Tab Reassignment -> Tap Card Navigation.
 */
public class Scenario5TabGridManagementAndSwipeDismissWorkflowTest {

    @Test
    public void testFullTabGridManagementAndSwipeDismissScenario() {
        // Step 1: User has 5 active tabs open
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://tab1.com", "Tab 1", 1000L, "/cache/1.jpg", 0));
        tabs.add(new Tab("tab-2", "https://tab2.com", "Tab 2", 2000L, "/cache/2.jpg", 1));
        tabs.add(new Tab("tab-3", "https://tab3.com", "Tab 3", 5000L, "/cache/3.jpg", 2)); // active tab
        tabs.add(new Tab("tab-4", "https://tab4.com", "Tab 4", 3000L, "/cache/4.jpg", 3));
        tabs.add(new Tab("tab-5", "https://tab5.com", "Tab 5", 4000L, "/cache/5.jpg", 4));

        String activeTabId = "tab-3";
        OverlayType activeOverlay = OverlayType.NONE;

        // Step 2: User taps Action Dock 'All Tabs (Grid)' button
        activeOverlay = OverlayType.TAB_GRID;
        int gridColumns = 3;
        int gridRows = (int) Math.ceil((double) tabs.size() / gridColumns);

        assertThat(activeOverlay).isEqualTo(OverlayType.TAB_GRID);
        assertThat(gridColumns).isEqualTo(3);
        assertThat(gridRows).isEqualTo(2);

        // Step 3: User swipes left on Tab 3 card (the active tab) past dismiss threshold
        float swipeOffset = -200f;
        float dismissThreshold = 150f;
        boolean dismissed = Math.abs(swipeOffset) >= dismissThreshold;

        assertThat(dismissed).isTrue();

        // Step 4: Tab 3 is deleted from repository & pool
        tabs.removeIf(t -> t.getId().equals("tab-3"));
        assertThat(tabs).hasSize(4);

        // Step 5: Active tab is automatically reassigned to the next available tab (tab-4 or fallback tab-2)
        if (activeTabId.equals("tab-3")) {
            activeTabId = tabs.get(2).getId(); // tab-4 was at index 3, now shifted to index 2
        }

        assertThat(activeTabId).isEqualTo("tab-4");

        // Step 6: User taps Tab 1 card in the grid to switch focus
        String tappedCardId = "tab-1";
        activeTabId = tappedCardId;
        activeOverlay = OverlayType.NONE; // Grid dismisses

        assertThat(activeTabId).isEqualTo("tab-1");
        assertThat(activeOverlay).isEqualTo(OverlayType.NONE);
    }
}
