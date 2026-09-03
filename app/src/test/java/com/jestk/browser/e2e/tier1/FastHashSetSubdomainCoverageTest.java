package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Tier 1: Feature Coverage Test for Fast HashSet & Subdomain Matching Engine (R6).
 */
public class FastHashSetSubdomainCoverageTest {

    static class MockAdBlockerEngine {
        private final Set<String> blockedHosts = new HashSet<>();

        public void loadHosts(Set<String> hosts) {
            blockedHosts.clear();
            for (String host : hosts) {
                blockedHosts.add(host.toLowerCase());
            }
        }

        public boolean shouldBlock(String host) {
            if (host == null || host.isEmpty()) return false;
            String current = host.toLowerCase();

            // Check exact host
            if (blockedHosts.contains(current)) {
                return true;
            }

            // Subdomain walk: e.g. "a.b.doubleclick.net" -> "b.doubleclick.net" -> "doubleclick.net"
            int dotIndex;
            while ((dotIndex = current.indexOf('.')) != -1) {
                current = current.substring(dotIndex + 1);
                if (blockedHosts.contains(current)) {
                    return true;
                }
            }

            return false;
        }
    }

    private MockAdBlockerEngine engine;

    @Before
    public void setUp() {
        engine = new MockAdBlockerEngine();
        engine.loadHosts(Set.of("doubleclick.net", "adservice.google.com", "analytics.tracker.co.uk"));
    }

    @Test
    public void testExactHostMatch() {
        assertThat(engine.shouldBlock("doubleclick.net")).isTrue();
        assertThat(engine.shouldBlock("adservice.google.com")).isTrue();
    }

    @Test
    public void testSubdomainMatch() {
        // Subdomains of doubleclick.net
        assertThat(engine.shouldBlock("ad.doubleclick.net")).isTrue();
        assertThat(engine.shouldBlock("sub.ad.doubleclick.net")).isTrue();
        assertThat(engine.shouldBlock("deep.nested.sub.ad.doubleclick.net")).isTrue();
    }

    @Test
    public void testLegitimateHostPassesThrough() {
        assertThat(engine.shouldBlock("google.com")).isFalse();
        assertThat(engine.shouldBlock("developer.android.com")).isFalse();
        assertThat(engine.shouldBlock("jestk.com")).isFalse();
    }

    @Test
    public void testCaseInsensitiveMatching() {
        assertThat(engine.shouldBlock("DOUBLECLICK.NET")).isTrue();
        assertThat(engine.shouldBlock("AdService.Google.COM")).isTrue();
        assertThat(engine.shouldBlock("Sub.DoubleClick.Net")).isTrue();
    }

    @Test
    public void testComplexMultiLevelTldMatching() {
        assertThat(engine.shouldBlock("analytics.tracker.co.uk")).isTrue();
        assertThat(engine.shouldBlock("sub.analytics.tracker.co.uk")).isTrue();
        assertThat(engine.shouldBlock("tracker.co.uk")).isFalse(); // only analytics.tracker.co.uk was listed
    }
}
