package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class HistoryDaoStressTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        historyDao = db.historyDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun aggregationWindow_boundaryPrecision_sub300sAggregates_exact300sInsertsNew() = runTest {
        val url = "https://jestk.com/article"
        val t0 = 1_000_000L

        // Initial visit inserted directly with t0
        val id1 = historyDao.insertHistory(
            HistoryEntity(
                url = url,
                title = "Initial Title",
                timestamp = t0,
                visitCount = 1
            )
        )

        // Case A: Visit at t0 + 299_999 ms (under 300s window) -> should update existing
        val nowA = t0 + 299_999L
        val existingA = historyDao.getRecentByUrl(url)
        assertThat(existingA).isNotNull()
        assertThat(nowA - existingA!!.timestamp).isLessThan(300_000L)
        historyDao.updateVisit(existingA.id, "Title at 299.999s", nowA)

        val afterA = historyDao.getRecentByUrl(url)
        assertThat(afterA?.id).isEqualTo(id1)
        assertThat(afterA?.visitCount).isEqualTo(2)
        assertThat(afterA?.title).isEqualTo("Title at 299.999s")
        assertThat(afterA?.timestamp).isEqualTo(nowA)
        assertThat(historyDao.getHistoryCount()).isEqualTo(1)

        // Case B: Next visit at nowA + 300_000 ms (exactly 300s / 5min window) -> should insert NEW record
        val nowB = nowA + 300_000L
        val existingB = historyDao.getRecentByUrl(url)
        assertThat(existingB).isNotNull()
        assertThat(nowB - existingB!!.timestamp).isAtLeast(300_000L)
        // Inserting new record per 5-minute rule
        val id2 = historyDao.insertHistory(
            HistoryEntity(
                url = url,
                title = "Title at +300s",
                timestamp = nowB,
                visitCount = 1
            )
        )

        assertThat(id2).isNotEqualTo(id1)
        assertThat(historyDao.getHistoryCount()).isEqualTo(2)

        // The most recent record for this URL is now id2
        val latest = historyDao.getRecentByUrl(url)
        assertThat(latest?.id).isEqualTo(id2)
        assertThat(latest?.title).isEqualTo("Title at +300s")
    }

    @Test
    fun recordVisit_titleHandling_retainsPreviousWhenBlank_defaultsToUrlWhenInitialBlank() = runTest {
        val url = "https://jestk.com/docs"

        // Initial visit with empty title -> uses url
        historyDao.recordVisit(url, "")
        val firstRecord = historyDao.getRecentByUrl(url)
        assertThat(firstRecord).isNotNull()
        assertThat(firstRecord?.title).isEqualTo(url)

        // Subsequent visit within 5 minutes with blank title -> retains existing title
        historyDao.recordVisit(url, "")
        val secondRecord = historyDao.getRecentByUrl(url)
        assertThat(secondRecord?.title).isEqualTo(url)
        assertThat(secondRecord?.visitCount).isEqualTo(2)

        // Subsequent visit within 5 minutes with non-blank title -> updates title
        historyDao.recordVisit(url, "Official Documentation")
        val thirdRecord = historyDao.getRecentByUrl(url)
        assertThat(thirdRecord?.title).isEqualTo("Official Documentation")
        assertThat(thirdRecord?.visitCount).isEqualTo(3)

        // Subsequent visit with blank title -> retains "Official Documentation"
        historyDao.recordVisit(url, "   ")
        val fourthRecord = historyDao.getRecentByUrl(url)
        assertThat(fourthRecord?.title).isEqualTo("Official Documentation")
        assertThat(fourthRecord?.visitCount).isEqualTo(4)
    }

    @Test
    fun recordVisit_ignoredSchemesAndBlanks_areNotInserted() = runTest {
        historyDao.recordVisit("", "Empty URL")
        historyDao.recordVisit("   ", "Whitespace URL")
        historyDao.recordVisit("about:blank", "Blank Page")
        historyDao.recordVisit("about:config", "Config")
        historyDao.recordVisit("about:flags", "Flags")

        assertThat(historyDao.getHistoryCount()).isEqualTo(0)
        assertThat(historyDao.getAllHistoryFlow().first()).isEmpty()
    }

    @Test
    fun concurrentRecordVisit_sameUrlAndDistinctUrls_runsWithoutCrashing() = runTest {
        val sameUrl = "https://concurrent.jestk.com"

        withContext(Dispatchers.IO) {
            val sameUrlJobs = (0 until 30).map { i ->
                async {
                    historyDao.recordVisit(sameUrl, "Concurrent $i")
                }
            }
            sameUrlJobs.awaitAll()
        }

        // Must have at least 1 record and no corrupted data
        val historySame = historyDao.getRecentByUrl(sameUrl)
        assertThat(historySame).isNotNull()
        assertThat(historySame?.url).isEqualTo(sameUrl)

        // Test 50 distinct URLs concurrently
        withContext(Dispatchers.IO) {
            val distinctJobs = (0 until 50).map { i ->
                async {
                    historyDao.recordVisit("https://site-$i.org", "Site $i")
                }
            }
            distinctJobs.awaitAll()
        }

        val allHistory = historyDao.getAllHistoryFlow().first()
        assertThat(allHistory.size).isAtLeast(50)
    }

    @Test
    fun searchHistory_adversarialQueries_wildcardsQuotesAndEmojis() = runTest {
        historyDao.insertHistory(HistoryEntity(url = "https://jestk.com/100%25_discount", title = "100% Special & Deal!", timestamp = 1000L))
        historyDao.insertHistory(HistoryEntity(url = "https://jestk.com/docs/o'reilly", title = "O'Reilly Book", timestamp = 2000L))
        historyDao.insertHistory(HistoryEntity(url = "https://jestk.com/search?q=\uD83D\uDD25", title = "Emoji \uD83D\uDD25 Fire", timestamp = 3000L))
        historyDao.insertHistory(HistoryEntity(url = "https://jestk.com/c++_guide", title = "C++ Guide", timestamp = 4000L))

        // Search with % wildcard character
        val searchPercent = historyDao.searchHistory("%")
        assertThat(searchPercent).isNotEmpty()

        // Search with single quote
        val searchQuote = historyDao.searchHistory("o'reilly")
        assertThat(searchQuote.map { it.url }).contains("https://jestk.com/docs/o'reilly")

        // Search with emoji
        val searchEmoji = historyDao.searchHistory("\uD83D\uDD25")
        assertThat(searchEmoji.map { it.url }).contains("https://jestk.com/search?q=\uD83D\uDD25")

        // Search with ++
        val searchCpp = historyDao.searchHistory("C++")
        assertThat(searchCpp.map { it.url }).contains("https://jestk.com/c++_guide")
    }

    @Test
    fun deleteHistoryBefore_prunesAccuratelyAtTimestampBoundary() = runTest {
        val cutoff = 5000L

        historyDao.insertHistory(HistoryEntity(url = "https://old1.com", title = "Old 1", timestamp = 1000L))
        historyDao.insertHistory(HistoryEntity(url = "https://old2.com", title = "Old 2", timestamp = 4999L))
        historyDao.insertHistory(HistoryEntity(url = "https://exact.com", title = "Exact Cutoff", timestamp = 5000L))
        historyDao.insertHistory(HistoryEntity(url = "https://new1.com", title = "New 1", timestamp = 5001L))
        historyDao.insertHistory(HistoryEntity(url = "https://new2.com", title = "New 2", timestamp = 9000L))

        assertThat(historyDao.getHistoryCount()).isEqualTo(5)

        historyDao.deleteHistoryBefore(cutoff)

        val remaining = historyDao.getAllHistoryFlow().first()
        // Items with timestamp < 5000L should be deleted. Items with timestamp >= 5000L should remain.
        assertThat(remaining.map { it.url }).containsExactly(
            "https://new2.com",
            "https://new1.com",
            "https://exact.com"
        ).inOrder()
    }

    @Test
    fun batchInsertAndLimitOrdering_1000Items_retrievesAccurately() = runTest {
        val count = 1000
        for (i in 0 until count) {
            historyDao.insertHistory(
                HistoryEntity(
                    url = "https://site-$i.com",
                    title = "Site $i",
                    timestamp = i.toLong()
                )
            )
        }

        assertThat(historyDao.getHistoryCount()).isEqualTo(count)

        val recent20 = historyDao.getRecentHistory(limit = 20)
        assertThat(recent20).hasSize(20)
        // Most recent timestamps (999 down to 980)
        val expectedUrls = (count - 1 downTo count - 20).map { "https://site-$it.com" }
        assertThat(recent20.map { it.url }).containsExactlyElementsIn(expectedUrls).inOrder()

        historyDao.clearAllHistory()
        assertThat(historyDao.getHistoryCount()).isEqualTo(0)
    }
}
