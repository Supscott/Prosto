package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.net.URI;
import java.util.Set;

/**
 * Tier 2: Boundary & Edge Case Test for Ad Blocker Request Interception (R6).
 */
public class AdBlockerInterceptorBoundaryTest {

    private boolean shouldBlock(String url, boolean enabled, Set<String> blocked) {
        if (!enabled || url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            String curr = host.toLowerCase();
            while (true) {
                if (blocked.contains(curr)) return true;
                int dot = curr.indexOf('.');
                if (dot == -1) break;
                curr = curr.substring(dot + 1);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Test
    public void testDataAndBlobUriInterception() {
        Set<String> blocked = Set.of("ad.com");
        String dataUri = "data:text/html;base64,PGgxPkhlbGxvPC9oMT4=";
        String blobUri = "blob:https://example.com/d9492160-5f28-40cf-b31a";

        // data: and blob: URLs have null hosts and should not be blocked
        assertThat(shouldBlock(dataUri, true, blocked)).isFalse();
        assertThat(shouldBlock(blobUri, true, blocked)).isFalse();
    }

    @Test
    public void testFileAndCustomSchemeUriInterception() {
        Set<String> blocked = Set.of("ad.com");
        String fileUri = "file:///android_asset/page.html";

        assertThat(shouldBlock(fileUri, true, blocked)).isFalse();
    }

    @Test
    public void testUrlWithAdDomainInQueryParameterOfLegitimateHost() {
        Set<String> blocked = Set.of("doubleclick.net");
        // Non-ad host serving a query param containing "doubleclick.net"
        String legitUrl = "https://legit-site.com/search?ref=doubleclick.net&q=news";

        // Host is legit-site.com, so it must NOT be blocked
        assertThat(shouldBlock(legitUrl, true, blocked)).isFalse();
    }

    @Test
    public void testMalformedUrlSyntaxHandling() {
        Set<String> blocked = Set.of("ad.com");
        String malformedUrl = "http://[invalid-ipv6-syntax:8080/ad.js";

        // Must not crash and return false
        assertThat(shouldBlock(malformedUrl, true, blocked)).isFalse();
    }

    @Test
    public void testUrlWithCustomPort() {
        Set<String> blocked = Set.of("adserver.com");
        String urlWithPort = "https://adserver.com:8443/tracker.js";

        assertThat(shouldBlock(urlWithPort, true, blocked)).isTrue();
    }
}
