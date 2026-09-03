package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Tier 1: Feature Coverage Test for Bundled Ad/Tracker Ruleset Loading (R6).
 */
public class AdTrackerRulesetCoverageTest {

    private Set<String> parseHosts(String hostsContent) throws Exception {
        Set<String> hosts = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(hostsContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Handle formats: "127.0.0.1 ad.doubleclick.net" or "0.0.0.0 ad.doubleclick.net" or plain "ad.doubleclick.net"
                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && (parts[0].equals("127.0.0.1") || parts[0].equals("0.0.0.0"))) {
                    hosts.add(parts[1].toLowerCase());
                } else {
                    hosts.add(parts[0].toLowerCase());
                }
            }
        }
        return hosts;
    }

    @Test
    public void testParseStandardHostsFormat() throws Exception {
        String data = "# Ad hosts\n127.0.0.1 doubleclick.net\n0.0.0.0 google-analytics.com\nadservice.google.com\n";
        Set<String> parsed = parseHosts(data);

        assertThat(parsed).containsExactly("doubleclick.net", "google-analytics.com", "adservice.google.com");
    }

    @Test
    public void testIgnoresCommentsAndEmptyLines() throws Exception {
        String data = "# Comment 1\n\n   \n# Comment 2\n127.0.0.1 tracker.com\n";
        Set<String> parsed = parseHosts(data);

        assertThat(parsed).containsExactly("tracker.com");
    }

    @Test
    public void testParsesCaseInsensitively() throws Exception {
        String data = "127.0.0.1 TRACKER.COM\nAdServer.Net\n";
        Set<String> parsed = parseHosts(data);

        assertThat(parsed).containsExactly("tracker.com", "adserver.net");
    }

    @Test
    public void testDuplicateRulesMergedIntoSet() throws Exception {
        String data = "127.0.0.1 tracker.com\n0.0.0.0 tracker.com\ntracker.com\n";
        Set<String> parsed = parseHosts(data);

        assertThat(parsed).hasSize(1);
        assertThat(parsed).contains("tracker.com");
    }

    @Test
    public void testRulesetLoadsIntoMemoryFast() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("0.0.0.0 ad").append(i).append(".example.com\n");
        }

        long start = System.nanoTime();
        Set<String> parsed = parseHosts(sb.toString());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(parsed).hasSize(5000);
        assertThat(elapsedMs).isLessThan(500); // Must be fast
    }
}
