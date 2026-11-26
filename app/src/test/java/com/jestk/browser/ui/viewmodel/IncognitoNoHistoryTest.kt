package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.IncognitoAwareTabRepository
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    fun pageLoadedInAnIncognitoTab_recordsNoVisit() = runTest(testDispatcher) {
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

    @Test
    fun favorites_refuseIncognitoAndNonWebTabs_andSaveARegularTabToItsOwnWorkspace() = runTest(testDispatcher) {
        val incognito = Tab(id = "i", url = "https://secret.example/", title = "Secret", isIncognito = true)
        val local = Tab(id = "f", url = "file:///sdcard/a.html", title = "Local")
        val web = Tab(id = "w", url = "https://example.com/", title = "Example", workspaceId = Workspace.DEFAULT_WORK_ID)
        uiState = uiState.copy(tabs = listOf(incognito, local, web), activeTabId = "i", currentUrl = incognito.url)

        coordinator.favorites.addTabToFavorites("i")
        coordinator.favorites.addTabToFavorites("f")
        coordinator.favorites.addCurrentToFavorites()
        coordinator.favorites.addTabToFavorites("w")
        advanceUntilIdle()
        val dao = db.favoriteDao()
        repeat(300) {
            if (dao.getAllFavorites(Workspace.DEFAULT_WORK_ID).isNotEmpty()) return@repeat
            withContext(Dispatchers.Default) { delay(10) }
            advanceUntilIdle()
        }

        assertThat(dao.getAllFavorites(Workspace.DEFAULT_WORK_ID).map { it.url }).containsExactly("https://example.com/")
        assertThat(dao.getAllFavorites(Workspace.DEFAULT_PERSONAL_ID)).isEmpty()
    }
}
