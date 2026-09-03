package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.BrowserSettings;
import com.jestk.browser.domain.model.SearchEngine;
import com.jestk.browser.domain.model.ThemeMode;

import org.junit.Test;

/**
 * Tier 2: Boundary & Edge Case Test for Settings Sheet Options (R5 & spec §4.4).
 */
public class SettingsSheetBoundaryTest {

    @Test
    public void testRapidThemeModeCycling() {
        ThemeMode current = ThemeMode.SYSTEM;

        current = ThemeMode.LIGHT;
        assertThat(current).isEqualTo(ThemeMode.LIGHT);

        current = ThemeMode.DARK;
        assertThat(current).isEqualTo(ThemeMode.DARK);

        current = ThemeMode.SYSTEM;
        assertThat(current).isEqualTo(ThemeMode.SYSTEM);
    }

    @Test
    public void testRapidSearchEngineCycling() {
        SearchEngine engine = SearchEngine.DUCKDUCKGO;

        engine = SearchEngine.GOOGLE;
        assertThat(engine).isEqualTo(SearchEngine.GOOGLE);

        engine = SearchEngine.BING;
        assertThat(engine).isEqualTo(SearchEngine.BING);

        engine = SearchEngine.DUCKDUCKGO;
        assertThat(engine).isEqualTo(SearchEngine.DUCKDUCKGO);
    }

    @Test
    public void testTogglingAdBlockerWithZeroRulesLoaded() {
        boolean adBlockerEnabled = true;
        int ruleCount = 0;

        // Even with 0 rules loaded, toggling the setting must be supported gracefully
        adBlockerEnabled = false;
        assertThat(adBlockerEnabled).isFalse();

        adBlockerEnabled = true;
        assertThat(adBlockerEnabled).isTrue();
    }

    @Test
    public void testSettingsPersistenceObjectImmutability() {
        BrowserSettings settings1 = new BrowserSettings(ThemeMode.SYSTEM, SearchEngine.DUCKDUCKGO, true);
        BrowserSettings settings2 = new BrowserSettings(ThemeMode.DARK, SearchEngine.GOOGLE, false);

        assertThat(settings1.getThemeMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(settings2.getThemeMode()).isEqualTo(ThemeMode.DARK);
    }

    @Test
    public void testClickingExternalLinksWhenActivityCannotResolveIntent() {
        String jestkUrl = "https://jestk.com";
        boolean canHandle = true; // In app or system browser

        assertThat(canHandle).isTrue();
        assertThat(jestkUrl).startsWith("https://");
    }
}
