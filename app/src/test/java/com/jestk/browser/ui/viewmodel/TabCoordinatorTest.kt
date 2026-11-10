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
import com.jestk.browser.data.repository.TabFolderRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.engine.FaviconManager
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
    private lateinit var tabFolderRepo: TabFolderRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var recentTabRepo: RecentTabRepositoryImpl

    private var uiState = BrowserUiState()
    private val tabThemeColors = ConcurrentHashMap<String, Int>()
    private val tabLoadingProgress = ConcurrentHashMap<String, Int>()
    private val tabMutex = Mutex()
    private val navigationCommands = mutableListOf<NavigationCommand>()

    private lateinit var coordinator: TabCoordinator
    private lateinit var webViewPool: com.jestk.browser.engine.WebViewPool

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        FaviconManager.init(context)
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        tabFolderRepo = TabFolderRepositoryImpl(db.tabFolderDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
        webViewPool = com.jestk.browser.engine.WebViewPool(context)

        coordinator = TabCoordinator(
            tabRepository = tabRepo,
            recentTabRepository = recentTabRepo,
            favoriteRepository = favoriteRepo,
            historyRepository = historyRepo,
            webViewPool = webViewPool,
            tabOperationMutex = tabMutex,
            tabThemeColors = tabThemeColors,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            emitNavigation = { navigationCommands.add(it) },
            scope = testScope,
            ioDispatcher = testDispatcher,
            tabLoadingProgress = tabLoadingProgress,
            tabFolderRepository = tabFolderRepo,
        )
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        webViewPool.clearAll()
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
    fun closeTab_whenActiveTabClosed_synchronouslySwitchesToAdjacentTabWithCorrectUrl() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()
            val initialHomeId = uiState.activeTabId!!

            coordinator.openNewTab("https://tab-a.com")
            advanceUntilIdle()
            val tabAId = uiState.activeTabId!!
            webViewPool.getOrCreateWebView(tabAId)

            coordinator.openNewTab("https://tab-b.com")
            advanceUntilIdle()
            val tabBId = uiState.activeTabId!!
            webViewPool.getOrCreateWebView(tabBId)

            assertThat(uiState.activeTabId).isEqualTo(tabBId)
            assertThat(uiState.currentUrl).isEqualTo("https://tab-b.com")
            assertThat(webViewPool.getWebView(tabBId)).isNotNull()
            assertThat(webViewPool.getWebView(tabAId)).isNotNull()

            // Close active tab B
            coordinator.closeTab(tabBId)

            // Must switch synchronously BEFORE advanceUntilIdle
            assertThat(uiState.activeTabId).isEqualTo(tabAId)
            assertThat(uiState.currentUrl).isEqualTo("https://tab-a.com")
            assertThat(uiState.tabs.map { it.id }).containsExactly(initialHomeId, tabAId)
            assertThat(webViewPool.getWebView(tabBId)).isNull()
            assertThat(webViewPool.getWebView(tabAId)).isNotNull()

            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tabAId)
            assertThat(uiState.currentUrl).isEqualTo("https://tab-a.com")
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

    @Test
    fun handleUpdateFavicon_whenPageUrlFromDifferentDomain_isIgnored() =
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
            coordinator.navigateToUrl("https://currentsite.com")
            advanceUntilIdle()

            // Late-arriving favicon from previous site (e.g. oldsite.com)
            coordinator.handleUpdateFavicon(activeTabId, dummyFile.absolutePath, pageUrl = "https://oldsite.com")
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            // Tab must NOT have oldsite's favicon
            assertThat(activeTab?.faviconUrl).isNull()
            assertThat(tabRepo.getTabById(activeTabId)?.faviconUrl).isNull()

            // Current site domain cache must not be poisoned with oldsite's favicon
            val cachedForCurrent = FaviconManager.getCachedFaviconForUrl("https://currentsite.com")
            assertThat(cachedForCurrent).isNull()
        }

    @Test
    fun handleUpdateFavicon_whenDomainFaviconDoesNotMatchCurrentTab_isIgnored() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FaviconManager.init(context)
            val domainFile = java.io.File(context.filesDir, "favicons/domain_different_site_com.png").apply {
                parentFile?.mkdirs()
                writeBytes("different_site_png".toByteArray())
            }

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://mysite.com")
            advanceUntilIdle()

            // Mismatched domain favicon path
            coordinator.handleUpdateFavicon(activeTabId, domainFile.absolutePath)
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.faviconUrl).isNull()
            assertThat(tabRepo.getTabById(activeTabId)?.faviconUrl).isNull()
        }

    @Test
    fun navigateToUrl_toDifferentDomain_cleansUpTabFaviconFile() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FaviconManager.init(context)

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://site1.com")
            advanceUntilIdle()

            // Save a tab favicon file on disk
            val tabFaviconFile = java.io.File(context.filesDir, "favicons/favicon_$activeTabId.png").apply {
                parentFile?.mkdirs()
                writeBytes("site1_icon".toByteArray())
            }
            assertThat(tabFaviconFile.exists()).isTrue()

            // Cross-domain navigation
            coordinator.navigateToUrl("https://site2.com")
            advanceUntilIdle()

            // Old tab favicon file must be deleted
            assertThat(tabFaviconFile.exists()).isFalse()
        }

    @Test
    fun restoreSession_whenTabFaviconPointsToDifferentDomainFile_resolvesCorrectly() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            FaviconManager.init(context)

            // Cache correct icon for siteB
            val siteBBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val siteBPath = FaviconManager.cacheFaviconForDomain("siteb.com", siteBBitmap, context)
            assertThat(siteBPath).isNotNull()

            // Setup DB tab with siteB url but mismatched domain_sitea_com.png favicon
            val wrongDomainFile = java.io.File(context.filesDir, "favicons/domain_sitea_com.png").apply {
                parentFile?.mkdirs()
                writeBytes("sitea_icon".toByteArray())
            }

            val tab = tabRepo.createTab("https://siteb.com", "Site B")
            tabRepo.updateFavicon(tab.id, wrongDomainFile.absolutePath)

            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val restoredTab = uiState.tabs.find { it.id == tab.id }
            assertThat(restoredTab?.faviconUrl).isEqualTo(siteBPath)
        }

    @Test
    fun handleTitleReceived_updatesUiStateAndPersistsToRepositories() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://example.com")
            advanceUntilIdle()

            coordinator.handleTitleReceived(activeTabId, "Example Domain")
            advanceUntilIdle()

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.title).isEqualTo("Example Domain")
            assertThat(uiState.pageTitle).isEqualTo("Example Domain")

            val persistedTab = tabRepo.getTabById(activeTabId)
            assertThat(persistedTab?.title).isEqualTo("Example Domain")

            val history = historyRepo.getAllHistory().first()
            assertThat(history.any { it.url == "https://example.com" && it.title == "Example Domain" }).isTrue()
        }

    @Test
    fun handleTitleReceived_whenBlankOrAboutBlank_isIgnored() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://example.com")
            advanceUntilIdle()

            coordinator.handleTitleReceived(activeTabId, "   ")
            advanceUntilIdle()
            assertThat(uiState.pageTitle).isEqualTo("https://example.com")

            coordinator.handleTitleReceived(activeTabId, "about:blank")
            advanceUntilIdle()
            assertThat(uiState.pageTitle).isEqualTo("https://example.com")
        }

    @Test
    fun handlePageStarted_whenCrossDomain_resetsTitleToLoading() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            coordinator.navigateToUrl("https://first-site.com")
            coordinator.handlePageFinished(activeTabId, "https://first-site.com", "First Site Title")
            advanceUntilIdle()

            assertThat(uiState.pageTitle).isEqualTo("First Site Title")

            // Navigate to cross-domain site
            coordinator.handlePageStarted(activeTabId, "https://second-site.org/path")
            advanceUntilIdle()

            assertThat(uiState.pageTitle).isEqualTo("Loading...")
            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.title).isEqualTo("Loading...")
            assertThat(tabRepo.getTabById(activeTabId)?.title).isEqualTo("Loading...")
        }

    @Test
    fun observeDatabaseFlows_prioritizesInMemoryActiveTabIdAndPreservesNewUrl() =
        runTest(testDispatcher) {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            coordinator.observeDatabaseFlows()
            advanceUntilIdle()

            // Open a second tab
            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val secondTabId = uiState.activeTabId!!

            // Start navigation in memory
            coordinator.handlePageStarted(secondTabId, "https://tab2-new.com")
            assertThat(uiState.currentUrl).isEqualTo("https://tab2-new.com")
            assertThat(uiState.pageTitle).isEqualTo("Loading...")

            // Let Room flow collect and advance
            advanceUntilIdle()

            assertThat(uiState.activeTabId).isEqualTo(secondTabId)
            assertThat(uiState.currentUrl).isEqualTo("https://tab2-new.com")
            assertThat(uiState.pageTitle).isEqualTo("Loading...")
        }

    @Test
    fun selectTab_whenLiveWebViewHasUrlAndTitle_syncsLiveState() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val pool = com.jestk.browser.engine.WebViewPool(context, null)
            val coordinatorWithPool = TabCoordinator(
                tabRepository = tabRepo,
                recentTabRepository = recentTabRepo,
                favoriteRepository = favoriteRepo,
                historyRepository = historyRepo,
                webViewPool = pool,
                tabOperationMutex = tabMutex,
                tabThemeColors = tabThemeColors,
                getCurrentState = { uiState },
                updateUiState = { transform -> uiState = transform(uiState) },
                emitNavigation = { navigationCommands.add(it) },
                scope = testScope,
                ioDispatcher = testDispatcher,
                tabLoadingProgress = tabLoadingProgress,
            )

            coordinatorWithPool.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val tab1Id = uiState.activeTabId!!

            // Open tab 2
            coordinatorWithPool.openNewTab("https://tab2.com")
            advanceUntilIdle()

            // Get live WebView for tab 1 and load a live URL
            val wv1 = pool.getOrCreateWebView(tab1Id, context)
            wv1.loadUrl("https://tab1-live.com")

            // Switch back to tab 1
            coordinatorWithPool.selectTab(tab1Id)
            advanceUntilIdle()

            assertThat(uiState.activeTabId).isEqualTo(tab1Id)
            assertThat(uiState.currentUrl).isEqualTo("https://tab1-live.com")
        }

    @Test
    fun handleExternalAppOffered_displaysPromptForActiveTab_andSuppressesAfterDismissal() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = activeTabId,
                appName = "YouTube",
                appPackage = "com.google.android.youtube",
                intentUri = "https://youtube.com/watch?v=123",
                isInstalled = true,
                domain = "youtube.com"
            )

            // Offer for active tab sets prompt
            coordinator.handleExternalAppOffered(activeTabId, prompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(prompt)

            // Dismiss explicitly with ExternalAppDismissReason.EXPLICIT
            coordinator.dismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT)
            assertThat(uiState.externalAppPrompt).isNull()

            // Subsequent offer for same domain is suppressed
            val secondPrompt = prompt.copy(id = "prompt_2")
            coordinator.handleExternalAppOffered(activeTabId, secondPrompt)
            assertThat(uiState.externalAppPrompt).isNull()

            // Navigating to a different domain clears suppression
            coordinator.handlePageStarted(activeTabId, "https://wikipedia.org/wiki/Kotlin")
            advanceUntilIdle()

            // Now YouTube offer can appear again or new domain offer works
            coordinator.handleExternalAppOffered(activeTabId, secondPrompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(secondPrompt)
        }

    @Test
    fun handleExternalAppOffered_ignoresBackgroundTab() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()

            val bgTabId = uiState.tabs.first { !it.isActive }.id
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = bgTabId,
                appName = "Telegram",
                intentUri = "tg://resolve?domain=test",
                domain = "tg"
            )

            coordinator.handleExternalAppOffered(bgTabId, prompt)
            assertThat(uiState.externalAppPrompt).isNull()
        }

    @Test
    fun handlePageStarted_preservesPrompt_whenNavigatingFromSearchToTargetDomain() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            // User begins on google.com
            coordinator.handlePageStarted(activeTabId, "https://google.com/search?q=instagram")
            advanceUntilIdle()

            // User taps instagram.com link: external app offered for instagram.com
            val instagramPrompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = activeTabId,
                appName = "Instagram",
                appPackage = "com.instagram.android",
                intentUri = "https://instagram.com/profile",
                isInstalled = true,
                domain = "instagram.com"
            )
            coordinator.handleExternalAppOffered(activeTabId, instagramPrompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(instagramPrompt)

            // Web navigation begins loading the clicked destination: https://instagram.com/profile
            // Previously, !sameDomain("google.com", "instagram.com") caused prompt to be wiped out immediately.
            coordinator.handlePageStarted(activeTabId, "https://instagram.com/profile")
            advanceUntilIdle()

            // The prompt MUST be preserved because it was offered for this navigation destination!
            assertThat(uiState.externalAppPrompt).isEqualTo(instagramPrompt)

            // If user subsequently navigates to an unrelated domain (e.g. wikipedia.org), prompt is cleared
            coordinator.handlePageStarted(activeTabId, "https://en.wikipedia.org/wiki/Kotlin")
            advanceUntilIdle()
            assertThat(uiState.externalAppPrompt).isNull()
        }

    @Test
    fun handleExternalAppOffered_forBackgroundTab_isShownWhenTabBecomesActive() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://foreground.com")
            advanceUntilIdle()
            coordinator.openTabInBackground("https://reddit.com/r/android")
            advanceUntilIdle()

            val backgroundTabId = uiState.tabs.first { !it.isActive }.id
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = backgroundTabId,
                appName = "Reddit",
                appPackage = "com.reddit.frontpage",
                intentUri = "https://reddit.com/r/android",
                isInstalled = true,
                domain = "reddit.com"
            )

            coordinator.handleExternalAppOffered(backgroundTabId, prompt)
            assertThat(uiState.externalAppPrompt).isNull()

            coordinator.selectTab(backgroundTabId)
            advanceUntilIdle()

            assertThat(uiState.externalAppPrompt).isEqualTo(prompt)
        }

    @Test
    fun dismissExternalAppPrompt_withoutRememberingDomain_allowsPromptAgain() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = activeTabId,
                appName = "Reddit",
                appPackage = "com.reddit.frontpage",
                intentUri = "https://reddit.com/r/android",
                isInstalled = true,
                domain = "reddit.com"
            )

            coordinator.handleExternalAppOffered(activeTabId, prompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(prompt)

            // Transient dismissal (e.g. app opened or transient dismissal)
            coordinator.dismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT)
            assertThat(uiState.externalAppPrompt).isNull()

            val secondPrompt = prompt.copy(id = "prompt_timeout_retry")
            coordinator.handleExternalAppOffered(activeTabId, secondPrompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(secondPrompt)
        }

    @Test
    fun handleExternalAppOffered_whenExplicitlyDismissed_suppressesEvenForUserInitiated() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = activeTabId,
                appName = "Reddit",
                appPackage = "com.reddit.frontpage",
                intentUri = "https://reddit.com/r/android",
                isInstalled = true,
                domain = "reddit.com"
            )

            coordinator.handleExternalAppOffered(activeTabId, prompt, userInitiated = true)
            assertThat(uiState.externalAppPrompt).isEqualTo(prompt)

            // User explicitly clicked "X" to dismiss the pill
            coordinator.dismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT)
            assertThat(uiState.externalAppPrompt).isNull()

            // Deliberate user tap must STILL be suppressed on this tab because user explicitly dismissed it
            val secondPrompt = prompt.copy(id = "prompt_explicit_retry")
            coordinator.handleExternalAppOffered(activeTabId, secondPrompt, userInitiated = true)
            assertThat(uiState.externalAppPrompt).isNull()
        }

    @Test
    fun handleExternalAppOffered_whenTimedOut_suppressesPassiveOffersButAllowsUserInitiated() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            val activeTabId = uiState.activeTabId!!
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = activeTabId,
                appName = "Reddit",
                appPackage = "com.reddit.frontpage",
                intentUri = "https://reddit.com/r/android",
                isInstalled = true,
                domain = "reddit.com"
            )

            coordinator.handleExternalAppOffered(activeTabId, prompt, userInitiated = false)
            assertThat(uiState.externalAppPrompt).isEqualTo(prompt)

            // Auto-timeout
            coordinator.dismissExternalAppPrompt(ExternalAppDismissReason.TIMEOUT)
            assertThat(uiState.externalAppPrompt).isNull()

            // Passive offer remains suppressed (anti-nag)
            val passiveRetry = prompt.copy(id = "prompt_passive_retry")
            coordinator.handleExternalAppOffered(activeTabId, passiveRetry, userInitiated = false)
            assertThat(uiState.externalAppPrompt).isNull()

            // But deliberate user tap bypasses timeout suppression!
            val userTap = prompt.copy(id = "prompt_user_tap")
            coordinator.handleExternalAppOffered(activeTabId, userTap, userInitiated = true)
            assertThat(uiState.externalAppPrompt).isEqualTo(userTap)
        }

    @Test
    fun openNewTab_clearsDismissedDomainForReusedTab() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://reddit.com/r/android")
            advanceUntilIdle()
            val tabId = uiState.activeTabId!!
            val prompt = com.jestk.browser.domain.model.ExternalAppPrompt(
                tabId = tabId,
                appName = "Reddit",
                appPackage = "com.reddit.frontpage",
                intentUri = "https://reddit.com/r/android",
                isInstalled = true,
                domain = "reddit.com"
            )

            coordinator.handleExternalAppOffered(tabId, prompt)
            coordinator.dismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT)
            assertThat(uiState.externalAppPrompt).isNull()

            // Same URL opened again from another app: the tab is reused, but the notification
            // must be offered for this new navigation.
            coordinator.openNewTab("https://reddit.com/r/android")
            advanceUntilIdle()

            val secondPrompt = prompt.copy(id = "prompt_reopened")
            coordinator.handleExternalAppOffered(uiState.activeTabId!!, secondPrompt)
            assertThat(uiState.externalAppPrompt).isEqualTo(secondPrompt)
        }

    @Test
    fun handleThemeColors_updatesTopAndBottomThemeColorsInState() =
        testScope.runTest {
            coordinator.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
            advanceUntilIdle()

            coordinator.openNewTab("https://example.com")
            advanceUntilIdle()
            val activeTabId = uiState.activeTabId!!

            val topColor = 0xFF123456.toInt()
            val bottomColor = 0xFF654321.toInt()

            coordinator.handleThemeColors(activeTabId, topColor, bottomColor)
            advanceUntilIdle()

            assertThat(uiState.activeThemeColor).isEqualTo(topColor)
            assertThat(uiState.activeTopThemeColor).isEqualTo(topColor)
            assertThat(uiState.activeBottomThemeColor).isEqualTo(bottomColor)

            val activeTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(activeTab?.themeColor).isEqualTo(topColor)
            assertThat(activeTab?.bottomThemeColor).isEqualTo(bottomColor)

            // When subsequently meta top theme color is received without bottom color, bottom color should be preserved
            val newTopColor = 0xFF998877.toInt()
            coordinator.handleThemeColors(activeTabId, newTopColor, null)
            advanceUntilIdle()

            assertThat(uiState.activeTopThemeColor).isEqualTo(newTopColor)
            assertThat(uiState.activeBottomThemeColor).isEqualTo(bottomColor)
            val updatedActiveTab = uiState.tabs.find { it.id == activeTabId }
            assertThat(updatedActiveTab?.themeColor).isEqualTo(newTopColor)
            assertThat(updatedActiveTab?.bottomThemeColor).isEqualTo(bottomColor)
        }

    @Test
    fun openPopupTab_createsNewActiveTabWithSpecifiedId() =
        runTest {
            val popupTabId = "popup-tab-123"
            coordinator.openPopupTab(popupTabId)
            advanceUntilIdle()

            assertThat(uiState.activeTabId).isEqualTo(popupTabId)
            val popupTab = uiState.tabs.find { it.id == popupTabId }
            assertThat(popupTab).isNotNull()
            assertThat(popupTab?.isActive).isTrue()
            assertThat(popupTab?.workspaceId).isEqualTo(uiState.activeWorkspaceId)
            assertThat(uiState.isLoading).isTrue()
            assertThat(uiState.loadingProgress).isGreaterThan(0f)
            assertThat(uiState.isHome).isFalse()
            assertThat(uiState.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun createFolder_createsFolderAndAssignsToTab() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tabId = uiState.activeTabId!!

            val folder = coordinator.createFolder(tabId, title = "Research", color = 0xFF10B981.toInt())
            advanceUntilIdle()

            assertThat(folder.title).isEqualTo("Research")
            assertThat(folder.color).isEqualTo(0xFF10B981.toInt())
            assertThat(uiState.getTabsInFolder(folder.id).map { it.id }).containsExactly(tabId)
            assertThat(uiState.getFolder(folder.id)).isNotNull()

            val fromRepo = tabFolderRepo.getFolderById(folder.id)
            assertThat(fromRepo).isNotNull()
            assertThat(fromRepo?.title).isEqualTo("Research")
        }

    @Test
    fun createFolderFromTabs_groupsBothTabs() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolderFromTabs(tab1Id, tab2Id, color = 0xFF2196F3.toInt())
            advanceUntilIdle()

            val tabsInFolder = uiState.getTabsInFolder(folder.id)
            assertThat(tabsInFolder.map { it.id }).containsExactly(tab1Id, tab2Id)
        }

    @Test
    fun updateFolder_updatesTitleAndColor() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            val folder = coordinator.createFolder(tab1Id, title = "Old Title", color = 100)
            advanceUntilIdle()

            coordinator.updateFolder(folder.id, title = "New Title", color = 200)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)?.title).isEqualTo("New Title")
            assertThat(uiState.getFolder(folder.id)?.color).isEqualTo(200)

            val fromRepo = tabFolderRepo.getFolderById(folder.id)
            assertThat(fromRepo?.title).isEqualTo("New Title")
            assertThat(fromRepo?.color).isEqualTo(200)
        }

    @Test
    fun addAndRemoveTabFromFolder_managesMembershipAndAutoDeletesWhenEmpty() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolder(tab1Id, title = "Folder", color = 1)
            advanceUntilIdle()

            coordinator.addTabToFolder(tab2Id, folder.id)
            advanceUntilIdle()
            assertThat(uiState.getTabsInFolder(folder.id).map { it.id }).containsExactly(tab1Id, tab2Id)

            // Remove tab2: folder should remain with 1 tab (tab1)
            coordinator.removeTabFromFolder(tab2Id)
            advanceUntilIdle()
            assertThat(uiState.getTabsInFolder(folder.id).map { it.id }).containsExactly(tab1Id)
            assertThat(uiState.getFolder(folder.id)).isNotNull()

            // Remove tab1: folder is now empty and must be automatically deleted
            coordinator.removeTabFromFolder(tab1Id)
            advanceUntilIdle()
            assertThat(uiState.getFolder(folder.id)).isNull()
            assertThat(tabFolderRepo.getFolderById(folder.id)).isNull()
        }

    @Test
    fun closeTab_whenLastTabInFolder_autoDeletesFolder() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolder(tab1Id, title = "Group", color = 1)
            advanceUntilIdle()

            // Closing tab1 (the only tab in folder): folder must be deleted
            coordinator.closeTab(tab1Id)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)).isNull()
            assertThat(tabFolderRepo.getFolderById(folder.id)).isNull()
        }

    @Test
    fun closeTab_whenOneTabRemains_preservesFolder() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolderFromTabs(tab1Id, tab2Id, color = 1)
            advanceUntilIdle()

            // Close tab2: folder must still exist with 1 tab remaining
            coordinator.closeTab(tab2Id)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)).isNotNull()
            assertThat(uiState.getTabsInFolder(folder.id).map { it.id }).containsExactly(tab1Id)
            assertThat(tabFolderRepo.getFolderById(folder.id)).isNotNull()
        }

    @Test
    fun ungroupFolder_removesFolderAndPreservesTabs() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolderFromTabs(tab1Id, tab2Id, color = 1)
            advanceUntilIdle()

            coordinator.ungroupFolder(folder.id)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)).isNull()
            assertThat(tabFolderRepo.getFolderById(folder.id)).isNull()
            assertThat(uiState.tabs.find { it.id == tab1Id }?.folderId).isNull()
            assertThat(uiState.tabs.find { it.id == tab2Id }?.folderId).isNull()
        }

    @Test
    fun closeFolder_closesAllTabsAndDeletesFolder() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab-outside.com")
            advanceUntilIdle()
            val outsideTabId = uiState.activeTabId!!

            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folder = coordinator.createFolderFromTabs(tab1Id, tab2Id, color = 1)
            advanceUntilIdle()

            coordinator.closeFolder(folder.id)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)).isNull()
            assertThat(tabFolderRepo.getFolderById(folder.id)).isNull()
            assertThat(uiState.tabs.map { it.id }).containsExactly(outsideTabId)
            assertThat(uiState.activeTabId).isEqualTo(outsideTabId)
        }

    @Test
    fun toggleFolderCollapse_fromDock_whenActiveTabInside_switchesToNearestOutsideTab() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab0.com")
            advanceUntilIdle()
            val tab0Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            // Group tab1 and tab2
            val folder = coordinator.createFolderFromTabs(tab1Id, tab2Id, color = 1)
            advanceUntilIdle()

            // tab2 is currently active (inside the folder)
            assertThat(uiState.activeTabId).isEqualTo(tab2Id)

            // Collapse from dock
            coordinator.toggleFolderCollapse(folder.id, fromDock = true)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)?.isCollapsed).isTrue()
            // Active tab must switch to tab0 (the nearest outside tab)
            assertThat(uiState.activeTabId).isEqualTo(tab0Id)
        }

    @Test
    fun toggleFolderCollapse_fromDock_whenActiveTabInsideAndNoOutsideTabs_opensHomeTab() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            val folder = coordinator.createFolder(tab1Id, color = 1)
            advanceUntilIdle()

            assertThat(uiState.activeTabId).isEqualTo(tab1Id)

            // Collapse from dock when no outside tabs exist
            coordinator.toggleFolderCollapse(folder.id, fromDock = true)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folder.id)?.isCollapsed).isTrue()
            // Active tab should now be a newly created Home tab
            assertThat(uiState.activeTabId).isNotEqualTo(tab1Id)
            val newActive = uiState.activeTab
            assertThat(newActive?.folderId).isNull()
            assertThat(newActive?.title).isEqualTo("Home")
        }

    @Test
    fun selectTab_whenTabInsideCollapsedFolder_autoExpandsFolder() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab0.com")
            advanceUntilIdle()
            val tab0Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            val folder = coordinator.createFolder(tab1Id, color = 1)
            advanceUntilIdle()

            // Collapse folder
            coordinator.toggleFolderCollapse(folder.id, fromDock = true)
            advanceUntilIdle()
            assertThat(uiState.getFolder(folder.id)?.isCollapsed).isTrue()
            assertThat(uiState.activeTabId).isEqualTo(tab0Id)

            // Selecting tab1 must auto-expand the folder
            coordinator.selectTab(tab1Id)
            advanceUntilIdle()

            assertThat(uiState.activeTabId).isEqualTo(tab1Id)
            assertThat(uiState.getFolder(folder.id)?.isCollapsed).isFalse()
            assertThat(tabFolderRepo.getFolderById(folder.id)?.isCollapsed).isFalse()
        }

    @Test
    fun toggleFolderCollapse_fromDock_whenAdjacentFolderCollapsed_switchesToEligibleTabWithoutExpandingAdjacentFolder() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab0.com")
            advanceUntilIdle()
            val tab0Id = uiState.activeTabId!!

            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            val folderB = coordinator.createFolder(tab1Id, color = 1)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folderA = coordinator.createFolder(tab2Id, color = 2)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tab2Id)

            // Collapse folderB while tab2 in folderA is active
            coordinator.toggleFolderCollapse(folderB.id, fromDock = true)
            advanceUntilIdle()
            assertThat(uiState.getFolder(folderB.id)?.isCollapsed).isTrue()

            // Current tabs order: tab0 (outside), tab1 (folderB, collapsed), tab2 (folderA, open & active)
            // tab1 is adjacent to tab2 (distance 1), tab0 is further away (distance 2).
            // When closing folderA from dock, it must NOT pick tab1 (which would expand folderB).
            // It must pick tab0 (outside and eligible).
            coordinator.toggleFolderCollapse(folderA.id, fromDock = true)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folderA.id)?.isCollapsed).isTrue()
            assertThat(uiState.getFolder(folderB.id)?.isCollapsed).isTrue()
            assertThat(uiState.activeTabId).isEqualTo(tab0Id)
        }

    @Test
    fun toggleFolderCollapse_fromDock_whenAllOtherTabsInCollapsedFolders_opensHomeTab() =
        runTest(testDispatcher) {
            coordinator.openNewTab("https://tab1.com")
            advanceUntilIdle()
            val tab1Id = uiState.activeTabId!!

            val folderB = coordinator.createFolder(tab1Id, color = 1)
            advanceUntilIdle()

            coordinator.openNewTab("https://tab2.com")
            advanceUntilIdle()
            val tab2Id = uiState.activeTabId!!

            val folderA = coordinator.createFolder(tab2Id, color = 2)
            advanceUntilIdle()
            assertThat(uiState.activeTabId).isEqualTo(tab2Id)

            // Collapse folderB while tab2 in folderA is active
            coordinator.toggleFolderCollapse(folderB.id, fromDock = true)
            advanceUntilIdle()
            assertThat(uiState.getFolder(folderB.id)?.isCollapsed).isTrue()

            // Close folderA from dock when the only other tab (tab1) is inside collapsed folderB
            coordinator.toggleFolderCollapse(folderA.id, fromDock = true)
            advanceUntilIdle()

            assertThat(uiState.getFolder(folderA.id)?.isCollapsed).isTrue()
            assertThat(uiState.getFolder(folderB.id)?.isCollapsed).isTrue()
            assertThat(uiState.activeTabId).isNotEqualTo(tab1Id)
            assertThat(uiState.activeTabId).isNotEqualTo(tab2Id)
            val newActive = uiState.activeTab
            assertThat(newActive?.folderId).isNull()
            assertThat(newActive?.title).isEqualTo("Home")
        }
}
