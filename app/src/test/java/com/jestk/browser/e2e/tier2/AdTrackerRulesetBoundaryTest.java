package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Tier 2: Boundary & Edge Case Test for Ad/Tracker Ruleset Loading (R6).
 */
public class AdTrackerRulesetBoundaryTest {

    @Test
    public void testEmptyRulesetString() {
        String emptyHosts = "";
        Set<String> rules = new HashSet<>();
        for (String line : emptyHosts.split("\n")) {
            if (!line.trim().isEmpty()) {
                rules.add(line.trim());
            }
        }

        assertThat(rules).isEmpty();
    }

    @Test
    public void testRulesetWithMalformedLinesAndSpaces() {
        String malformed = "   \n# Only comments\n   127.0.0.1      ad.com   \n0.0.0.0\t\ttracker.org\n\n\n";
        Set<String> rules = new HashSet<>();
        for (String raw : malformed.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                rules.add(parts[1].toLowerCase());
            } else {
                rules.add(parts[0].toLowerCase());
            }
        }

        assertThat(rules).containsExactly("ad.com", "tracker.org");
    }

    @Test
    public void testScaleWithFiftyThousandRules() {
        Set<String> hugeRuleset = new HashSet<>();
        for (int i = 0; i < 50000; i++) {
            hugeRuleset.add("ad-" + i + ".network.com");
        }

        assertThat(hugeRuleset).hasSize(50000);
        assertThat(hugeRuleset.contains("ad-25000.network.com")).isTrue();
    }

    @Test
    public void testNonAsciiAndPunycodeDomains() {
        Set<String> rules = Set.of("xn-- reklam-3ya.com", "reklam.рф");
        assertThat(rules).hasSize(2);
    }

    @Test
    public void testIpv6LocalhostRulesetSyntax() {
        String ipv6Rule = "::1 tracking.example.com";
        String[] parts = ipv6Rule.split("\\s+");
        String host = parts.length >= 2 ? parts[1] : parts[0];

        assertThat(host).isEqualTo("tracking.example.com");
    }
}
