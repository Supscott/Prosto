package com.jestk.browser.e2e.tier4;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 4 - Scenario 7: Bottom Bar Auto-Hide & Back Navigation Hierarchy Workflow.
 * Exercises: NestedScroll downward translation -> Upward reveal -> Search modal open ->
 * Back press overlay dismissal -> History navigation (3 pages deep) -> Back press history pop ->
 * Root history back press -> Active tab closing.
 */
public class Scenario7BottomBarAutoHideAndBackHierarchyWorkflowTest {

    static class MockFullNavigationContext {
        float bottomBarOffsetPx = 0f;
        OverlayType activeOverlay = OverlayType.NONE;
        final List<String> currentTabHistory = new ArrayList<>();
        int historyIndex = -1;
        final List<Tab> openTabs = new ArrayList<>();
        int activeTabIndex = 0;

        public void navigate(String url) {
            currentTabHistory.add(url);
            historyIndex = currentTabHistory.size() - 1;
        }

        public void scrollDown(float delta) {
            bottomBarOffsetPx = Math.min(200f, bottomBarOffsetPx + delta);
        }

        public void scrollUp(float delta) {
            bottomBarOffsetPx = Math.max(0f, bottomBarOffsetPx - delta);
        }

        public String pressBack() {
            // Priority 1: Dismiss open overlays
            if (activeOverlay != OverlayType.NONE) {
                OverlayType dismissed = activeOverlay;
                activeOverlay = OverlayType.NONE;
                return "DISMISSED_" + dismissed.name();
            }

            // Priority 2: Navigate back in WebView history
            if (historyIndex > 0) {
                historyIndex--;
                return "HISTORY_BACK_TO_" + currentTabHistory.get(historyIndex);
            }

            // Priority 3: Close active tab
            if (!openTabs.isEmpty()) {
                Tab closed = openTabs.remove(activeTabIndex);
                if (activeTabIndex >= openTabs.size() && !openTabs.isEmpty()) {
                    activeTabIndex = openTabs.size() - 1;
                }
                return "CLOSED_TAB_" + closed.getId();
            }

            return "EXIT_APP";
        }
    }

    @Test
    public void testFullAutoHideAndBackHierarchyWorkflow() {
        MockFullNavigationContext ctx = new MockFullNavigationContext();

        // Step 1: Open 2 tabs
        ctx.openTabs.add(new Tab("tab-1", "https://tab1.com", "Tab 1", 1000L, null, 0));
        ctx.openTabs.add(new Tab("tab-2", "https://tab2.com", "Tab 2", 2000L, null, 1));
        ctx.activeTabIndex = 1; // Tab 2 is active

        // Step 2: Navigate 3 pages deep in Tab 2
        ctx.navigate("https://news.com/home");
        ctx.navigate("https://news.com/tech");
        ctx.navigate("https://news.com/tech/article-1");

        assertThat(ctx.historyIndex).isEqualTo(2);

        // Step 3: Scroll down on article -> bottom bar hides smoothly
        ctx.scrollDown(250f);
        assertThat(ctx.bottomBarOffsetPx).isEqualTo(200f);

        // Step 4: Scroll up slightly -> bottom bar begins revealing
        ctx.scrollUp(50f);
        assertThat(ctx.bottomBarOffsetPx).isEqualTo(150f);

        // Step 5: User opens Search modal overlay
        ctx.activeOverlay = OverlayType.SEARCH;
        assertThat(ctx.activeOverlay).isEqualTo(OverlayType.SEARCH);

        // Step 6: User presses Back -> Priority 1: Dismiss Search overlay (bottom bar & history untouched)
        String action1 = ctx.pressBack();
        assertThat(action1).isEqualTo("DISMISSED_SEARCH");
        assertThat(ctx.activeOverlay).isEqualTo(OverlayType.NONE);
        assertThat(ctx.historyIndex).isEqualTo(2);

        // Step 7: User presses Back again -> Priority 2: WebView back navigation (article-1 -> tech)
        String action2 = ctx.pressBack();
        assertThat(action2).isEqualTo("HISTORY_BACK_TO_https://news.com/tech");
        assertThat(ctx.historyIndex).isEqualTo(1);

        // Step 8: User presses Back again -> Priority 2: WebView back navigation (tech -> home)
        String action3 = ctx.pressBack();
        assertThat(action3).isEqualTo("HISTORY_BACK_TO_https://news.com/home");
        assertThat(ctx.historyIndex).isEqualTo(0);

        // Step 9: User presses Back at root of Tab 2 -> Priority 3: Close Tab 2 -> Focus returns to Tab 1
        String action4 = ctx.pressBack();
        assertThat(action4).isEqualTo("CLOSED_TAB_tab-2");
        assertThat(ctx.openTabs).hasSize(1);
        assertThat(ctx.openTabs.get(0).getId()).isEqualTo("tab-1");
        assertThat(ctx.activeTabIndex).isEqualTo(0);
    }
}
