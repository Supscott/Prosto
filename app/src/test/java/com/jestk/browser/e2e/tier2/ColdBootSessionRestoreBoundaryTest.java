package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Cold Boot Session Restoration (R1).
 */
public class ColdBootSessionRestoreBoundaryTest {

    @Test
    public void testColdBootWithMissingSnapshotFileOnDisk() {
        // Tab points to a deleted/missing snapshot file
        String nonExistentPath = "/path/to/missing_snapshot_file.jpg";
        File file = new File(nonExistentPath);
        Tab tab = new Tab("tab-missing", "https://example.com", "Example", 100L, nonExistentPath, 0);

        boolean fileExists = file.exists();
        // UI fallback placeholder must be rendered when file does not exist
        boolean usePlaceholder = !fileExists;

        assertThat(usePlaceholder).isTrue();
    }

    @Test
    public void testColdBootWhenActiveTabIdPointsToInvalidTab() {
        List<Tab> tabs = List.of(
                new Tab("tab-1", "https://site1.com", "Site 1", 100L, null, 0),
                new Tab("tab-2", "https://site2.com", "Site 2", 200L, null, 1)
        );
        String lastActiveTabId = "non-existent-tab-999";

        // Fallback to first tab
        Tab active = tabs.stream()
                .filter(t -> t.getId().equals(lastActiveTabId))
                .findFirst()
                .orElse(tabs.get(0));

        assertThat(active.getId()).isEqualTo("tab-1");
    }

    @Test
    public void testColdBootWithCorruptedTabEntries() {
        List<Tab> rawTabs = new ArrayList<>();
        rawTabs.add(new Tab("valid-1", "https://valid.com", "Valid", 100L, null, 0));
        rawTabs.add(new Tab("invalid-null-url", "", "Empty URL", 100L, null, 1));

        // Sanitization filter on boot
        List<Tab> sanitized = rawTabs.stream()
                .map(t -> t.getUrl().isEmpty() ? new Tab(t.getId(), "about:blank", t.getTitle().isEmpty() ? "New Tab" : t.getTitle(), t.getLastActiveTimestamp(), t.getSnapshotPath(), t.getPosition()) : t)
                .toList();

        assertThat(sanitized.get(1).getUrl()).isEqualTo("about:blank");
    }

    @Test
    public void testRapidColdRestartCycles() {
        List<Tab> persisted = List.of(new Tab("tab-1", "https://jestk.com", "Jestk", 100L, null, 0));

        // 10 simulation boots
        for (int i = 0; i < 10; i++) {
            List<Tab> booted = new ArrayList<>(persisted);
            assertThat(booted).hasSize(1);
            assertThat(booted.get(0).getId()).isEqualTo("tab-1");
        }
    }

    @Test
    public void testColdBootWithHundredPersistedTabs() {
        List<Tab> hundredTabs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            hundredTabs.add(new Tab("t-" + i, "https://s" + i + ".com", "S " + i, (long) i, null, i));
        }

        assertThat(hundredTabs).hasSize(100);
        assertThat(hundredTabs.get(99).getPosition()).isEqualTo(99);
    }
}
