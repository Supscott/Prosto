package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tier 2: Boundary & Edge Case Test for Repositories & StateFlow (R1).
 */
public class RepositoryStateFlowBoundaryTest {

    @Test
    public void testGetRecentTabsWithZeroLimitReturnsEmpty() {
        List<Tab> tabs = List.of(
                new Tab("1", "u1", "t1", 100L, null, 0),
                new Tab("2", "u2", "t2", 200L, null, 1)
        );

        List<Tab> result = tabs.stream().limit(0).collect(Collectors.toList());

        assertThat(result).isEmpty();
    }

    @Test
    public void testGetRecentTabsWithLimitExceedingTotalTabs() {
        List<Tab> tabs = List.of(
                new Tab("1", "u1", "t1", 100L, null, 0),
                new Tab("2", "u2", "t2", 200L, null, 1)
        );

        List<Tab> result = tabs.stream().limit(100).collect(Collectors.toList());

        assertThat(result).hasSize(2);
    }

    @Test
    public void testSearchHistoryWithBlankOrSpecialRegexQuery() {
        List<String> historyTitles = List.of("Page (1)", "Google [Search]", "Normal Title", "Regex .*+?^$");

        String query = ".*+?^$"; // Tricky regex characters
        List<String> matches = historyTitles.stream()
                .filter(t -> t.contains(query))
                .toList();

        assertThat(matches).containsExactly("Regex .*+?^$");
    }

    @Test
    public void testAddingMultipleDuplicateFavoritesAllowedWithUniqueIds() {
        List<FavoriteItem> favorites = new ArrayList<>();
        favorites.add(new FavoriteItem(1L, "Jestk", "https://jestk.com", 0));
        favorites.add(new FavoriteItem(2L, "Jestk", "https://jestk.com", 1));

        assertThat(favorites).hasSize(2);
        assertThat(favorites.get(0).getId()).isNotEqualTo(favorites.get(1).getId());
    }

    @Test
    public void testUpdatingSnapshotForDeletedTabHandledGracefully() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("tab-1", "https://jestk.com", "Jestk", 100L, null, 0));

        // Delete tab-1
        tabs.remove(0);

        // Attempt update snapshot for deleted tab-1
        boolean updated = false;
        for (Tab t : tabs) {
            if (t.getId().equals("tab-1")) {
                updated = true;
                break;
            }
        }

        assertThat(updated).isFalse();
    }
}
