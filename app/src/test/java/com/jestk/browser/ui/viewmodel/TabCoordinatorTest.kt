package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TabCoordinatorTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var recentTabRepo: RecentTabRepositoryImpl

    private var uiState = BrowserUiState()
    private val tabThemeColors = ConcurrentHashMap<String, Int>()
    private val tabLoadingProgress = ConcurrentHashMap<String, Int>()
    private val tabMutex = Mutex()
    private val navigationCommands = mutableListOf<NavigationCommand>()

    private lateinit var coordinator: TabCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)

        coordinator = TabCoordinator(
            tabRepository = tabRepo,
            recentTabRepository = recentTabRepo,
            favoriteRepository = favoriteRepo,
            historyRepository = historyRepo,
            webViewPool = null,
            tabOperationMutex = tabMutex,
            tabThemeColors = tabThemeColors,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            emitNavigation = { navigationCommands.add(it) },
            scope = testScope,
            ioDispatcher = testDispatcher,
            tabLoadingProgress = tabLoadingProgress,
        )
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun selectTab_whenSwitchingFromLoadingTabAToLoadedTabB_isLoadingBecomesFalseImmediately() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            // Open Tab A
            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!

            // Tab A begins loading at 45%
            coordinator.handlePageStarted(tabAId, "https://tab-a.com")
            coordinator.handleProgressChanged(tabAId, 45)

            assertThat(uiState.isLoading).isTrue()
            assertThat(uiState.loadingProgress).isEqualTo(0.45f)
            assertThat(coordinator.tabLoadingProgress[tabAId]).isEqualTo(45)

            // Open Tab B and complete loading
            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()
            val tabBId = uiState.activeTabId!!
            coordinator.handlePageStarted(tabBId, "https://tab-b.com")
            coordinator.handleProgressChanged(tabBId, 100)
            coordinator.handlePageFinished(tabBId, "https://tab-b.com", "Tab B")

            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(1.0f)

            // Switch to Tab A: loading state is restored
            coordinator.selectTab(tabAId)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tabAId)
            assertThat(uiState.isLoading).isTrue()
            assertThat(uiState.loadingProgress).isEqualTo(0.45f)

            // Switch to Tab B: isLoading becomes false immediately without leaking Tab A's progress
            coordinator.selectTab(tabBId)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tabBId)
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(1.0f)
        }

    @Test
    fun backgroundTabCompletion_updatesProgressInMapAndSwitchingBackShowsNotLoading() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            // Open Tab A
            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!
            coordinator.handlePageStarted(tabAId, "https://tab-a.com")
            coordinator.handleProgressChanged(tabAId, 30)

            // Open Tab B and finish loading
            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()
            val tabBId = uiState.activeTabId!!
            coordinator.handlePageFinished(tabBId, "https://tab-b.com", "Tab B")

            assertThat(uiState.activeTabId).isEqualTo(tabBId)
            assertThat(uiState.isLoading).isFalse()
            assertThat(coordinator.tabLoadingProgress[tabAId]).isEqualTo(30)

            // Tab A finishes in background while Tab B is active
            coordinator.handleProgressChanged(tabAId, 100)
            coordinator.handlePageFinished(tabAId, "https://tab-a.com", "Tab A")
            advanceUntilIdle()

            // tabLoadingProgress for Tab A is updated to 100
            assertThat(coordinator.tabLoadingProgress[tabAId]).isEqualTo(100)
            // Active tab (Tab B) UI state is not polluted
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(1.0f)

            // Switching back to Tab A shows isLoading = false
            coordinator.selectTab(tabAId)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tabAId)
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(1.0f)
        }

    @Test
    fun switchingBackToTabA_whileStillLoading_restoresLoadingStateCorrectly() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            // Open Tab A
            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!
            coordinator.handlePageStarted(tabAId, "https://tab-a.com")
            coordinator.handleProgressChanged(tabAId, 50)

            // Open Tab B and complete loading
            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()
            val tabBId = uiState.activeTabId!!
            coordinator.handlePageFinished(tabBId, "https://tab-b.com", "Tab B")

            assertThat(uiState.activeTabId).isEqualTo(tabBId)
            assertThat(uiState.isLoading).isFalse()

            // Tab A progresses to 85% in background
            coordinator.handleProgressChanged(tabAId, 85)
            advanceUntilIdle()
            assertThat(coordinator.tabLoadingProgress[tabAId]).isEqualTo(85)
            // Tab B still unaffected
            assertThat(uiState.isLoading).isFalse()

            // Switching back to Tab A restores loading state at 85%
            coordinator.selectTab(tabAId)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tabAId)
            assertThat(uiState.isLoading).isTrue()
            assertThat(uiState.loadingProgress).isEqualTo(0.85f)
        }

    @Test
    fun closeTab_removesTabFromLoadingProgressMap() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!
            coordinator.handlePageStarted(tabAId, "https://tab-a.com")
            coordinator.handleProgressChanged(tabAId, 40)

            assertThat(coordinator.tabLoadingProgress).containsKey(tabAId)

            coordinator.closeTab(tabAId)
            advanceUntilIdle()

            assertThat(coordinator.tabLoadingProgress).doesNotContainKey(tabAId)
        }

    @Test
    fun closeAllTabs_clearsAllLoadingProgress() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab-a.com")
            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()

            coordinator.closeAllTabs()
            advanceUntilIdle()

            // Only the new single home tab should exist in progress map with 100
            val homeTabId = uiState.activeTabId!!
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(0f)
            assertThat(coordinator.tabLoadingProgress[homeTabId]).isEqualTo(100)
        }

    @Test
    fun handleStopLoading_setsProgressTo100AndResetsIsLoading() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!
            coordinator.handlePageStarted(tabAId, "https://tab-a.com")
            coordinator.handleProgressChanged(tabAId, 35)

            assertThat(uiState.isLoading).isTrue()

            coordinator.handleStopLoading(tabAId)

            assertThat(uiState.isLoading).isFalse()
            assertThat(coordinator.tabLoadingProgress[tabAId]).isEqualTo(100)

            // Switch to another tab and switch back
            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()
            coordinator.selectTab(tabAId)
            advanceUntilIdle()

            // Tab A remains stopped
            assertThat(uiState.isLoading).isFalse()
            assertThat(uiState.loadingProgress).isEqualTo(1.0f)
        }
}
