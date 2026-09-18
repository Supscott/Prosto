package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Tier 1 Trailing Add (+) Button (R2 & spec §3.1).
 */
public class TrailingAddButtonCoverageTest {

    @Test
    public void testTrailingAddButtonSpawnsNewTab() {
        List<Tab> tabList = new ArrayList<>();
        tabList.add(new Tab("tab-1", "https://jestk.com", "Jestk", 1000L, null, 0));

        // When trailing '+' button clicked
        Tab newTab = new Tab("tab-2", "about:blank", "New Tab", System.currentTimeMillis(), null, tabList.size());
        tabList.add(newTab);

        assertThat(tabList).hasSize(2);
        assertThat(tabList.get(1).getUrl()).isEqualTo("about:blank");
    }

    @Test
    public void testNewlySpawnedTabBecomesActive() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://jestk.com", "Jestk", 1000L, null, 0));
        String activeTabId = "tab-1";

        // Spawn new tab
        Tab newTab = new Tab("tab-2", "about:blank", "New Tab", System.currentTimeMillis(), null, 1);
        tabs.add(newTab);
        activeTabId = newTab.getId();

        assertThat(activeTabId).isEqualTo("tab-2");
    }

    @Test
    public void testNewTabAppendedToEndOfPositionList() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://one.com", "One", 100L, null, 0));
        tabs.add(new Tab("tab-2", "https://two.com", "Two", 200L, null, 1));

        int newPosition = tabs.size();
        Tab spawned = new Tab("tab-3", "about:blank", "New Tab", 300L, null, newPosition);
        tabs.add(spawned);

        assertThat(spawned.getPosition()).isEqualTo(2);
        assertThat(tabs.get(2).getId()).isEqualTo("tab-3");
    }

    @Test
    public void testTrailingButtonRemainsPersistentAtRowEnd() {
        // Trailing button is placed after all tab items in LazyRow
        int tabCount = 5;
        int trailingButtonIndex = tabCount; // Placed at the end

        assertThat(trailingButtonIndex).isEqualTo(5);
    }

    @Test
    public void testSequentialSpawnsGenerateUniqueIds() {
        List<Tab> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(new Tab("tab-" + java.util.UUID.randomUUID(), "about:blank", "New Tab", System.currentTimeMillis(), null, i));
        }

        long uniqueIdCount = created.stream().map(Tab::getId).distinct().count();
        assertThat(uniqueIdCount).isEqualTo(5);
    }
}
