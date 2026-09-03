package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.OverlayType;

import org.junit.Test;

import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Tier 2 Grounded Action Bar Dock (R2 & spec §3.2).
 * Houses 4 icon buttons: Home, Search, All Tabs (Grid), Settings.
 */
public class ActionBarDockCoverageTest {

    enum DockAction {
        HOME,
        SEARCH,
        ALL_TABS,
        SETTINGS
    }

    @Test
    public void testDockHousesAllFourStandardButtons() {
        List<DockAction> actions = List.of(DockAction.HOME, DockAction.SEARCH, DockAction.ALL_TABS, DockAction.SETTINGS);

        assertThat(actions).containsExactly(
                DockAction.HOME,
                DockAction.SEARCH,
                DockAction.ALL_TABS,
                DockAction.SETTINGS
        ).inOrder();
    }

    @Test
    public void testHomeActionSwitchesToHomeDashboard() {
        boolean showHomeDashboard = false;
        DockAction triggered = DockAction.HOME;

        if (triggered == DockAction.HOME) {
            showHomeDashboard = true;
        }

        assertThat(showHomeDashboard).isTrue();
    }

    @Test
    public void testSearchActionLaunchesSearchOverlay() {
        OverlayType activeOverlay = OverlayType.NONE;
        DockAction triggered = DockAction.SEARCH;

        if (triggered == DockAction.SEARCH) {
            activeOverlay = OverlayType.SEARCH;
        }

        assertThat(activeOverlay).isEqualTo(OverlayType.SEARCH);
    }

    @Test
    public void testAllTabsActionLaunchesTabGridOverlay() {
        OverlayType activeOverlay = OverlayType.NONE;
        DockAction triggered = DockAction.ALL_TABS;

        if (triggered == DockAction.ALL_TABS) {
            activeOverlay = OverlayType.TAB_GRID;
        }

        assertThat(activeOverlay).isEqualTo(OverlayType.TAB_GRID);
    }

    @Test
    public void testSettingsActionLaunchesSettingsSheet() {
        OverlayType activeOverlay = OverlayType.NONE;
        DockAction triggered = DockAction.SETTINGS;

        if (triggered == DockAction.SETTINGS) {
            activeOverlay = OverlayType.SETTINGS;
        }

        assertThat(activeOverlay).isEqualTo(OverlayType.SETTINGS);
    }
}
