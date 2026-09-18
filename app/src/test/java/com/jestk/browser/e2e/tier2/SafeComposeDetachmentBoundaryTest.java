package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Safe Compose Detachment (R3).
 */
public class SafeComposeDetachmentBoundaryTest {

    static class MockContainer {
        final List<MockChild> children = new ArrayList<>();

        public void add(MockChild child) {
            if (child.parent != null && child.parent != this) {
                throw new IllegalStateException("View already has a parent");
            }
            children.add(child);
            child.parent = this;
        }

        public void remove(MockChild child) {
            children.remove(child);
            if (child.parent == this) {
                child.parent = null;
            }
        }
    }

    static class MockChild {
        MockContainer parent = null;

        public void safeDetach() {
            if (parent != null) {
                parent.remove(this);
            }
        }
    }

    @Test
    public void testStressDetachmentInRapidTabSwitchingLoop() {
        MockContainer container = new MockContainer();
        List<MockChild> children = List.of(new MockChild(), new MockChild(), new MockChild());

        // Switch tabs 100 times rapidly
        for (int i = 0; i < 100; i++) {
            MockChild active = children.get(i % children.size());
            active.safeDetach();
            container.add(active);
            assertThat(active.parent).isSameInstanceAs(container);
            active.safeDetach();
        }

        assertThat(container.children).isEmpty();
    }

    @Test
    public void testSafeDetachWhenAlreadyDetachedDoesNotThrow() {
        MockChild child = new MockChild();
        assertThat(child.parent).isNull();

        child.safeDetach();
        child.safeDetach();

        assertThat(child.parent).isNull();
    }

    @Test
    public void testReattachingToSameContainerMultipleTimesWithSafeDetach() {
        MockContainer container = new MockContainer();
        MockChild child = new MockChild();

        for (int i = 0; i < 10; i++) {
            child.safeDetach();
            container.add(child);
            assertThat(container.children).hasSize(1);
        }
    }

    @Test
    public void testDetachingAllChildrenInBulk() {
        MockContainer container = new MockContainer();
        List<MockChild> list = List.of(new MockChild(), new MockChild(), new MockChild());

        for (MockChild c : list) {
            container.add(c);
        }
        assertThat(container.children).hasSize(3);

        for (MockChild c : list) {
            c.safeDetach();
        }

        assertThat(container.children).isEmpty();
    }

    @Test
    public void testChildAddedToContainerThenRemovedExternally() {
        MockContainer container = new MockContainer();
        MockChild child = new MockChild();

        container.add(child);
        container.remove(child);

        assertThat(child.parent).isNull();
        // Subsequent safe detach should still succeed
        child.safeDetach();
        assertThat(child.parent).isNull();
    }
}
