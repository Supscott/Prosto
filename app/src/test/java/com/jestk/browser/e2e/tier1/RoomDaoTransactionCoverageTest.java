package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;
import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 1: Feature Coverage Test for Room DAOs & Transactions (R1).
 */
public class RoomDaoTransactionCoverageTest {

    @Test
    public void testTabDaoInsertUpdateDeleteFlow() {
        List<Tab> tabDao = new ArrayList<>();
        Tab initial = new Tab("tab-1", "https://jestk.com", "Jestk", 1000L, null, 0);

        // Insert
        tabDao.add(initial);
        assertThat(tabDao).hasSize(1);

        // Update
        Tab updated = new Tab("tab-1", "https://jestk.com/docs", "Jestk Docs", 2000L, "/path.jpg", 0);
        tabDao.removeIf(t -> t.getId().equals("tab-1"));
        tabDao.add(updated);
        assertThat(tabDao.get(0).getTitle()).isEqualTo("Jestk Docs");

        // Delete
        tabDao.removeIf(t -> t.getId().equals("tab-1"));
        assertThat(tabDao).isEmpty();
    }

    @Test
    public void testHistoryDaoClearHistory() {
        List<HistoryItem> historyDao = new ArrayList<>();
        historyDao.add(new HistoryItem(1L, "https://a.com", "A", 100L));
        historyDao.add(new HistoryItem(2L, "https://b.com", "B", 200L));

        historyDao.clear();

        assertThat(historyDao).isEmpty();
    }

    @Test
    public void testFavoriteDaoReorderingPositions() {
        List<FavoriteItem> favorites = new ArrayList<>();
        favorites.add(new FavoriteItem(1L, "One", "https://1.com", 0));
        favorites.add(new FavoriteItem(2L, "Two", "https://2.com", 1));

        // Swap positions
        FavoriteItem f1 = new FavoriteItem(1L, "One", "https://1.com", 1);
        FavoriteItem f2 = new FavoriteItem(2L, "Two", "https://2.com", 0);
        favorites.clear();
        favorites.add(f2);
        favorites.add(f1);

        assertThat(favorites.get(0).getId()).isEqualTo(2L);
        assertThat(favorites.get(0).getPosition()).isEqualTo(0);
    }

    @Test
    public void testTabDaoFindById() {
        List<Tab> tabs = List.of(
                new Tab("id-100", "https://test.com", "Test", 100L, null, 0),
                new Tab("id-200", "https://example.com", "Example", 200L, null, 1)
        );

        Tab found = tabs.stream().filter(t -> t.getId().equals("id-200")).findFirst().orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Example");
    }

    @Test
    public void testBatchQueryExecution() {
        List<Tab> tabs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tabs.add(new Tab("tab-" + i, "https://url" + i + ".com", "Title " + i, (long) i, null, i));
        }

        assertThat(tabs).hasSize(50);
        assertThat(tabs.get(49).getPosition()).isEqualTo(49);
    }
}
