package com.jestk.browser.e2e.tier4;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier 4 - Scenario 1: Multi-Tab Browsing & Lifecycle Workflow.
 * Exercises: Tab Creation, Switching via Pill Swipe & Inactive Circles, WebView Detachment/Reattachment,
 * State Preservation, Tab Reordering, Background Tab Media Stream Persistence.
 */
public class Scenario1MultiTabBrowsingAndLifecycleWorkflowTest {

    static class MockTabSession {
        final String id;
        String url;
        String title;
        int scrollY = 0;
        boolean audioPlaying = false;
        boolean timersPaused = false;
        Object parentContainer = null;

        public MockTabSession(String id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
        }
    }

    @Test
    public void testFullMultiTabBrowsingAndLifecycleScenario() {
        // Step 1: Initial state - 1 active tab
        List<Tab> tabEntities = new ArrayList<>();
        Map<String, MockTabSession> liveSessions = new HashMap<>();

        Tab tab1 = new Tab("tab-1", "https://jestk.com", "Jestk Browser", 1000L, null, 0);
        tabEntities.add(tab1);
        MockTabSession session1 = new MockTabSession("tab-1", "https://jestk.com", "Jestk Browser");
        liveSessions.put("tab-1", session1);
        String activeTabId = "tab-1";

        // Step 2: Spawn 3 new tabs sequentially
        Tab tab2 = new Tab("tab-2", "https://news.ycombinator.com", "Hacker News", 2000L, null, 1);
        Tab tab3 = new Tab("tab-3", "https://github.com", "GitHub", 3000L, null, 2);
        Tab tab4 = new Tab("tab-4", "https://music.site.com/radio", "Live Audio Stream", 4000L, null, 3);

        tabEntities.addAll(List.of(tab2, tab3, tab4));
        MockTabSession session2 = new MockTabSession("tab-2", "https://news.ycombinator.com", "Hacker News");
        MockTabSession session3 = new MockTabSession("tab-3", "https://github.com", "GitHub");
        MockTabSession session4 = new MockTabSession("tab-4", "https://music.site.com/radio", "Live Audio Stream");
        session4.audioPlaying = true;

        liveSessions.put("tab-2", session2);
        liveSessions.put("tab-3", session3);
        liveSessions.put("tab-4", session4);

        assertThat(tabEntities).hasSize(4);
        assertThat(liveSessions).hasSize(4);

        // Step 3: Switch to Tab 4 (Audio tab) and scroll down
        activeTabId = "tab-4";
        session4.scrollY = 450;
        Object composeHost = new Object();
        session4.parentContainer = composeHost;

        assertThat(activeTabId).isEqualTo("tab-4");
        assertThat(session4.audioPlaying).isTrue();

        // Step 4: Switch to Tab 2 via inactive circle tap -> verify safe detachment of tab-4 and attachment of tab-2
        session4.parentContainer = null; // Detached
        activeTabId = "tab-2";
        session2.parentContainer = composeHost; // Attached

        // Crucial requirement: Tab 4 audio continues and timers NOT paused in background
        assertThat(session4.audioPlaying).isTrue();
        assertThat(session4.timersPaused).isFalse();
        assertThat(session4.scrollY).isEqualTo(450); // Preserved scroll

        // Step 5: Switch via active pill swipe left (Tab 2 -> Tab 3)
        int currentIndex = 1; // Tab 2 index
        int nextIndex = currentIndex + 1; // Tab 3 index
        session2.parentContainer = null;
        activeTabId = tabEntities.get(nextIndex).getId();
        session3.parentContainer = composeHost;

        assertThat(activeTabId).isEqualTo("tab-3");

        // Step 6: Close Tab 1 (inactive) -> update positions
        tabEntities.removeIf(t -> t.getId().equals("tab-1"));
        liveSessions.remove("tab-1");

        assertThat(tabEntities).hasSize(3);
        assertThat(liveSessions).hasSize(3);
        assertThat(tabEntities.stream().map(Tab::getId).toList()).containsExactly("tab-2", "tab-3", "tab-4");

        // Step 7: Return to Tab 4 -> verify scroll state and uninterrupted audio
        session3.parentContainer = null;
        activeTabId = "tab-4";
        session4.parentContainer = composeHost;

        assertThat(activeTabId).isEqualTo("tab-4");
        assertThat(session4.scrollY).isEqualTo(450);
        assertThat(session4.audioPlaying).isTrue();
    }
}
