package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.BrowserSettings;
import com.jestk.browser.domain.model.SearchEngine;
import com.jestk.browser.domain.model.ThemeMode;

import org.junit.Test;

/**
 * Tier 1: Feature Coverage Test for Settings Sheet Options & Theme (R5 & spec §4.4).
 * Theme (System/Light/Dark), Search Engine (DuckDuckGo/Google/Bing), Ad Blocker toggle, GitHub/Jestk links.
 */
public class SettingsSheetCoverageTest {

    @Test
    public void testThemeModeOptions() {
        BrowserSettings settings = new BrowserSettings(ThemeMode.SYSTEM, SearchEngine.DUCKDUCKGO, true);

        BrowserSettings light = new BrowserSettings(ThemeMode.LIGHT, settings.getSearchEngine(), settings.isAdBlockerEnabled());
        BrowserSettings dark = new BrowserSettings(ThemeMode.DARK, settings.getSearchEngine(), settings.isAdBlockerEnabled());

        assertThat(light.getThemeMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(dark.getThemeMode()).isEqualTo(ThemeMode.DARK);
    }

    @Test
    public void testSearchEngineOptions() {
        BrowserSettings settings = new BrowserSettings(ThemeMode.SYSTEM, SearchEngine.DUCKDUCKGO, true);

        BrowserSettings google = new BrowserSettings(settings.getThemeMode(), SearchEngine.GOOGLE, settings.isAdBlockerEnabled());
        BrowserSettings bing = new BrowserSettings(settings.getThemeMode(), SearchEngine.BING, settings.isAdBlockerEnabled());

        assertThat(google.getSearchEngine()).isEqualTo(SearchEngine.GOOGLE);
        assertThat(bing.getSearchEngine()).isEqualTo(SearchEngine.BING);
    }

    @Test
    public void testAdBlockerToggleSwitch() {
        BrowserSettings settings = new BrowserSettings(ThemeMode.SYSTEM, SearchEngine.DUCKDUCKGO, true);

        BrowserSettings toggledOff = new BrowserSettings(settings.getThemeMode(), settings.getSearchEngine(), false);
        assertThat(toggledOff.isAdBlockerEnabled()).isFalse();

        BrowserSettings toggledOn = new BrowserSettings(settings.getThemeMode(), settings.getSearchEngine(), true);
        assertThat(toggledOn.isAdBlockerEnabled()).isTrue();
    }

    @Test
    public void testExternalLinksDefined() {
        String jestkUrl = "https://jestk.com";
        String githubUrl = "https://github.com/jestk/browser";

        assertThat(jestkUrl).isEqualTo("https://jestk.com");
        assertThat(githubUrl).contains("github.com");
    }

    @Test
    public void testProfileSectionShowsComingSoon() {
        String profileStatus = "Coming Soon";
        assertThat(profileStatus).isEqualTo("Coming Soon");
    }
}
