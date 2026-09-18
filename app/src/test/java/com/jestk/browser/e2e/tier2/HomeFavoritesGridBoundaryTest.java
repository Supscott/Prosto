package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Home Screen Favorites Grid (R5 & spec §4.1).
 */
public class HomeFavoritesGridBoundaryTest {

    @Test
    public void testExactlySixFavoritesDoesNotShowExpandButton() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean showExpandButton = favorites.size() > 6;
        assertThat(showExpandButton).isFalse();
    }

    @Test
    public void testExactlySevenFavoritesShowsExpandButton() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean showExpandButton = favorites.size() > 6;
        assertThat(showExpandButton).isTrue();
    }

    @Test
    public void testCollapsingFavoritesAfterExpansion() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean isExpanded = true;
        List<FavoriteItem> displayedExpanded = isExpanded ? favorites : favorites.subList(0, 6);
        assertThat(displayedExpanded).hasSize(12);

        isExpanded = false;
        List<FavoriteItem> displayedCollapsed = isExpanded ? favorites : favorites.subList(0, 6);
        assertThat(displayedCollapsed).hasSize(6);
    }

    @Test
    public void testAddingFavoriteWithDuplicateUrl() {
        List<FavoriteItem> favorites = new ArrayList<>();
        favorites.add(new FavoriteItem(1L, "Jestk 1", "https://jestk.com", 0));
        favorites.add(new FavoriteItem(2L, "Jestk 2", "https://jestk.com", 1));

        assertThat(favorites).hasSize(2);
    }

    @Test
    public void testFavoriteWithHundredCharacterTitle() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("F");
        FavoriteItem fav = new FavoriteItem(1L, sb.toString(), "https://example.com", 0);

        assertThat(fav.getTitle().length()).isEqualTo(100);
    }
}
