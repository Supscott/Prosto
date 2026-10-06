package com.jestk.browser.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
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
import com.jestk.browser.engine.FaviconManager
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
        FaviconManager.init(context)
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
        FaviconManager.deleteAllFavicons()
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

    @Test
    fun navigateToUrl_whenCachedFaviconExists_prepopulatesFaviconImmediately() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val cachedPath = FaviconManager.cacheFaviconForDomain("github.com", bitmap, context)
            assertThat(cachedPath).isNotNull()

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!

            coordinator.navigateToUrl("https://github.com/torvalds/linux")
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.faviconUrl).isEqualTo(cachedPath)
            assertThat(tabRepo.getTabById(activeTabId)?.faviconUrl).isEqualTo(cachedPath)
        }

    @Test
    fun navigateToUrl_whenUncachedAndCrossDomain_resetsFaviconToNull() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.handleUpdateFavicon(activeTabId, "/dummy/old_favicon.png")
            advanceUntilIdle()
            assertThat(uiState.tabs.find { it.id == activeTabId }?.faviconUrl).isEqualTo("/dummy/old_favicon.png")

            // Navigate to an uncached cross-domain URL
            coordinator.navigateToUrl("https://uncached-site.org/home")
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.faviconUrl).isNull()
            assertThat(tabRepo.getTabById(activeTabId)?.faviconUrl).isNull()
        }

    @Test
    fun handlePageStarted_whenCachedFaviconExists_setsFaviconFromCache() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val cachedPath = FaviconManager.cacheFaviconForDomain("kotlinlang.org", bitmap, context)
            assertThat(cachedPath).isNotNull()

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.handlePageStarted(activeTabId, "https://kotlinlang.org/docs/home.html")
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.faviconUrl).isEqualTo(cachedPath)
            assertThat(tabRepo.getTabById(activeTabId)?.faviconUrl).isEqualTo(cachedPath)
        }

    @Test
    fun openNewTab_whenCachedFaviconExists_prepopulatesFaviconImmediately() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val cachedPath = FaviconManager.cacheFaviconForDomain("reddit.com", bitmap, context)
            assertThat(cachedPath).isNotNull()

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://reddit.com/r/android")
            advanceUntilIdle()

            val newTabId = uiState.activeTabId!!
            val newTab = uiState.tabs.find { it.id == newTabId }
            assertThat(newTab?.faviconUrl).isEqualTo(cachedPath)
            assertThat(tabRepo.getTabById(newTabId)?.faviconUrl).isEqualTo(cachedPath)
        }

    @Test
    fun handleUpdateFavicon_updatesRecentTabAndCachesDomainFavicon() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FaviconManager.init(context)
            val dummyFile = java.io.File(context.filesDir, "favicons/test_source.png").apply {
                parentFile?.mkdirs()
                writeBytes("png_data".toByteArray())
            }

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://news.ycombinator.com")
            advanceUntilIdle()

            coordinator.handleUpdateFavicon(activeTabId, dummyFile.absolutePath)
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.faviconUrl).isEqualTo(dummyFile.absolutePath)

            // Verify domain cache was populated
            val cached = FaviconManager.getCachedFaviconForUrl("https://news.ycombinator.com/item?id=123")
            assertThat(cached).isNotNull()
            assertThat(java.io.File(cached!!).name).isEqualTo("domain_news.ycombinator.com.png")
        }
}
