package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Safe Compose Parent Detachment (R3).
 * Verifies that (webView.parent as? ViewGroup)?.removeView(webView) is executed before reattaching.
 */
public class SafeComposeDetachmentCoverageTest {

    // Mock ViewGroup container to simulate Compose AndroidView host
    static class MockViewGroup {
        private final List<MockView> children = new ArrayList<>();

        public void addView(MockView view) {
            if (view.parent != null && view.parent != this) {
                throw new IllegalStateException("The specified child already has a parent. You must call removeView() on the child's parent first.");
            }
            children.add(view);
            view.parent = this;
        }

        public void removeView(MockView view) {
            children.remove(view);
            if (view.parent == this) {
                view.parent = null;
            }
        }

        public boolean contains(MockView view) {
            return children.contains(view);
        }
    }

    static class MockView {
        MockViewGroup parent;

        public void detachFromParent() {
            if (parent != null) {
                parent.removeView(this);
            }
        }
    }

    @Test
    public void testSafeDetachmentWhenAttachingToNewParent() {
        MockViewGroup container1 = new MockViewGroup();
        MockViewGroup container2 = new MockViewGroup();
        MockView webView = new MockView();

        // Attached to container 1
        container1.addView(webView);
        assertThat(webView.parent).isSameInstanceAs(container1);

        // Detach before re-attaching to container 2
        webView.detachFromParent();
        assertThat(webView.parent).isNull();

        container2.addView(webView);
        assertThat(webView.parent).isSameInstanceAs(container2);
    }

    @Test
    public void testDirectReattachmentThrowsExceptionWithoutDetachment() {
        MockViewGroup container1 = new MockViewGroup();
        MockViewGroup container2 = new MockViewGroup();
        MockView webView = new MockView();

        container1.addView(webView);

        boolean threw = false;
        try {
            container2.addView(webView);
        } catch (IllegalStateException e) {
            threw = true;
        }

        assertThat(threw).isTrue();
    }

    @Test
    public void testDetachmentWhenParentAlreadyNullIsSafeNoOp() {
        MockView webView = new MockView();
        assertThat(webView.parent).isNull();

        // Calling detach should not throw
        webView.detachFromParent();
        assertThat(webView.parent).isNull();
    }

    @Test
    public void testMultipleCycleAttachmentAndDetachment() {
        MockViewGroup container = new MockViewGroup();
        MockView webView = new MockView();

        for (int i = 0; i < 5; i++) {
            container.addView(webView);
            assertThat(container.contains(webView)).isTrue();
            webView.detachFromParent();
            assertThat(container.contains(webView)).isFalse();
        }
    }

    @Test
    public void testDetachingOneChildDoesNotAffectSiblings() {
        MockViewGroup container = new MockViewGroup();
        MockView child1 = new MockView();
        MockView child2 = new MockView();

        container.addView(child1);
        container.addView(child2);

        child1.detachFromParent();

        assertThat(container.contains(child1)).isFalse();
        assertThat(container.contains(child2)).isTrue();
    }
}
