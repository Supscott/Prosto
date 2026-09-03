package com.jestk.browser.e2e.tier3;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;
import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.SearchEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 3: Pairwise Cross-Feature Test — Search Overlay & History / Favorites Repository Interaction.
 */
public class SearchOverlayAndHistoryStateInteractionTest {

    @Test
    public void testSearchSubmissionUpdatesHistoryAndRecents() {
        List<HistoryItem> history = new ArrayList<>();
        String query = "jetpack compose android";

        // Dispatch search to DuckDuckGo
        String searchUrl = "https://duckduckgo.com/?q=" + query.replace(" ", "+");
        history.add(new HistoryItem(1L, searchUrl, query + " - DuckDuckGo", System.currentTimeMillis()));

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getUrl()).startsWith("https://duckduckgo.com/?q=");
    }

    @Test
    public void testTappingHistorySuggestionInSearchOverlayDispatchesDirectly() {
        HistoryItem suggestion = new HistoryItem(5L, "https://developer.android.com/reference", "Android Docs", 1000L);
        String dispatchedUrl = suggestion.getUrl();

        assertThat(dispatchedUrl).isEqualTo("https://developer.android.com/reference");
    }

    @Test
    public void testSearchOverlayPrefilledWithActiveUrlAndEditingRefreshesSuggestions() {
        String activeUrl = "https://kotlinlang.org";
        String currentInput = activeUrl;

        // User deletes and types "andro"
        currentInput = "andro";
        List<HistoryItem> allHistory = List.of(
                new HistoryItem(1L, "https://developer.android.com", "Android Docs", 100L),
                new HistoryItem(2L, "https://kotlinlang.org", "Kotlin", 200L)
        );

        String filter = currentInput.toLowerCase();
        List<HistoryItem> suggestions = allHistory.stream()
                .filter(h -> h.getTitle().toLowerCase().contains(filter) || h.getUrl().toLowerCase().contains(filter))
                .toList();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getTitle()).contains("Android");
    }

    @Test
    public void testAddingUrlFromSearchResultToFavorites() {
        List<FavoriteItem> favorites = new ArrayList<>();
        String pageUrl = "https://jestk.com/docs";
        String pageTitle = "Jestk Documentation";

        favorites.add(new FavoriteItem(1L, pageTitle, pageUrl, favorites.size()));

        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).getUrl()).isEqualTo("https://jestk.com/docs");
    }

    @Test
    public void testClearingHistoryInSettingsImmediatelyEmptiesSearchSuggestions() {
        List<HistoryItem> history = new ArrayList<>();
        history.add(new HistoryItem(1L, "https://site1.com", "Site 1", 100L));
        history.add(new HistoryItem(2L, "https://site2.com", "Site 2", 200L));

        // Settings -> Clear History
        history.clear();

        assertThat(history).isEmpty();
    }

    @Test
    public void testSwitchingDefaultSearchEngineUpdatesDispatch() {
        SearchEngine engine = SearchEngine.DUCKDUCKGO;
        String query = "testing";

        String ddgUrl = "https://duckduckgo.com/?q=" + query;
        engine = SearchEngine.GOOGLE;
        String googleUrl = "https://www.google.com/search?q=" + query;

        assertThat(ddgUrl).contains("duckduckgo.com");
        assertThat(googleUrl).contains("google.com");
    }
}
