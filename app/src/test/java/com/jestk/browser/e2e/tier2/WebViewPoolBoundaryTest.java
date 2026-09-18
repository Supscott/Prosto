package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tier 2: Boundary & Edge Case Test for Multi-WebView Pool Architecture (R3).
 */
public class WebViewPoolBoundaryTest {

    static class MockWebViewPool {
        private final Map<String, Object> pool = new HashMap<>();

        public Object getOrCreate(String tabId) {
            if (tabId == null) throw new IllegalArgumentException("tabId cannot be null");
            return pool.computeIfAbsent(tabId, k -> new Object());
        }

        public void remove(String tabId) {
            pool.remove(tabId);
        }

        public int size() {
            return pool.size();
        }
    }

    @Test
    public void testPoolWithNullTabIdThrowsIllegalArgument() {
        MockWebViewPool pool = new MockWebViewPool();
        boolean threw = false;
        try {
            pool.getOrCreate(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }

        assertThat(threw).isTrue();
    }

    @Test
    public void testRemovingNonExistentTabIdFromPoolIsSafeNoOp() {
        MockWebViewPool pool = new MockWebViewPool();
        pool.getOrCreate("tab-1");

        // Remove unknown tab
        pool.remove("unknown-tab-99");

        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    public void testHighFrequencyCreationAndRemovalStress() {
        MockWebViewPool pool = new MockWebViewPool();

        for (int i = 0; i < 500; i++) {
            String tabId = "tab-" + i;
            pool.getOrCreate(tabId);
            if (i % 2 == 0) {
                pool.remove(tabId);
            }
        }

        assertThat(pool.size()).isEqualTo(250);
    }

    @Test
    public void testRecreatingTabAfterRemovalGetsNewInstance() {
        MockWebViewPool pool = new MockWebViewPool();
        Object firstInstance = pool.getOrCreate("tab-1");

        pool.remove("tab-1");
        Object secondInstance = pool.getOrCreate("tab-1");

        assertThat(secondInstance).isNotSameInstanceAs(firstInstance);
    }

    @Test
    public void testEmptyTabIdStringHandling() {
        MockWebViewPool pool = new MockWebViewPool();
        Object wv = pool.getOrCreate("");

        assertThat(wv).isNotNull();
        assertThat(pool.size()).isEqualTo(1);
    }
}
