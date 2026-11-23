package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.util.concurrent.ConcurrentHashMap

/** One page load is one history visit, however many finish and title events it reports (PRO-42). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PageVisitHistoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var webViewPool: WebViewPool
    private lateinit var coordinator: TabCoordinator
    private var uiState = BrowserUiState()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        FaviconManager.init(context)
        db = AppDatabase.createInMemory(context)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        webViewPool = WebViewPool(context)
        coordinator = TabCoordinator(
            tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            historyRepository = historyRepo,
            webViewPool = webViewPool,
            tabOperationMutex = Mutex(),
            tabThemeColors = ConcurrentHashMap(),
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            emitNavigation = {},
            scope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun teardown() {
        webViewPool.clearAll()
        FaviconManager.deleteAllFavicons()
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun visit(url: String): HistoryItem? = historyRepo.getAllHistory().first().find { it.url == url }

    @Test
    fun onePageLoadWithSeveralTitleAndFinishEvents_countsOneVisit() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        val url = "https://example.com/?tab8"
        val events = coordinator.pageEvents

        events.handlePageStarted(tab, url)
        events.handleTitleReceived(tab, "Example Domain")
        events.handleTitleReceived(tab, "Example Domain")
        events.handlePageFinished(tab, url, "Example Domain")
        // The back/forward cache path can finish the same navigation once more.
        events.handlePageFinished(tab, url, "Example Domain")
        advanceUntilIdle()

        assertThat(visit(url)?.visitCount).isEqualTo(1)
        assertThat(visit(url)?.title).isEqualTo("Example Domain")
    }

    @Test
    fun laterTitle_renamesTheVisitWithoutCountingAnother() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        val url = "https://news.example/story"
        val events = coordinator.pageEvents

        events.handlePageStarted(tab, url)
        events.handlePageFinished(tab, url, url)
        events.handleTitleReceived(tab, "The Story")
        advanceUntilIdle()

        assertThat(visit(url)?.visitCount).isEqualTo(1)
        assertThat(visit(url)?.title).isEqualTo("The Story")
    }

    @Test
    fun navigatingAwayAndBack_countsEachVisitAndKeepsTitlesApart() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        val first = "https://example.com/"
        val second = "https://other.example/"
        val events = coordinator.pageEvents

        events.handlePageStarted(tab, first)
        events.handlePageFinished(tab, first, "Example Domain")
        events.handlePageStarted(tab, second)
        events.handlePageFinished(tab, second, "Other")
        events.handlePageStarted(tab, first)
        events.handlePageFinished(tab, first, "Example Domain")
        advanceUntilIdle()

        assertThat(visit(first)?.visitCount).isEqualTo(2)
        assertThat(visit(second)?.visitCount).isEqualTo(1)
        assertThat(visit(second)?.title).isEqualTo("Other")
    }

    @Test
    fun sameDocumentNavigation_isRecordedByItsTitle() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        val events = coordinator.pageEvents

        events.handlePageStarted(tab, "https://spa.example/")
        events.handlePageFinished(tab, "https://spa.example/", "Home")
        // History API navigation: doUpdateVisitedHistory -> page started, then a title, no finish.
        events.handlePageStarted(tab, "https://spa.example/watch?v=1")
        events.handleTitleReceived(tab, "Video 1")
        advanceUntilIdle()

        assertThat(visit("https://spa.example/watch?v=1")?.visitCount).isEqualTo(1)
        assertThat(visit("https://spa.example/watch?v=1")?.title).isEqualTo("Video 1")
        assertThat(visit("https://spa.example/")?.title).isEqualTo("Home")
    }
}
