package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.IncognitoAwareTabRepository
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.Workspace
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

/** An incognito tab leaves no history visit, recent tab, snapshot or Room row (PRO-75). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class IncognitoNoHistoryTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var tabRepo: IncognitoAwareTabRepository
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
        tabRepo = IncognitoAwareTabRepository(TabRepositoryImpl(db.tabDao(), testDispatcher))
        webViewPool = WebViewPool(context)
        coordinator = TabCoordinator(
            tabRepository = tabRepo,
            recentTabRepository = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher),
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

    @Test
    fun pageLoadedInAnIncognitoTab_recordsNoVisitAndNoRecentTab() = runTest(testDispatcher) {
        val tab = tabRepo.createTab(url = "", title = "", isIncognito = true)
        uiState = uiState.copy(tabs = listOf(tab), activeTabId = tab.id)
        val url = "https://secret.example/page"
        val events = coordinator.pageEvents

        events.handlePageStarted(tab.id, url)
        events.handleTitleReceived(tab.id, "Secret")
        events.handlePageFinished(tab.id, url, "Secret")
        coordinator.pageAppearance.handleSnapshot(tab.id, "/files/snap.png")
        advanceUntilIdle()

        assertThat(historyRepo.getAllHistory().first()).isEmpty()
        assertThat(db.recentTabDao().getRecentTabs(10, Workspace.DEFAULT_PERSONAL_ID)).isEmpty()
        assertThat(db.tabDao().getAllTabs()).isEmpty()
        assertThat(tabRepo.getTabById(tab.id)?.snapshotPath).isNull()
    }

    @Test
    fun pageLoadedInARegularTab_stillRecordsTheVisit() = runTest(testDispatcher) {
        val tab = tabRepo.createTab(url = "", title = "")
        uiState = uiState.copy(tabs = listOf(tab), activeTabId = tab.id)
        val url = "https://example.com/page"
        val events = coordinator.pageEvents

        events.handlePageStarted(tab.id, url)
        events.handlePageFinished(tab.id, url, "Example")
        advanceUntilIdle()

        assertThat(historyRepo.getAllHistory().first().map { it.url }).containsExactly(url)
    }
}
