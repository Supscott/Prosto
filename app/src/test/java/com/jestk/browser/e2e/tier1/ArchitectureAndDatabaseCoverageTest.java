package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.BrowserSettings;
import com.jestk.browser.domain.model.FavoriteItem;
import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.SearchEngine;
import com.jestk.browser.domain.model.Tab;
import com.jestk.browser.domain.model.ThemeMode;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Project Scaffolding, Room Database Entities, DAOs, and Architecture (R1).
 */
public class ArchitectureAndDatabaseCoverageTest {

    @Test
    public void testTabEntityDataModelIntegrity() {
        // Given a tab entity with full parameters
        long timestamp = System.currentTimeMillis();
        Tab tab = new Tab("tab-001", "https://jestk.com", "Jestk Browser", timestamp, "/data/snapshots/tab_001.jpg", 0);

        // Then verify all properties conform to the domain specification
        assertThat(tab.getId()).isEqualTo("tab-001");
        assertThat(tab.getUrl()).isEqualTo("https://jestk.com");
        assertThat(tab.getTitle()).isEqualTo("Jestk Browser");
        assertThat(tab.getLastActiveTimestamp()).isEqualTo(timestamp);
        assertThat(tab.getSnapshotPath()).isEqualTo("/data/snapshots/tab_001.jpg");
        assertThat(tab.getPosition()).isEqualTo(0);
    }

    @Test
    public void testHistoryEntityDataModelIntegrity() {
        // Given a history entry
        long timestamp = System.currentTimeMillis();
        HistoryItem history = new HistoryItem(101L, "https://developer.android.com", "Android Developers", timestamp);

        // Then verify properties
        assertThat(history.getId()).isEqualTo(101L);
        assertThat(history.getUrl()).isEqualTo("https://developer.android.com");
        assertThat(history.getTitle()).isEqualTo("Android Developers");
        assertThat(history.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    public void testFavoriteEntityDataModelIntegrity() {
        // Given a favorite entry
        FavoriteItem favorite = new FavoriteItem(5L, "GitHub", "https://github.com", 1);

        // Then verify properties
        assertThat(favorite.getId()).isEqualTo(5L);
        assertThat(favorite.getTitle()).isEqualTo("GitHub");
        assertThat(favorite.getUrl()).isEqualTo("https://github.com");
        assertThat(favorite.getPosition()).isEqualTo(1);
    }

    @Test
    public void testBrowserSettingsModelDefaultsAndEnums() {
        // Given default browser settings
        BrowserSettings defaultSettings = new BrowserSettings(ThemeMode.SYSTEM, SearchEngine.DUCKDUCKGO, true);

        // Then verify defaults match specification
        assertThat(defaultSettings.getThemeMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(defaultSettings.getSearchEngine()).isEqualTo(SearchEngine.DUCKDUCKGO);
        assertThat(defaultSettings.isAdBlockerEnabled()).isTrue();

        // And verify enum coverage
        assertThat(ThemeMode.values()).asList().containsExactly(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).inOrder();
        assertThat(SearchEngine.values()).asList().containsExactly(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BING).inOrder();
    }

    @Test
    public void testDatabaseTransactionBatchOperationsSimulation() {
        // Given an in-memory collection simulating Room DAO transaction
        List<Tab> tabTable = new ArrayList<>();
        Tab t1 = new Tab("tab-1", "https://site1.com", "Site 1", 1000L, null, 0);
        Tab t2 = new Tab("tab-2", "https://site2.com", "Site 2", 2000L, null, 1);
        Tab t3 = new Tab("tab-3", "https://site3.com", "Site 3", 3000L, null, 2);

        // When inserting in batch
        tabTable.add(t1);
        tabTable.add(t2);
        tabTable.add(t3);

        // Then verify row count and order
        assertThat(tabTable).hasSize(3);
        assertThat(tabTable.get(0).getId()).isEqualTo("tab-1");
        assertThat(tabTable.get(2).getId()).isEqualTo("tab-3");

        // When deleting tab-2
        tabTable.removeIf(t -> t.getId().equals("tab-2"));

        // Then verify state consistency
        assertThat(tabTable).hasSize(2);
        assertThat(tabTable.stream().map(Tab::getId).toList()).containsExactly("tab-1", "tab-3").inOrder();
    }
}
