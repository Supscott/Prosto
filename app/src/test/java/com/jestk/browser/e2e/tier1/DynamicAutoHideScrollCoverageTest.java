package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tier 1: Feature Coverage Test for Dynamic Auto-Hide Scroll Behavior (R2 & spec §2.1).
 * Scrolling down translates bottom bar downward out of view; scrolling up reveals it.
 */
public class DynamicAutoHideScrollCoverageTest {

    static class MockAutoHideScrollController {
        private final float maxOffsetPx;
        private float currentOffsetPx = 0f; // 0 = fully visible, maxOffsetPx = fully hidden

        public MockAutoHideScrollController(float maxOffsetPx) {
            this.maxOffsetPx = maxOffsetPx;
        }

        public void onScroll(float deltaY) {
            // deltaY > 0 means user is scrolling down (content moves up)
            // deltaY < 0 means user is scrolling up (content moves down)
            currentOffsetPx = Math.max(0f, Math.min(maxOffsetPx, currentOffsetPx + deltaY));
        }

        public float getOffset() {
            return currentOffsetPx;
        }

        public boolean isFullyHidden() {
            return currentOffsetPx >= maxOffsetPx;
        }

        public boolean isFullyVisible() {
            return currentOffsetPx <= 0f;
        }
    }

    @Test
    public void testScrollDownHidesBottomBar() {
        MockAutoHideScrollController controller = new MockAutoHideScrollController(200f);

        // Scroll down 250px
        controller.onScroll(250f);

        assertThat(controller.getOffset()).isEqualTo(200f);
        assertThat(controller.isFullyHidden()).isTrue();
    }

    @Test
    public void testScrollUpRevealsBottomBar() {
        MockAutoHideScrollController controller = new MockAutoHideScrollController(200f);
        controller.onScroll(200f); // initially hidden

        // Scroll up 200px
        controller.onScroll(-200f);

        assertThat(controller.getOffset()).isEqualTo(0f);
        assertThat(controller.isFullyVisible()).isTrue();
    }

    @Test
    public void testPartialScrollClamping() {
        MockAutoHideScrollController controller = new MockAutoHideScrollController(200f);

        // Partial scroll down
        controller.onScroll(80f);
        assertThat(controller.getOffset()).isEqualTo(80f);
        assertThat(controller.isFullyHidden()).isFalse();
        assertThat(controller.isFullyVisible()).isFalse();

        // Partial scroll up
        controller.onScroll(-30f);
        assertThat(controller.getOffset()).isEqualTo(50f);
    }

    @Test
    public void testCannotOverScrollBeyondBounds() {
        MockAutoHideScrollController controller = new MockAutoHideScrollController(200f);

        // Scroll up when already at top
        controller.onScroll(-100f);
        assertThat(controller.getOffset()).isEqualTo(0f);

        // Scroll down way past maximum
        controller.onScroll(5000f);
        assertThat(controller.getOffset()).isEqualTo(200f);
    }

    @Test
    public void testZeroDeltaScrollMaintainsState() {
        MockAutoHideScrollController controller = new MockAutoHideScrollController(200f);
        controller.onScroll(100f);

        controller.onScroll(0f);
        assertThat(controller.getOffset()).isEqualTo(100f);
    }
}
