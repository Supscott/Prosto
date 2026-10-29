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
}
