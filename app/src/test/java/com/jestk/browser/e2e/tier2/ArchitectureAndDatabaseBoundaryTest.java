package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.FavoriteItem;
import com.jestk.browser.domain.model.HistoryItem;
import com.jestk.browser.domain.model.Tab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 2: Boundary & Edge Case Test for Architecture & Room Entities (R1).
 * Tests large volume, special characters, unicode, emoji, max integer IDs, empty strings.
 */
public class ArchitectureAndDatabaseBoundaryTest {

    @Test
    public void testLargeScaleTabEntityVolume() {
        List<Tab> largeTabList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeTabList.add(new Tab("tab-" + i, "https://domain" + i + ".com", "Title " + i, System.currentTimeMillis(), null, i));
        }

        assertThat(largeTabList).hasSize(1000);
        assertThat(largeTabList.get(999).getPosition()).isEqualTo(999);
    }

    @Test
    public void testSqlSpecialCharactersAndQuotesInTitles() {
        String trickyTitle = "O'Reilly's \"Special\" -- ' OR '1'='1; DROP TABLE tabs; --";
        Tab tab = new Tab("sql-test", "https://example.com", trickyTitle, 100L, null, 0);

        assertThat(tab.getTitle()).isEqualTo(trickyTitle);
    }

    @Test
    public void testUnicodeAndEmojiHandlingInEntities() {
        String emojiTitle = "🔥 Jestk 🚀 Browser 🌐 日本語 • Русский • 😃";
        String idnUrl = "https://xn--e1afmkfd.xn--p1ai"; // кириллица.рф in Punycode
        HistoryItem history = new HistoryItem(1L, idnUrl, emojiTitle, System.currentTimeMillis());

        assertThat(history.getTitle()).isEqualTo(emojiTitle);
        assertThat(history.getUrl()).isEqualTo(idnUrl);
    }

    @Test
    public void testEmptyStringsAndNullSnapshotHandling() {
        Tab emptyTab = new Tab("", "", "", 0L, null, 0);

        assertThat(emptyTab.getId()).isEmpty();
        assertThat(emptyTab.getUrl()).isEmpty();
        assertThat(emptyTab.getTitle()).isEmpty();
        assertThat(emptyTab.getSnapshotPath()).isNull();
    }

    @Test
    public void testMaxLongIdsInHistoryAndFavorite() {
        long maxId = Long.MAX_VALUE;
        FavoriteItem fav = new FavoriteItem(maxId, "Max Favorite", "https://max.com", Integer.MAX_VALUE);

        assertThat(fav.getId()).isEqualTo(Long.MAX_VALUE);
        assertThat(fav.getPosition()).isEqualTo(Integer.MAX_VALUE);
    }
}
