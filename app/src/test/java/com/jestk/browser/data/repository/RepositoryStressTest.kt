package com.jestk.browser.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.local.entity.HistoryEntity
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositoryStressTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var settingsRepo: SettingsRepositoryImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun tabRepository_batchOperationsAndReordering_maintainsPositionIntegrity() = runTest(testDispatcher) {
        val createdTabs = mutableListOf<Tab>()
        for (i in 0 until 50) {
            val tab = tabRepo.createTab("https://site$i.org", "Title $i")
            createdTabs.add(tab)
        }

        val allTabs = tabRepo.getAllTabs().first()
        assertThat(allTabs).hasSize(50)
        for (i in 0 until 50) {
            assertThat(allTabs[i].position).isEqualTo(i)
        }

        // Reverse reorder
        val reversed = allTabs.reversed()
        tabRepo.reorderTabs(reversed)

        val reorderedTabs = tabRepo.getAllTabs().first()
        assertThat(reorderedTabs.first().id).isEqualTo(createdTabs.last().id)
        assertThat(reorderedTabs.last().id).isEqualTo(createdTabs.first().id)

        // Snapshot updating
        tabRepo.updateSnapshot(createdTabs[0].id, "/snapshots/tab0.png")
        val tab0 = tabRepo.getTabById(createdTabs[0].id)
        assertThat(tab0?.snapshotPath).isEqualTo("/snapshots/tab0.png")
    }

    @Test
    fun historyDao_fiveMinuteAggregationWindow_aggregatesOrSplitsCorrectly() = runTest(testDispatcher) {
        val url = "https://jestk.com/docs"
        val title = "Jestk Docs"

        // Initial visit at current time
        val t0 = 1000000L
        db.historyDao().insertHistory(
            HistoryEntity(
                url = url,
                title = title,
                timestamp = t0,
                visitCount = 1
            )
        )

        val historyAfterFirst = historyRepo.getRecentHistory(10).first()
        assertThat(historyAfterFirst).hasSize(1)
        assertThat(historyAfterFirst[0].visitCount).isEqualTo(1)
        assertThat(historyAfterFirst[0].timestamp).isEqualTo(t0)

        // Second visit within 5 min (2 minutes later = +120,000 ms)
        val t1 = t0 + 120000L
        val recent = db.historyDao().getRecentByUrl(url)
        assertThat(recent).isNotNull()
        if (recent != null && (t1 - recent.timestamp) < 300_000L) {
            db.historyDao().updateVisit(recent.id, "Jestk Docs Updated", t1)
        }

        val historyAfterSecond = historyRepo.getRecentHistory(10).first()
        assertThat(historyAfterSecond).hasSize(1)
        assertThat(historyAfterSecond[0].visitCount).isEqualTo(2)
        assertThat(historyAfterSecond[0].title).isEqualTo("Jestk Docs Updated")
        assertThat(historyAfterSecond[0].timestamp).isEqualTo(t1)

        // Third visit after 5 min (> 300,000 ms later: +400,000 ms)
        val t2 = t1 + 400000L
        val recent2 = db.historyDao().getRecentByUrl(url)
        assertThat(recent2).isNotNull()
        if (recent2 != null && (t2 - recent2.timestamp) < 300_000L) {
            db.historyDao().updateVisit(recent2.id, "Jestk Docs New Visit", t2)
        } else {
            db.historyDao().insertHistory(
                HistoryEntity(
                    url = url,
                    title = "Jestk Docs New Visit",
                    timestamp = t2,
                    visitCount = 1
                )
            )
        }

        val historyAfterThird = historyRepo.getRecentHistory(10).first()
        assertThat(historyAfterThird).hasSize(2)
        assertThat(historyAfterThird[0].timestamp).isEqualTo(t2)
        assertThat(historyAfterThird[0].visitCount).isEqualTo(1)
        assertThat(historyAfterThird[1].timestamp).isEqualTo(t1)
        assertThat(historyAfterThird[1].visitCount).isEqualTo(2)
    }

    @Test
    fun favoriteRepository_limitsAndCRUD() = runTest(testDispatcher) {
        for (i in 1..10) {
            favoriteRepo.addFavorite("Fav $i", "https://fav$i.com")
        }

        val allFavorites = favoriteRepo.getFavorites().first()
        assertThat(allFavorites).hasSize(10)

        val limitedFavorites = favoriteRepo.getFavoritesLimited(6).first()
        assertThat(limitedFavorites).hasSize(6)

        // Delete one favorite
        favoriteRepo.deleteFavorite(allFavorites[0].id)
        val remaining = favoriteRepo.getFavorites().first()
        assertThat(remaining).hasSize(9)
    }

    @Test
    fun settingsRepository_flowUpdatesReactively() = runTest(testDispatcher) {
        val initial = settingsRepo.getSettings().first()
        assertThat(initial.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(initial.searchEngine).isEqualTo(SearchEngine.DUCKDUCKGO)
        assertThat(initial.isAdBlockerEnabled).isTrue()

        settingsRepo.setThemeMode(ThemeMode.DARK)
        settingsRepo.setSearchEngine(SearchEngine.BING)
        settingsRepo.setAdBlockerEnabled(false)

        val updated = settingsRepo.getSettings().first()
        assertThat(updated.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(updated.searchEngine).isEqualTo(SearchEngine.BING)
        assertThat(updated.isAdBlockerEnabled).isFalse()
    }
}
