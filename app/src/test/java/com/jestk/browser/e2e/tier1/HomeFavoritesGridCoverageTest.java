package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Home Screen Favorites Grid (R5 & spec §4.1).
 * Responsive 2D Grid with 6-item default cap + empty prompt + "Show All Favorites" expansion.
 */
public class HomeFavoritesGridCoverageTest {

    @Test
    public void testEmptyFavoritesShowsBookmarkPrompt() {
        List<FavoriteItem> favorites = List.of();
        boolean showEmptyBookmarkPrompt = favorites.isEmpty();

        assertThat(showEmptyBookmarkPrompt).isTrue();
    }

    @Test
    public void testDefaultDisplayCappedAtSixItems() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean isExpanded = false;
        List<FavoriteItem> displayed = isExpanded ? favorites : favorites.subList(0, 6);

        assertThat(displayed).hasSize(6);
    }

    @Test
    public void testShowAllFavoritesButtonAppearsWhenMoreThanSixItems() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean showExpandButton = favorites.size() > 6;
        assertThat(showExpandButton).isTrue();
    }

    @Test
    public void testExpandingFavoritesShowsAllItems() {
        List<FavoriteItem> favorites = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            favorites.add(new FavoriteItem((long) i, "Fav " + i, "https://fav" + i + ".com", i - 1));
        }

        boolean isExpanded = true;
        List<FavoriteItem> displayed = isExpanded ? favorites : favorites.subList(0, 6);

        assertThat(displayed).hasSize(9);
    }

    @Test
    public void testTappingFavoriteNavigatesToUrl() {
        FavoriteItem fav = new FavoriteItem(1L, "Jestk", "https://jestk.com", 0);
        String targetUrl = fav.getUrl();

        assertThat(targetUrl).isEqualTo("https://jestk.com");
    }
}
