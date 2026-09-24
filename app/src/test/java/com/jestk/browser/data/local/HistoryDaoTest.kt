package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
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
    fun recordVisit_initialVisit_createsNewHistoryRecord() =
        runTest {
            historyDao.recordVisit("https://kotlinlang.org", "Kotlin Programming")

            val history = historyDao.getAllHistoryFlow().first()
            assertThat(history).hasSize(1)
            assertThat(history[0].url).isEqualTo("https://kotlinlang.org")
            assertThat(history[0].title).isEqualTo("Kotlin Programming")
            assertThat(history[0].visitCount).isEqualTo(1)
        }

    @Test
    fun recordVisit_subsequentVisitWithin5Minutes_aggregatesVisitCount() =
        runTest {
            historyDao.recordVisit("https://kotlinlang.org", "Kotlin")
            historyDao.recordVisit("https://kotlinlang.org", "Kotlin Programming Language")

            val history = historyDao.getAllHistoryFlow().first()
            assertThat(history).hasSize(1)
            assertThat(history[0].url).isEqualTo("https://kotlinlang.org")
            assertThat(history[0].title).isEqualTo("Kotlin Programming Language")
            assertThat(history[0].visitCount).isEqualTo(2)
        }

    @Test
    fun historyQueries_hideExistingDuplicateUrls() =
        runTest {
            historyDao.insertHistory(
                HistoryEntity(url = "https://example.com", title = "Old", timestamp = 1000L),
            )
            historyDao.insertHistory(
                HistoryEntity(url = "https://example.com", title = "Latest", timestamp = 2000L),
            )

            val history = historyDao.getAllHistoryFlow().first()

            assertThat(history).hasSize(1)
            assertThat(history[0].title).isEqualTo("Latest")
        }

    @Test
    fun searchHistoryFlow_matchesQueryInTitleOrUrl() =
        runTest {
            historyDao.insertHistory(
                HistoryEntity(url = "https://github.com/jetpack-compose", title = "Compose Repo", timestamp = 1000L),
            )
            historyDao.insertHistory(
                HistoryEntity(url = "https://developer.android.com", title = "Android Docs", timestamp = 2000L),
            )
            historyDao.insertHistory(
                HistoryEntity(url = "https://kotlinlang.org/docs", title = "Coroutines Guide", timestamp = 3000L),
            )

            val resultsByTitle = historyDao.searchHistoryFlow("Guide").first()
            assertThat(resultsByTitle.map { it.url }).containsExactly("https://kotlinlang.org/docs")

            val resultsByUrl = historyDao.searchHistoryFlow("github").first()
            assertThat(resultsByUrl.map { it.url }).containsExactly("https://github.com/jetpack-compose")
        }

    @Test
    fun clearAllHistory_deletesAllRecords() =
        runTest {
            historyDao.recordVisit("https://site1.com", "Site 1")
            historyDao.recordVisit("https://site2.com", "Site 2")

            historyDao.clearAllHistory()

            val history = historyDao.getAllHistoryFlow().first()
            assertThat(history).isEmpty()
        }

    @Test
    fun getPagedHistory_returnsCorrectLimitAndOffset() =
        runTest {
            for (i in 1..25) {
                historyDao.insertHistory(
                    HistoryEntity(
                        url = "https://site$i.com",
                        title = "Site $i",
                        timestamp = 1000L * i,
                    ),
                )
            }

            val page1 = historyDao.getPagedHistory(limit = 10, offset = 0)
            assertThat(page1).hasSize(10)
            // Most recent timestamp is site 25
            assertThat(page1[0].url).isEqualTo("https://site25.com")
            assertThat(page1[9].url).isEqualTo("https://site16.com")

            val page2 = historyDao.getPagedHistory(limit = 10, offset = 10)
            assertThat(page2).hasSize(10)
            assertThat(page2[0].url).isEqualTo("https://site15.com")
            assertThat(page2[9].url).isEqualTo("https://site6.com")

            val page3 = historyDao.getPagedHistory(limit = 10, offset = 20)
            assertThat(page3).hasSize(5)
            assertThat(page3[0].url).isEqualTo("https://site5.com")
            assertThat(page3[4].url).isEqualTo("https://site1.com")
        }

    @Test
    fun searchHistoryPaged_returnsMatchingItemsWithPagination() =
        runTest {
            for (i in 1..15) {
                historyDao.insertHistory(
                    HistoryEntity(
                        url = "https://example.com/topic$i",
                        title = "Article topic $i",
                        timestamp = 1000L * i,
                    ),
                )
            }

            val pagedMatches = historyDao.searchHistoryPaged(query = "topic", limit = 10, offset = 0)
            assertThat(pagedMatches).hasSize(10)
            assertThat(pagedMatches[0].url).isEqualTo("https://example.com/topic15")

            val pagedMatches2 = historyDao.searchHistoryPaged(query = "topic", limit = 10, offset = 10)
            assertThat(pagedMatches2).hasSize(5)
            assertThat(pagedMatches2[0].url).isEqualTo("https://example.com/topic5")
        }
}
