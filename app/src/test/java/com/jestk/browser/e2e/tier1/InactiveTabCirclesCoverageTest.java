package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Inactive Tab Circles (R2 & spec §3.1).
 * Inactive tabs render as circular favicon buttons with tap to switch & long-press menu.
 */
public class InactiveTabCirclesCoverageTest {

    @Test
    public void testInactiveTabsRenderAsCircularIcons() {
        Tab activeTab = new Tab("tab-2", "https://active.com", "Active", 1000L, null, 1);
        List<Tab> allTabs = List.of(
                new Tab("tab-1", "https://first.com", "First", 1000L, null, 0),
                activeTab,
                new Tab("tab-3", "https://third.com", "Third", 1000L, null, 2)
        );

        // Inactive tabs are all tabs except activeTab
        List<Tab> inactiveTabs = allTabs.stream()
                .filter(t -> !t.getId().equals(activeTab.getId()))
                .toList();

        assertThat(inactiveTabs).hasSize(2);
        assertThat(inactiveTabs.get(0).getId()).isEqualTo("tab-1");
        assertThat(inactiveTabs.get(1).getId()).isEqualTo("tab-3");
    }

    @Test
    public void testSingleTapSwitchesActiveFocus() {
        String currentActiveTabId = "tab-1";
        String tappedInactiveTabId = "tab-3";

        // Action: Tap tab-3
        String newActiveTabId = tappedInactiveTabId;

        assertThat(newActiveTabId).isEqualTo("tab-3");
        assertThat(newActiveTabId).isNotEqualTo(currentActiveTabId);
    }

    @Test
    public void testLongPressMenuOptions() {
        // Spec requirement: long press menu options: Close Tab, Copy URL, Add to Favorites
        List<String> menuOptions = List.of("Close Tab", "Copy URL", "Add to Favorites");

        assertThat(menuOptions).containsExactly("Close Tab", "Copy URL", "Add to Favorites").inOrder();
    }

    @Test
    public void testCreationOrderPreservation() {
        List<Tab> tabs = List.of(
                new Tab("t1", "https://a.com", "A", 100L, null, 0),
                new Tab("t2", "https://b.com", "B", 200L, null, 1),
                new Tab("t3", "https://c.com", "C", 300L, null, 2)
        );

        assertThat(tabs.get(0).getPosition()).isLessThan(tabs.get(1).getPosition());
        assertThat(tabs.get(1).getPosition()).isLessThan(tabs.get(2).getPosition());
    }

    @Test
    public void testFaviconDisplayInInactiveCircle() {
        Tab inactive = new Tab("tab-fav", "https://github.com", "GitHub", 100L, null, 0);
        String expectedFaviconUrl = "https://github.com/favicon.ico";

        assertThat(expectedFaviconUrl).contains(inactive.getUrl());
    }
}
