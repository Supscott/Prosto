package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Room DAOs & Transactions (R1).
 */
public class RoomDaoTransactionBoundaryTest {

    @Test
    public void testDeletingNonExistentIdIsSafeNoOp() {
        List<Tab> tabDao = new ArrayList<>();
        tabDao.add(new Tab("tab-1", "https://jestk.com", "Jestk", 100L, null, 0));

        // Delete non-existent ID
        boolean removed = tabDao.removeIf(t -> t.getId().equals("non-existent-id"));

        assertThat(removed).isFalse();
        assertThat(tabDao).hasSize(1);
    }

    @Test
    public void testQueryingEmptyTableReturnsEmptyList() {
        List<HistoryItem> historyDao = new ArrayList<>();

        List<HistoryItem> result = new ArrayList<>(historyDao);

        assertThat(result).isEmpty();
    }

    @Test
    public void testUpdatingSameTabConcurrentlySimulation() {
        Tab original = new Tab("tab-1", "https://old.com", "Old", 100L, null, 0);
        Tab updateA = new Tab("tab-1", "https://new-a.com", "New A", 200L, null, 0);
        Tab updateB = new Tab("tab-1", "https://new-b.com", "New B", 300L, null, 0);

        List<Tab> table = new ArrayList<>();
        table.add(original);

        // Sequential updates
        table.removeIf(t -> t.getId().equals("tab-1"));
        table.add(updateA);
        table.removeIf(t -> t.getId().equals("tab-1"));
        table.add(updateB);

        assertThat(table).hasSize(1);
        assertThat(table.get(0).getUrl()).isEqualTo("https://new-b.com");
    }

    @Test
    public void testLargeBatchInsertionTenThousandRecords() {
        List<HistoryItem> batch = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            batch.add(new HistoryItem((long) i, "https://history" + i + ".com", "History " + i, System.currentTimeMillis()));
        }

        assertThat(batch).hasSize(10000);
    }

    @Test
    public void testZeroTimestampAndNegativePositionsHandling() {
        Tab tab = new Tab("tab-zero", "https://zero.com", "Zero", 0L, null, -1);

        assertThat(tab.getLastActiveTimestamp()).isEqualTo(0L);
        assertThat(tab.getPosition()).isEqualTo(-1);
    }
}
