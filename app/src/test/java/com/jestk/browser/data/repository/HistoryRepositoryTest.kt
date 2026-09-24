package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HistoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun recordVisit_recordsNewItem() = runTest(testDispatcher) {
        repository.recordVisit("https://kotlinlang.org", "Kotlin")

        val history = repository.getAllHistory().first()
        assertThat(history).hasSize(1)
        assertThat(history[0].url).isEqualTo("https://kotlinlang.org")
        assertThat(history[0].title).isEqualTo("Kotlin")
    }

    @Test
    fun recordVisit_ignoresBlankOrAboutUrls() = runTest(testDispatcher) {
        repository.recordVisit("", "")
        repository.recordVisit("about:blank", "Blank")

        val history = repository.getAllHistory().first()
        assertThat(history).isEmpty()
    }

    @Test
    fun searchHistory_filtersByQuery() = runTest(testDispatcher) {
        repository.recordVisit("https://developer.android.com", "Android Developers")
        repository.recordVisit("https://github.com", "GitHub")

        val results = repository.searchHistory("Android").first()
        assertThat(results).hasSize(1)
        assertThat(results[0].title).isEqualTo("Android Developers")
    }

    @Test
    fun clearHistory_deletesAllEntries() = runTest(testDispatcher) {
        repository.recordVisit("https://site1.com", "Site 1")
        repository.recordVisit("https://site2.com", "Site 2")

        repository.clearHistory()

        val history = repository.getAllHistory().first()
        assertThat(history).isEmpty()
    }

    @Test
    fun getPagedHistory_delegatesToDaoAndMapsToDomain() = runTest(testDispatcher) {
        for (i in 1..25) {
            repository.recordVisit("https://site$i.com", "Site $i")
        }

        val page1 = repository.getPagedHistory(limit = 10, offset = 0)
        assertThat(page1).hasSize(10)
        assertThat(page1[0].url).isEqualTo("https://site25.com")

        val page2 = repository.getPagedHistory(limit = 10, offset = 10)
        assertThat(page2).hasSize(10)
        assertThat(page2[0].url).isEqualTo("https://site15.com")

        val page3 = repository.getPagedHistory(limit = 10, offset = 20)
        assertThat(page3).hasSize(5)
        assertThat(page3[0].url).isEqualTo("https://site5.com")
    }

    @Test
    fun searchHistoryPaged_delegatesToDaoAndMapsToDomain() = runTest(testDispatcher) {
        for (i in 1..15) {
            repository.recordVisit("https://search-site$i.com", "Search Item $i")
        }

        val page = repository.searchHistoryPaged(query = "Search", limit = 10, offset = 0)
        assertThat(page).hasSize(10)
        assertThat(page[0].title).isEqualTo("Search Item 15")
    }
}
