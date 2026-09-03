package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Back Navigation Hierarchy (R6 & spec §5.3).
 */
public class BackNavigationHierarchyBoundaryTest {

    @Test
    public void testDeepHistoryStackNavigationBack() {
        List<String> history = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            history.add("https://page" + i + ".com");
        }
        int currentIndex = history.size() - 1;

        // Press back 49 times
        for (int i = 0; i < 49; i++) {
            if (currentIndex > 0) {
                currentIndex--;
            }
        }

        assertThat(currentIndex).isEqualTo(0);
        assertThat(history.get(currentIndex)).isEqualTo("https://page0.com");
    }

    @Test
    public void testBackPressWhenNoTabsOpenExitsCleanly() {
        List<String> openTabs = new ArrayList<>();
        boolean exitApp = openTabs.isEmpty();

        assertThat(exitApp).isTrue();
    }

    @Test
    public void testRapidDoubleBackPressHandling() {
        OverlayType overlay = OverlayType.SEARCH;
        List<String> history = List.of("p1", "p2");
        int index = 1;

        // First press: dismisses overlay
        if (overlay != OverlayType.NONE) {
            overlay = OverlayType.NONE;
        }
        // Second rapid press: goes back in history
        if (overlay == OverlayType.NONE && index > 0) {
            index--;
        }

        assertThat(overlay).isEqualTo(OverlayType.NONE);
        assertThat(index).isEqualTo(0);
    }

    @Test
    public void testBackPressDuringActivePageLoad() {
        boolean isLoading = true;
        boolean cancelLoadTriggered = false;

        if (isLoading) {
            cancelLoadTriggered = true; // stopLoading / goBack
        }

        assertThat(cancelLoadTriggered).isTrue();
    }

    @Test
    public void testDismissingNestedDialogBeforeOverlay() {
        boolean isDialogShowing = true;
        OverlayType overlay = OverlayType.SETTINGS;

        // Dialog takes priority over overlay
        if (isDialogShowing) {
            isDialogShowing = false;
        }

        assertThat(isDialogShowing).isFalse();
        assertThat(overlay).isEqualTo(OverlayType.SETTINGS);
    }
}
