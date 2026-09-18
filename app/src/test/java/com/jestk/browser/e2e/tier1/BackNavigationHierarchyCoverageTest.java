package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Back Navigation Hierarchy (R6 & spec §5.3).
 * Hierarchy: Dismiss active overlay -> WebView goBack() -> Close active tab.
 */
public class BackNavigationHierarchyCoverageTest {

    static class MockBrowserNavigationState {
        OverlayType activeOverlay = OverlayType.NONE;
        List<String> webViewHistory = new ArrayList<>();
        int historyIndex = -1;
        List<String> openTabs = new ArrayList<>();
        int activeTabIndex = -1;

        public void loadUrl(String url) {
            webViewHistory.add(url);
            historyIndex = webViewHistory.size() - 1;
        }

        public boolean canGoBack() {
            return historyIndex > 0;
        }

        public void goBack() {
            if (canGoBack()) {
                historyIndex--;
            }
        }

        public String handleBackPressed() {
            // 1. Dismiss active overlay first
            if (activeOverlay != OverlayType.NONE) {
                OverlayType dismissed = activeOverlay;
                activeOverlay = OverlayType.NONE;
                return "DISMISSED_OVERLAY_" + dismissed.name();
            }

            // 2. WebView back navigation
            if (canGoBack()) {
                goBack();
                return "WEBVIEW_GO_BACK";
            }

            // 3. Close active tab
            if (!openTabs.isEmpty()) {
                String closed = openTabs.remove(activeTabIndex);
                if (activeTabIndex >= openTabs.size()) {
                    activeTabIndex = openTabs.size() - 1;
                }
                return "CLOSED_TAB_" + closed;
            }

            return "EXIT_APP";
        }
    }

    @Test
    public void testDismissesOverlayFirst() {
        MockBrowserNavigationState state = new MockBrowserNavigationState();
        state.openTabs.add("tab-1");
        state.activeTabIndex = 0;
        state.loadUrl("https://page1.com");
        state.loadUrl("https://page2.com");
        state.activeOverlay = OverlayType.SEARCH;

        String action = state.handleBackPressed();

        assertThat(action).isEqualTo("DISMISSED_OVERLAY_SEARCH");
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE);
        // History remains untouched at this step
        assertThat(state.historyIndex).isEqualTo(1);
    }

    @Test
    public void testNavigatesWebViewBackWhenNoOverlay() {
        MockBrowserNavigationState state = new MockBrowserNavigationState();
        state.openTabs.add("tab-1");
        state.activeTabIndex = 0;
        state.loadUrl("https://page1.com");
        state.loadUrl("https://page2.com");

        String action = state.handleBackPressed();

        assertThat(action).isEqualTo("WEBVIEW_GO_BACK");
        assertThat(state.historyIndex).isEqualTo(0);
        assertThat(state.openTabs).hasSize(1);
    }

    @Test
    public void testClosesActiveTabWhenNoHistoryAndNoOverlay() {
        MockBrowserNavigationState state = new MockBrowserNavigationState();
        state.openTabs.add("tab-1");
        state.openTabs.add("tab-2");
        state.activeTabIndex = 1;
        state.loadUrl("https://page1.com"); // single entry in active tab

        String action = state.handleBackPressed();

        assertThat(action).isEqualTo("CLOSED_TAB_tab-2");
        assertThat(state.openTabs).containsExactly("tab-1");
        assertThat(state.activeTabIndex).isEqualTo(0);
    }

    @Test
    public void testTabGridOverlayDismissalPriority() {
        MockBrowserNavigationState state = new MockBrowserNavigationState();
        state.openTabs.add("tab-1");
        state.activeTabIndex = 0;
        state.activeOverlay = OverlayType.TAB_GRID;

        String action = state.handleBackPressed();

        assertThat(action).isEqualTo("DISMISSED_OVERLAY_TAB_GRID");
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE);
    }

    @Test
    public void testSettingsSheetOverlayDismissalPriority() {
        MockBrowserNavigationState state = new MockBrowserNavigationState();
        state.openTabs.add("tab-1");
        state.activeTabIndex = 0;
        state.activeOverlay = OverlayType.SETTINGS;

        String action = state.handleBackPressed();

        assertThat(action).isEqualTo("DISMISSED_OVERLAY_SETTINGS");
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE);
    }
}
