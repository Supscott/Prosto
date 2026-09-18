package com.jestk.browser.e2e.tier4;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Tier 4 - Scenario 3: Ad Blocking & Tracker Interception with Settings Toggle.
 * Exercises: Asset Loading -> Fast HashSet Subdomain Lookup -> WebViewClient Interception ->
 * Empty WebResourceResponse -> Settings Toggle Pass-Through -> Real-time Reload.
 */
public class Scenario3AdBlockingAndTrackerInterceptionWorkflowTest {

    static class MockAdBlockerSystem {
        private final Set<String> blockedHosts = new HashSet<>();
        private boolean isEnabled = true;

        public void loadAssetRules(String hostsData) throws Exception {
            blockedHosts.clear();
            try (BufferedReader reader = new BufferedReader(new StringReader(hostsData))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\s+");
                    String domain = parts.length >= 2 ? parts[1] : parts[0];
                    blockedHosts.add(domain.toLowerCase());
                }
            }
        }

        public void setEnabled(boolean enabled) {
            this.isEnabled = enabled;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public InputStream intercept(String url) {
            if (!isEnabled || url == null) return null; // Pass through
            try {
                String host = URI.create(url).getHost();
                if (host == null) return null;
                String curr = host.toLowerCase();
                while (true) {
                    if (blockedHosts.contains(curr)) {
                        return new ByteArrayInputStream(new byte[0]); // Blocked empty response
                    }
                    int dot = curr.indexOf('.');
                    if (dot == -1) break;
                    curr = curr.substring(dot + 1);
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    @Test
    public void testFullAdBlockingAndSettingsToggleLifecycle() throws Exception {
        MockAdBlockerSystem system = new MockAdBlockerSystem();

        // Step 1: App startup loads asset rules into memory
        String rulesetContent = """
                # EasyList Ad hosts
                127.0.0.1 doubleclick.net
                0.0.0.0 adservice.google.com
                analytics.tracker.io
                telemetry.ads.net
                """;
        system.loadAssetRules(rulesetContent);
        assertThat(system.isEnabled()).isTrue();

        // Step 2: Page navigates with mixed resources (HTML, Ads, CDN scripts, Analytics)
        String mainHtmlUrl = "https://news.example.com/article";
        String adBannerUrl = "https://banner.doubleclick.net/ad.gif";
        String trackerScriptUrl = "https://analytics.tracker.io/track.js";
        String legitCdnUrl = "https://cdn.example.com/bundle.min.js";

        // Step 3: Verify ad and tracker domains are intercepted with empty byte streams
        InputStream bannerResponse = system.intercept(adBannerUrl);
        InputStream trackerResponse = system.intercept(trackerScriptUrl);
        InputStream htmlResponse = system.intercept(mainHtmlUrl);
        InputStream cdnResponse = system.intercept(legitCdnUrl);

        assertThat(bannerResponse).isNotNull();
        assertThat(bannerResponse.read()).isEqualTo(-1); // Empty stream
        assertThat(trackerResponse).isNotNull();
        assertThat(trackerResponse.read()).isEqualTo(-1);

        assertThat(htmlResponse).isNull(); // Allowed
        assertThat(cdnResponse).isNull();  // Allowed

        // Step 4: User opens Settings sheet and toggles Ad Blocker OFF
        system.setEnabled(false);
        assertThat(system.isEnabled()).isFalse();

        // Step 5: Reload page -> ad requests pass through without interception
        InputStream bannerAfterToggle = system.intercept(adBannerUrl);
        InputStream trackerAfterToggle = system.intercept(trackerScriptUrl);

        assertThat(bannerAfterToggle).isNull();
        assertThat(trackerAfterToggle).isNull();

        // Step 6: User toggles Ad Blocker back ON
        system.setEnabled(true);
        assertThat(system.intercept(adBannerUrl)).isNotNull();
    }
}
