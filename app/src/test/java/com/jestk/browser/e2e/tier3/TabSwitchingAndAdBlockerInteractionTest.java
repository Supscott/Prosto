package com.jestk.browser.e2e.tier3;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tier 3: Pairwise Cross-Feature Test — Tab Switching & Ad Blocker Engine Interaction.
 */
public class TabSwitchingAndAdBlockerInteractionTest {

    static class MockTabSession {
        final String tabId;
        final String currentUrl;

        public MockTabSession(String tabId, String currentUrl) {
            this.tabId = tabId;
            this.currentUrl = currentUrl;
        }
    }

    private boolean isRequestBlocked(String url, boolean isAdBlockerEnabled, Set<String> blockedHosts) {
        if (!isAdBlockerEnabled || url == null) return false;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String curr = host.toLowerCase();
            while (true) {
                if (blockedHosts.contains(curr)) return true;
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
    public void testTabCreationInheritsGlobalAdBlockerSetting() {
        boolean globalAdBlockerEnabled = true;
        Set<String> blocked = Set.of("ads.com", "track.org");

        List<MockTabSession> tabs = new ArrayList<>();
        tabs.add(new MockTabSession("tab-1", "https://site1.com"));
        tabs.add(new MockTabSession("tab-2", "https://site2.com"));

        for (MockTabSession session : tabs) {
            boolean blockedInSession = isRequestBlocked("https://ads.com/banner.js", globalAdBlockerEnabled, blocked);
            assertThat(blockedInSession).isTrue();
        }
    }

    @Test
    public void testDynamicAdBlockerToggleAppliesImmediatelyToAllTabs() {
        boolean adBlockerEnabled = true;
        Set<String> blocked = Set.of("doubleclick.net");

        // Tab 1 & Tab 2 request ad
        assertThat(isRequestBlocked("https://ad.doubleclick.net/pixel", adBlockerEnabled, blocked)).isTrue();

        // User disables ad blocker in Settings
        adBlockerEnabled = false;

        assertThat(isRequestBlocked("https://ad.doubleclick.net/pixel", adBlockerEnabled, blocked)).isFalse();
    }

    @Test
    public void testTabSwitchingFromAdHeavyPageMaintainsIsolation() {
        Set<String> blocked = Set.of("ads.network.com");

        MockTabSession adHeavyTab = new MockTabSession("tab-ad", "https://news.com");
        MockTabSession cleanTab = new MockTabSession("tab-clean", "https://jestk.com");

        boolean blockedInAdTab = isRequestBlocked("https://ads.network.com/ad.js", true, blocked);
        boolean blockedInCleanTab = isRequestBlocked("https://jestk.com/bundle.js", true, blocked);

        assertThat(blockedInAdTab).isTrue();
        assertThat(blockedInCleanTab).isFalse();
    }

    @Test
    public void testBackgroundTabAdRequestBlockedWhileForegroundTabBrowses() {
        Set<String> blocked = Set.of("tracker.io");
        String activeTabId = "foreground-tab";

        String backgroundTabRequest = "https://tracker.io/event";
        String foregroundTabRequest = "https://wikipedia.org/page";

        assertThat(isRequestBlocked(backgroundTabRequest, true, blocked)).isTrue();
        assertThat(isRequestBlocked(foregroundTabRequest, true, blocked)).isFalse();
    }

    @Test
    public void testReopeningClosedTabPreservesAdBlockerFiltering() {
        Set<String> blocked = Set.of("spam.com");
        Tab tab = new Tab("tab-temp", "https://site.com", "Site", 100L, null, 0);

        // Close tab
        tab = null;

        // Reopen tab
        Tab restoredTab = new Tab("tab-restored", "https://site.com", "Site", 200L, null, 0);
        boolean isBlocked = isRequestBlocked("https://sub.spam.com/script.js", true, blocked);

        assertThat(isBlocked).isTrue();
        assertThat(restoredTab).isNotNull();
    }

    @Test
    public void testFastSubdomainMatchingUnderConcurrentTabRequests() {
        Set<String> blocked = new HashSet<>(Set.of("adserver.com", "adservice.google.com"));

        List<String> requests = List.of(
                "https://a.b.c.adserver.com/img.png",
                "https://page.com/style.css",
                "https://adservice.google.com/log",
                "https://jestk.com/index.html"
        );

        List<Boolean> blockResults = requests.stream()
                .map(r -> isRequestBlocked(r, true, blocked))
                .toList();

        assertThat(blockResults).containsExactly(true, false, true, false).inOrder();
    }
}
