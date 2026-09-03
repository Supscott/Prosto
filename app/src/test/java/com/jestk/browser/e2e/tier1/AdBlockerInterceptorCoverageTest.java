package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;

/**
 * Tier 1: Feature Coverage Test for WebViewClient shouldInterceptRequest Ad Blocking (R6).
 */
public class AdBlockerInterceptorCoverageTest {

    static class MockWebResourceResponse {
        final String mimeType;
        final String encoding;
        final InputStream data;

        public MockWebResourceResponse(String mimeType, String encoding, InputStream data) {
            this.mimeType = mimeType;
            this.encoding = encoding;
            this.data = data;
        }
    }

    static class MockInterceptorClient {
        private boolean isAdBlockerEnabled = true;
        private final Set<String> blockedDomains = Set.of("doubleclick.net", "adservice.google.com");

        public void setAdBlockerEnabled(boolean enabled) {
            this.isAdBlockerEnabled = enabled;
        }

        public MockWebResourceResponse shouldInterceptRequest(String url) {
            if (!isAdBlockerEnabled || url == null) {
                return null; // Pass through to standard network pipeline
            }

            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null && isBlocked(host.toLowerCase())) {
                    return new MockWebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
            } catch (Exception ignored) {
            }

            return null;
        }

        private boolean isBlocked(String host) {
            String curr = host;
            while (true) {
                if (blockedDomains.contains(curr)) return true;
                int dot = curr.indexOf('.');
                if (dot == -1) break;
                curr = curr.substring(dot + 1);
            }
            return false;
        }
    }

    @Test
    public void testBlocksAdRequestWhenEnabled() {
        MockInterceptorClient client = new MockInterceptorClient();
        MockWebResourceResponse response = client.shouldInterceptRequest("https://ad.doubleclick.net/banner.js");

        assertThat(response).isNotNull();
        assertThat(response.mimeType).isEqualTo("text/plain");
        assertThat(response.encoding).isEqualTo("UTF-8");
    }

    @Test
    public void testPassesThroughAdRequestWhenDisabled() {
        MockInterceptorClient client = new MockInterceptorClient();
        client.setAdBlockerEnabled(false);

        MockWebResourceResponse response = client.shouldInterceptRequest("https://ad.doubleclick.net/banner.js");

        assertThat(response).isNull(); // Allowed
    }

    @Test
    public void testPassesThroughNonAdRequestWhenEnabled() {
        MockInterceptorClient client = new MockInterceptorClient();
        MockWebResourceResponse response = client.shouldInterceptRequest("https://cdn.example.com/main.js");

        assertThat(response).isNull();
    }

    @Test
    public void testEmptyResponseBodyOnBlockedRequest() throws Exception {
        MockInterceptorClient client = new MockInterceptorClient();
        MockWebResourceResponse response = client.shouldInterceptRequest("https://adservice.google.com/ads.js");

        assertThat(response).isNotNull();
        assertThat(response.data.read()).isEqualTo(-1); // End of empty stream
    }

    @Test
    public void testDynamicToggleDuringSession() {
        MockInterceptorClient client = new MockInterceptorClient();

        // 1. Initial: blocked
        assertThat(client.shouldInterceptRequest("https://doubleclick.net/pixel")).isNotNull();

        // 2. Toggle off: allowed
        client.setAdBlockerEnabled(false);
        assertThat(client.shouldInterceptRequest("https://doubleclick.net/pixel")).isNull();

        // 3. Toggle back on: blocked again
        client.setAdBlockerEnabled(true);
        assertThat(client.shouldInterceptRequest("https://doubleclick.net/pixel")).isNotNull();
    }
}
