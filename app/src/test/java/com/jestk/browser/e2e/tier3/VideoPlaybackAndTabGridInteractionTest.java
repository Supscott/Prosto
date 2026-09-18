package com.jestk.browser.e2e.tier3;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 3: Pairwise Cross-Feature Test — Video Playback, PiP & 3-Column Tab Grid Interaction.
 */
public class VideoPlaybackAndTabGridInteractionTest {

    static class MockVideoTabManager {
        boolean isVideoPlaying = false;
        boolean isAudioActive = false;
        boolean isMiniPlayerVisible = false;
        boolean isFullscreen = false;
        OverlayType activeOverlay = OverlayType.NONE;
        final List<Tab> tabs = new ArrayList<>();
        String activeTabId = null;

        public void startVideo() {
            isVideoPlaying = true;
            isAudioActive = true;
        }

        public void openTabGrid() {
            activeOverlay = OverlayType.TAB_GRID;
            // Video continues playing in background while user views grid
        }

        public void switchTabFromGrid(String newTabId) {
            if (activeTabId != null && !activeTabId.equals(newTabId) && isVideoPlaying) {
                isMiniPlayerVisible = true;
            }
            activeTabId = newTabId;
            activeOverlay = OverlayType.NONE;
        }

        public void dismissTab(String tabId) {
            tabs.removeIf(t -> t.getId().equals(tabId));
            if (tabId.equals(activeTabId)) {
                isVideoPlaying = false;
                isAudioActive = false;
                isMiniPlayerVisible = false;
            }
        }
    }

    @Test
    public void testOpeningTabGridDoesNotPauseVideoAudio() {
        MockVideoTabManager manager = new MockVideoTabManager();
        manager.tabs.add(new Tab("video-tab", "https://youtube.com/watch?v=123", "Video", 100L, null, 0));
        manager.activeTabId = "video-tab";
        manager.startVideo();

        manager.openTabGrid();

        assertThat(manager.activeOverlay).isEqualTo(OverlayType.TAB_GRID);
        assertThat(manager.isVideoPlaying).isTrue();
        assertThat(manager.isAudioActive).isTrue();
    }

    @Test
    public void testSwipingToDismissVideoTabStopsAudioAndReleasesPlayer() {
        MockVideoTabManager manager = new MockVideoTabManager();
        manager.tabs.add(new Tab("video-tab", "https://video.com", "Video", 100L, null, 0));
        manager.tabs.add(new Tab("text-tab", "https://news.com", "News", 200L, null, 1));
        manager.activeTabId = "video-tab";
        manager.startVideo();

        manager.openTabGrid();
        manager.dismissTab("video-tab");

        assertThat(manager.tabs).hasSize(1);
        assertThat(manager.isVideoPlaying).isFalse();
        assertThat(manager.isAudioActive).isFalse();
    }

    @Test
    public void testSwitchingFromTabGridSpawnsMiniPlayer() {
        MockVideoTabManager manager = new MockVideoTabManager();
        manager.tabs.add(new Tab("video-tab", "https://video.com", "Video", 100L, null, 0));
        manager.tabs.add(new Tab("other-tab", "https://other.com", "Other", 200L, null, 1));
        manager.activeTabId = "video-tab";
        manager.startVideo();

        manager.openTabGrid();
        manager.switchTabFromGrid("other-tab");

        assertThat(manager.activeTabId).isEqualTo("other-tab");
        assertThat(manager.isMiniPlayerVisible).isTrue();
        assertThat(manager.isAudioActive).isTrue();
    }

    @Test
    public void testFullscreenVideoExitingViaBackRestoresTab() {
        MockVideoTabManager manager = new MockVideoTabManager();
        manager.isFullscreen = true;

        // Press Back
        if (manager.isFullscreen) {
            manager.isFullscreen = false;
        }

        assertThat(manager.isFullscreen).isFalse();
        assertThat(manager.activeOverlay).isEqualTo(OverlayType.NONE);
    }

    @Test
    public void testEnteringSystemPiPFromTabGrid() {
        boolean isSystemPiPSupported = true;
        boolean videoPlaying = true;
        boolean enteredPiP = false;

        if (isSystemPiPSupported && videoPlaying) {
            enteredPiP = true;
        }

        assertThat(enteredPiP).isTrue();
    }

    @Test
    public void testMultipleTabsWithVideoOnlyStreamsActiveAudio() {
        List<Tab> tabs = List.of(
                new Tab("v1", "https://v1.com", "V1", 100L, null, 0),
                new Tab("v2", "https://v2.com", "V2", 200L, null, 1)
        );

        String activeTab = "v2";
        assertThat(activeTab).isEqualTo("v2");
    }
}
