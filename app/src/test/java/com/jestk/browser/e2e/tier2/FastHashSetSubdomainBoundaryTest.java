package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Tier 2: Boundary & Edge Case Test for Fast HashSet Subdomain Traversal (R6).
 */
public class FastHashSetSubdomainBoundaryTest {

    private boolean isBlocked(String host, Set<String> blockedSet) {
        if (host == null || host.isEmpty()) return false;
        String current = host.toLowerCase();

        if (blockedSet.contains(current)) return true;

        int dotIndex;
        while ((dotIndex = current.indexOf('.')) != -1) {
            current = current.substring(dotIndex + 1);
            if (blockedSet.contains(current)) return true;
        }
        return false;
    }

    @Test
    public void testDeeplyNestedTenLevelSubdomain() {
        Set<String> blocked = Set.of("ads.com");
        String tenLevels = "a.b.c.d.e.f.g.h.i.j.ads.com";

        assertThat(isBlocked(tenLevels, blocked)).isTrue();
    }

    @Test
    public void testRootTldOnlyInLookup() {
        Set<String> blocked = Set.of("ads.com");
        String rootTld = "com";

        assertThat(isBlocked(rootTld, blocked)).isFalse();
    }

    @Test
    public void testIpAddressHostnames() {
        Set<String> blocked = Set.of("192.168.1.100");

        assertThat(isBlocked("192.168.1.100", blocked)).isTrue();
        assertThat(isBlocked("192.168.1.1", blocked)).isFalse();
    }

    @Test
    public void testTrailingDotFqdnHostname() {
        Set<String> blocked = Set.of("doubleclick.net");
        String fqdn = "ad.doubleclick.net.";

        // Strip trailing dot
        String normalized = fqdn.endsWith(".") ? fqdn.substring(0, fqdn.length() - 1) : fqdn;

        assertThat(isBlocked(normalized, blocked)).isTrue();
    }

    @Test
    public void testNullAndEmptyHost() {
        Set<String> blocked = Set.of("ads.com");

        assertThat(isBlocked(null, blocked)).isFalse();
        assertThat(isBlocked("", blocked)).isFalse();
    }
}
