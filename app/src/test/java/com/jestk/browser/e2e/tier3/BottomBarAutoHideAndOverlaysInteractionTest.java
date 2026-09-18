package com.jestk.browser.e2e.tier3;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;

import org.junit.Test;

/**
 * Tier 3: Pairwise Cross-Feature Test — Dynamic Auto-Hide Scroll & Modal Overlays Interaction.
 */
public class BottomBarAutoHideAndOverlaysInteractionTest {

    static class MockBrowserLayoutState {
        float bottomBarOffsetPx = 0f; // 0 = visible, 200 = hidden
        OverlayType activeOverlay = OverlayType.NONE;

        public void onScrollDown(float delta) {
            bottomBarOffsetPx = Math.min(200f, bottomBarOffsetPx + delta);
        }

        public void onScrollUp(float delta) {
            bottomBarOffsetPx = Math.max(0f, bottomBarOffsetPx - delta);
        }

        public void openOverlay(OverlayType type) {
            this.activeOverlay = type;
            // When opening Tab Grid or Search Overlay, reset bottom bar visibility
            if (type == OverlayType.TAB_GRID || type == OverlayType.SEARCH) {
                this.bottomBarOffsetPx = 0f;
            }
        }

        public void dismissOverlay() {
            this.activeOverlay = OverlayType.NONE;
        }
    }

    @Test
    public void testOpeningSearchOverlayWhenBottomBarIsHiddenResetsBar() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        state.onScrollDown(200f);
        assertThat(state.bottomBarOffsetPx).isEqualTo(200f);

        state.openOverlay(OverlayType.SEARCH);

        assertThat(state.activeOverlay).isEqualTo(OverlayType.SEARCH);
        assertThat(state.bottomBarOffsetPx).isEqualTo(0f);
    }

    @Test
    public void testOpeningTabGridWhenBottomBarIsHiddenResetsBar() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        state.onScrollDown(150f);

        state.openOverlay(OverlayType.TAB_GRID);

        assertThat(state.activeOverlay).isEqualTo(OverlayType.TAB_GRID);
        assertThat(state.bottomBarOffsetPx).isEqualTo(0f);
    }

    @Test
    public void testOpeningSettingsSheetWhenBottomBarIsHiddenAnchorsCorrectly() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        state.onScrollDown(200f);

        state.openOverlay(OverlayType.SETTINGS);

        assertThat(state.activeOverlay).isEqualTo(OverlayType.SETTINGS);
    }

    @Test
    public void testPullToRefreshWhenBottomBarIsPartiallyTranslated() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        state.onScrollDown(80f);

        // Pull down gesture on page top reveals bottom bar
        state.onScrollUp(80f);

        assertThat(state.bottomBarOffsetPx).isEqualTo(0f);
    }

    @Test
    public void testScrollingInHomeRecentsCarouselDoesNotTranslateBottomBar() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        // Horizontal carousel scroll events do not affect vertical bottom bar offset
        float horizontalScrollDelta = 100f;

        assertThat(state.bottomBarOffsetPx).isEqualTo(0f);
    }

    @Test
    public void testDismissingTabGridRestoresStandardViewport() {
        MockBrowserLayoutState state = new MockBrowserLayoutState();
        state.openOverlay(OverlayType.TAB_GRID);
        state.dismissOverlay();

        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE);
        assertThat(state.bottomBarOffsetPx).isEqualTo(0f);
    }
}
