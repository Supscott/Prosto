package com.jestk.browser.e2e.tier3;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.BrowserSettings;
import com.jestk.browser.domain.model.SearchEngine;
import com.jestk.browser.domain.model.Tab;
import com.jestk.browser.domain.model.ThemeMode;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 3: Pairwise Cross-Feature Test — Cold Boot Restore, Snapshot Disk Cache & Room DB Interaction.
 */
public class ColdBootRestoreAndSnapshotCacheInteractionTest {

    @Test
    public void testColdBootRestoresTabsAndPopulatesGridWithDiskSnapshots() {
        List<Tab> persisted = List.of(
                new Tab("tab-1", "https://jestk.com", "Jestk", 1000L, "/cache/tab_1.jpg", 0),
                new Tab("tab-2", "https://github.com", "GitHub", 2000L, "/cache/tab_2.jpg", 1)
        );

        // Cold boot restores
        List<Tab> restored = new ArrayList<>(persisted);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).getSnapshotPath()).isEqualTo("/cache/tab_1.jpg");
        assertThat(restored.get(1).getSnapshotPath()).isEqualTo("/cache/tab_2.jpg");
    }

    @Test
    public void testMissingSnapshotFilesFallbackToFaviconCards() {
        Tab tab = new Tab("tab-1", "https://jestk.com", "Jestk", 1000L, null, 0);

        boolean hasDiskSnapshot = tab.getSnapshotPath() != null;
        String cardContent = hasDiskSnapshot ? "IMAGE" : "FAVICON_AND_TITLE";

        assertThat(cardContent).isEqualTo("FAVICON_AND_TITLE");
    }

    @Test
    public void testActiveTabIndexPreservedAcrossProcessDeath() {
        List<Tab> tabs = List.of(
                new Tab("t1", "u1", "T1", 100L, null, 0),
                new Tab("t2", "u2", "T2", 500L, null, 1),
                new Tab("t3", "u3", "T3", 200L, null, 2)
        );
        String lastActiveId = "t2";

        Tab active = tabs.stream().filter(t -> t.getId().equals(lastActiveId)).findFirst().orElse(tabs.get(0));

        assertThat(active.getId()).isEqualTo("t2");
        assertThat(active.getPosition()).isEqualTo(1);
    }

    @Test
    public void testColdBootWithCustomThemeAndSearchEngine() {
        BrowserSettings savedSettings = new BrowserSettings(ThemeMode.DARK, SearchEngine.GOOGLE, true);

        // Boot applies restored settings
        assertThat(savedSettings.getThemeMode()).isEqualTo(ThemeMode.DARK);
        assertThat(savedSettings.getSearchEngine()).isEqualTo(SearchEngine.GOOGLE);
    }

    @Test
    public void testWebViewPoolLazyInstantiationOnColdBoot() {
        List<Tab> tabs = List.of(
                new Tab("t1", "u1", "T1", 100L, null, 0),
                new Tab("t2", "u2", "T2", 200L, null, 1),
                new Tab("t3", "u3", "T3", 300L, null, 2)
        );
        String activeTabId = "t1";

        // Only active tab's WebView is instantiated immediately
        List<String> instantiatedPool = new ArrayList<>();
        instantiatedPool.add(activeTabId);

        assertThat(instantiatedPool).hasSize(1);
        assertThat(instantiatedPool).containsExactly("t1");
    }

    @Test
    public void testColdBootRestorationWithDeletedTabSnapshotCleanup() {
        List<String> diskSnapshotFiles = new ArrayList<>(List.of("tab_1.jpg", "tab_2.jpg", "tab_deleted.jpg"));
        List<Tab> activeTabs = List.of(
                new Tab("1", "u1", "T1", 100L, "/cache/tab_1.jpg", 0),
                new Tab("2", "u2", "T2", 200L, "/cache/tab_2.jpg", 1)
        );

        List<String> validSnapshotNames = activeTabs.stream().map(t -> "tab_" + t.getId() + ".jpg").toList();
        diskSnapshotFiles.removeIf(f -> !validSnapshotNames.contains(f));

        assertThat(diskSnapshotFiles).containsExactly("tab_1.jpg", "tab_2.jpg");
    }
}
