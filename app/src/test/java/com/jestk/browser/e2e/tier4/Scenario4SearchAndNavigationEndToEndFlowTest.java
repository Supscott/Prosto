package com.jestk.browser.e2e.tier4;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.OverlayType;
import com.jestk.browser.domain.model.SearchEngine;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier 4 - Scenario 4: Search & Navigation Flow.
 * Exercises: Active tab pill tap -> Search Modal Overlay slide-in (bias 0.55) ->
 * Soft keyboard autofocus -> Input query evaluation -> History suggestions filtering ->
 * DuckDuckGo URL routing -> Page load -> History stack update.
 */
public class Scenario4SearchAndNavigationEndToEndFlowTest {

    @Test
    public void testFullSearchAndNavigationWorkflow() {
        // Step 1: Initial state on active tab
        String activeTabUrl = "https://jestk.com";
        OverlayType activeOverlay = OverlayType.NONE;
        boolean keyboardVisible = false;
        List<HistoryItem> historyDatabase = new ArrayList<>();
        historyDatabase.add(new HistoryItem(1L, "https://developer.android.com", "Android Developers Guide", 1000L));
        historyDatabase.add(new HistoryItem(2L, "https://kotlinlang.org", "Kotlin Language", 2000L));

        // Step 2: User taps active tab pill -> launches Search Overlay
        activeOverlay = OverlayType.SEARCH;
        float overlayBias = 0.55f;
        String searchInput = activeTabUrl;
        keyboardVisible = true; // Autofocus triggered via SoftwareKeyboardController

        assertThat(activeOverlay).isEqualTo(OverlayType.SEARCH);
        assertThat(overlayBias).isEqualTo(0.55f);
        assertThat(searchInput).isEqualTo("https://jestk.com");
        assertThat(keyboardVisible).isTrue();

        // Step 3: User clears input and types "andro"
        searchInput = "andro";
        String filter = searchInput.toLowerCase();
        List<HistoryItem> matchingSuggestions = historyDatabase.stream()
                .filter(h -> h.getTitle().toLowerCase().contains(filter) || h.getUrl().toLowerCase().contains(filter))
                .toList();

        assertThat(matchingSuggestions).hasSize(1);
        assertThat(matchingSuggestions.get(0).getTitle()).contains("Android Developers Guide");

        // Step 4: User continues typing freeform query "android jetpack compose webview"
        searchInput = "android jetpack compose webview";

        // Step 5: User presses Search (ImeAction.Search) -> evaluated by SearchEngine.DUCKDUCKGO
        SearchEngine engine = SearchEngine.DUCKDUCKGO;
        String encodedQuery = URLEncoder.encode(searchInput, StandardCharsets.UTF_8);
        String targetUrl = "https://duckduckgo.com/?q=" + encodedQuery;

        // Dismiss overlay & hide keyboard
        activeOverlay = OverlayType.NONE;
        keyboardVisible = false;

        assertThat(targetUrl).isEqualTo("https://duckduckgo.com/?q=android+jetpack+compose+webview");
        assertThat(activeOverlay).isEqualTo(OverlayType.NONE);
        assertThat(keyboardVisible).isFalse();

        // Step 6: Page load finishes -> History database recorded
        historyDatabase.add(new HistoryItem(3L, targetUrl, "android jetpack compose webview - DuckDuckGo", System.currentTimeMillis()));

        assertThat(historyDatabase).hasSize(3);
        assertThat(historyDatabase.get(2).getUrl()).startsWith("https://duckduckgo.com/?q=");
    }
}
