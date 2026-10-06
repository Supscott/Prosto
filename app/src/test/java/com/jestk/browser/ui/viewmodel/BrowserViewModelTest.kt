package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class BrowserViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var recentTabRepo: com.jestk.browser.domain.repository.RecentTabRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        recentTabRepo =
            com.jestk.browser.data.repository
                .RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel =
        BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            recentTabRepository = recentTabRepo,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun coldBoot_withEmptyDatabase_createsDefaultHomeTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeTabId).isNotNull()
            assertThat(state.currentUrl).isEmpty()
            assertThat(state.pageTitle).isEqualTo("Home")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.HOME)
        }

    @Test
    fun coldBoot_withExistingTabs_restoresActiveTab() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://jestk.com", title = "Jestk")
            val tab2 = tabRepo.createTab(url = "https://news.ycombinator.com", title = "Hacker News")
            tabRepo.setActiveTab(tab2.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isEqualTo(tab2.id)
            assertThat(state.currentUrl).isEqualTo("https://news.ycombinator.com")
            assertThat(state.pageTitle).isEqualTo("Hacker News")
        }

    @Test
    fun openNewTab_addsTabAndMakesItActive() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://kotlinlang.org"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.currentUrl).isEqualTo("https://kotlinlang.org")
        }

    @Test
    fun closeTab_whenMultipleTabs_switchesToAdjacentTab() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://jestk.com", title = "Jestk")
            val tab2 = tabRepo.createTab(url = "https://github.com", title = "GitHub")
            tabRepo.setActiveTab(tab1.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.CloseTab(tab1.id))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeTabId).isEqualTo(tab2.id)
        }

    @Test
    fun closeTab_whenOnTabsScreen_doesNotDismissTabsScreen() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://jestk.com", title = "Jestk")
            val tab2 = tabRepo.createTab(url = "https://github.com", title = "GitHub")
            tabRepo.setActiveTab(tab1.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenTabGrid)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.TAB_GRID)

            viewModel.onEvent(BrowserUiEvent.CloseTab(tab1.id))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeTabId).isEqualTo(tab2.id)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.TAB_GRID)
        }

    @Test
    fun closeTab_whenLastTabClosedOnTabsScreen_doesNotDismissTabsScreen() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://jestk.com", title = "Jestk")
            tabRepo.setActiveTab(tab1.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenTabGrid)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.TAB_GRID)

            viewModel.onEvent(BrowserUiEvent.CloseTab(tab1.id))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.TAB_GRID)
        }

    @Test
    fun loadUrl_withDomain_prependsHttps() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("jestk.com"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.currentUrl).isEqualTo("https://jestk.com")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun loadUrl_withQuery_formatsAsSearchEngineUrl() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("android compose"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.currentUrl).startsWith("https://duckduckgo.com/?q=")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun handleBackPress_hierarchyAdherence() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSettings)
            assertThat(viewModel.uiState.value.activeScreen).isEqualTo(ScreenType.SETTINGS)
            assertThat(viewModel.uiState.value.isSettingsVisible).isTrue()

            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            assertThat(viewModel.uiState.value.isSettingsVisible).isFalse()
        }

    @Test
    fun onWebScroll_maintainsBottomBarVisible() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // Scroll down
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(25f))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // Scroll up
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(-25f))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
        }

    @Test
    fun onWebScroll_withinFirstScreen_maintainsBottomBarVisible() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Open a webpage tab
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // Scroll down within first screen (scrollY = 400, viewportHeight = 800)
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 25f, scrollY = 400, viewportHeight = 800))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // Scroll down further within first screen (scrollY = 800, viewportHeight = 800)
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 25f, scrollY = 800, viewportHeight = 800))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
        }

    @Test
    fun onWebScroll_belowHScreen_hidesOnScrollDownAndShowsOnScrollUp() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Open a webpage tab
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()

            val viewportHeight = 800

            // 1. Scroll down below h-screen -> bottom bar hides
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 25f, scrollY = 1200, viewportHeight = viewportHeight))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

            // 2. Continuing downward scroll keeps it hidden
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 30f, scrollY = 1500, viewportHeight = viewportHeight))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

            // 3. Scroll up below h-screen -> bottom bar reappears
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = -25f, scrollY = 1450, viewportHeight = viewportHeight))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // 4. Scroll down again below h-screen -> bottom bar hides
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 20f, scrollY = 1500, viewportHeight = viewportHeight))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

            // 5. Scroll back up into top screen (scrollY <= viewportHeight) -> bottom bar reappears
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 0f, scrollY = 600, viewportHeight = viewportHeight))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
        }

    @Test
    fun onWebScroll_onHomeScreen_alwaysMaintainsBottomBarVisible() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isHome).isTrue()

            // Even if scrolled down below a simulated screen height, dock never hides on Home screen
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 50f, scrollY = 1500, viewportHeight = 800))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
        }

    @Test
    fun navigationEvents_resetBottomBarVisible() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Open a web page
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()

            val activeTabId = viewModel.uiState.value.activeTabId!!

            // Hide dock by scrolling down below h-screen
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 30f, scrollY = 1500, viewportHeight = 800))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

            // Page navigation resets visibility
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(activeTabId, "https://example.com/page2"))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

            // Hide dock again
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 30f, scrollY = 1500, viewportHeight = 800))
            assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

            // Opening a new tab resets visibility
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://another.com"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
        }

    @Test
    fun settingsEvents_updateConfiguration() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.SetThemeMode(ThemeMode.DARK))
            viewModel.onEvent(BrowserUiEvent.SetSearchEngine(SearchEngine.GOOGLE))
            viewModel.onEvent(BrowserUiEvent.SetAdBlockerEnabled(false))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.settings.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(state.settings.searchEngine).isEqualTo(SearchEngine.GOOGLE)
            assertThat(state.settings.isAdBlockerEnabled).isFalse()
        }

    @Test
    fun loadUrl_setsFallbackFaviconForDomain() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://en.wikipedia.org/wiki/Kotlin"))
            advanceUntilIdle()

            val activeTab = viewModel.uiState.value.activeTab
            assertThat(activeTab).isNotNull()
            // PRIV-01: Visited domains are not phone-homed to third-party Google CDN
            assertThat(activeTab?.faviconUrl).isNull()
        }

    @Test
    fun updateFavicon_updatesActiveTabFavicon() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_$activeId.png"))
            advanceUntilIdle()

            val activeTab = viewModel.uiState.value.activeTab
            assertThat(activeTab?.faviconUrl).isEqualTo("/data/user/0/favicons/favicon_$activeId.png")
        }

    @Test
    fun loadUrl_toDifferentDomain_resetsFaviconToNull() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://siteA.com"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_siteA.png"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTab?.faviconUrl).isEqualTo("/data/user/0/favicons/favicon_siteA.png")

            // Navigating to a different domain must immediately reset favicon to null to avoid stale icon retention
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://siteB.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTab?.faviconUrl).isNull()
        }

    @Test
    fun loadUrl_toSameDomain_preservesFavicon() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://siteA.com/page1"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_siteA.png"))
            advanceUntilIdle()

            // Navigating within the same domain must preserve the favicon smoothly
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://siteA.com/page2"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTab?.faviconUrl).isEqualTo("/data/user/0/favicons/favicon_siteA.png")
        }

    @Test
    fun onPageStarted_toDifferentDomain_resetsFaviconToNull() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_siteA.png"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OnPageStarted(activeId, "https://differentdomain.org"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTab?.faviconUrl).isNull()
        }

    @Test
    fun onPageStarted_toSameDomain_preservesFavicon() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://samedomain.org/articles"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_samedomain.png"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OnPageStarted(activeId, "https://samedomain.org/about"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTab?.faviconUrl).isEqualTo("/data/user/0/favicons/favicon_samedomain.png")
        }

    @Test
    fun addCurrentTabToFavorites_savesFavoriteWithFavicon() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "https://kotlinlang.org/icon.png"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.AddCurrentTabToFavorites)
            advanceUntilIdle()

            val favorites = viewModel.uiState.value.favorites
            assertThat(favorites).isNotEmpty()
            val fav = favorites.find { it.url == "https://kotlinlang.org" }
            assertThat(fav).isNotNull()
            assertThat(fav?.iconUrl).isEqualTo("https://kotlinlang.org/icon.png")
        }

    @Test
    fun updateFavicon_updatesFavoriteIconWhenUrlBookmarked() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.AddFavorite("Kotlin", "https://kotlinlang.org"))
            advanceUntilIdle()

            val activeId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "https://kotlinlang.org/new_icon.png"))
            advanceUntilIdle()

            val favorites = viewModel.uiState.value.favorites
            val fav = favorites.find { it.url == "https://kotlinlang.org" }
            assertThat(fav?.iconUrl).isEqualTo("https://kotlinlang.org/new_icon.png")
        }

    @Test
    fun handleBackPress_whenHistoryExhaustedOnWebsite_resetsToHome() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://kotlinlang.org"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://kotlinlang.org")

            // Back when history is exhausted on single website tab resets to Home
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.currentUrl).isEmpty()
            assertThat(state.pageTitle).isEqualTo("Home")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.HOME)
        }

    @Test
    fun handleBackPress_whenAlreadyOnCleanHomeTab_emitsExitApp() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Tab is already on clean home tab
            assertThat(viewModel.uiState.value.currentUrl).isEmpty()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.HOME)

            var exitAppReceived = false
            backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
                viewModel.navigationCommands.collect { command ->
                    if (command is NavigationCommand.ExitApp) {
                        exitAppReceived = true
                    }
                }
            }

            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            assertThat(exitAppReceived).isTrue()
        }

    @Test
    fun openRecentTab_whenAlreadyOpen_switchesToIt() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site1.com"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site2.com"))
            advanceUntilIdle()

            val item1 =
                com.jestk.browser.domain.model
                    .RecentTabItem(url = "https://site1.com", title = "Site 1")
            viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item1))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.currentUrl).isEqualTo("https://site1.com")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun openRecentTab_whenClosedAndCurrentIsHome_loadsUrlInCurrentTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Current active tab is clean Home tab
            assertThat(viewModel.uiState.value.currentUrl).isEmpty()

            val item =
                com.jestk.browser.domain.model
                    .RecentTabItem(url = "https://jestk.org", title = "Jestk")
            viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.currentUrl).isEqualTo("https://jestk.org")
        }

    @Test
    fun openRecentTab_whenClosedAndCurrentIsNonEmpty_opensNewTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://existing.com"))
            advanceUntilIdle()

            val item =
                com.jestk.browser.domain.model
                    .RecentTabItem(url = "https://newsite.com", title = "New")
            viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.currentUrl).isEqualTo("https://newsite.com")
        }

    @Test
    fun closingTab_doesNotRemoveFromRecentTabs() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Open a tab and finish page load
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://persistent.com"))
            advanceUntilIdle()

            val tabId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabId, "https://persistent.com", "Persistent Tab"))
            advanceUntilIdle()

            // Verify recentTabs contains this entry
            assertThat(
                viewModel.uiState.value.recentTabs
                    .any { it.url == "https://persistent.com" },
            ).isTrue()

            // Now close the tab
            viewModel.onEvent(BrowserUiEvent.CloseTab(tabId))
            advanceUntilIdle()

            // Tab is gone from active tabs, but still present in recentTabs!
            assertThat(
                viewModel.uiState.value.tabs
                    .any { it.id == tabId },
            ).isFalse()
            assertThat(
                viewModel.uiState.value.recentTabs
                    .any { it.url == "https://persistent.com" },
            ).isTrue()
        }

    @Test
    fun reorderTabs_updatesTabPositions() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://first.com"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://second.com"))
            advanceUntilIdle()

            val currentTabs = viewModel.uiState.value.tabs
            val reversed = currentTabs.reversed()

            viewModel.onEvent(BrowserUiEvent.ReorderTabs(reversed))
            advanceUntilIdle()

            val reorderedTabs = viewModel.uiState.value.tabs
            assertThat(reorderedTabs.first().id).isEqualTo(reversed.first().id)
            assertThat(reorderedTabs.last().id).isEqualTo(reversed.last().id)
        }

    @Test
    fun openSearch_whenOnHome_doesNotCreateNewTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Initially 1 tab on Home
            assertThat(viewModel.uiState.value.tabs).hasSize(1)
            val initialTabId = viewModel.uiState.value.activeTabId

            // When already on home, clicking search opens search overlay on current tab without creating new tab
            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeTabId).isEqualTo(initialTabId)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.SEARCH)
            assertThat(state.searchQuery).isEmpty()
            assertThat(state.currentUrl).isEmpty()
        }

    @Test
    fun openSearch_whenOnWebPage_createsNewTabAndOpensSearchOverlay() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(1)
            val initialTabId = viewModel.uiState.value.activeTabId

            // Clicking search while on a web page opens a new tab for search
            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isNotEqualTo(initialTabId)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.SEARCH)
            assertThat(state.searchQuery).isEmpty()
            assertThat(state.currentUrl).isEmpty()
        }

    @Test
    fun openSearch_withOpenInNewTabFalse_editsCurrentTabWithoutCreatingNewTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(1)

            // Clicking URL editor on current tab
            viewModel.onEvent(BrowserUiEvent.OpenSearch("https://example.com", openInNewTab = false))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.SEARCH)
            assertThat(state.searchQuery).isEqualTo("https://example.com")
        }

    @Test
    fun setFullscreenCustomView_updatesStateWithOrientation() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val fakeView = android.view.View(ApplicationProvider.getApplicationContext())
            viewModel.onEvent(
                BrowserUiEvent.SetFullscreenCustomView(
                    view = fakeView,
                    isFullscreen = true,
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isFullscreenCustomView).isTrue()
            assertThat(state.fullscreenCustomView).isSameInstanceAs(fakeView)
            assertThat(state.fullscreenRequestedOrientation).isEqualTo(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        }

    @Test
    fun handleBackPress_whenFullscreenCustomView_exitsFullscreen() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val fakeView = android.view.View(ApplicationProvider.getApplicationContext())
            viewModel.onEvent(
                BrowserUiEvent.SetFullscreenCustomView(
                    view = fakeView,
                    isFullscreen = true,
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                ),
            )
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isFullscreenCustomView).isTrue()

            // Pressing back in fullscreen must exit fullscreen without closing tab
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isFullscreenCustomView).isFalse()
            assertThat(state.fullscreenCustomView).isNull()
            assertThat(state.tabs).hasSize(1)
        }

    @Test
    fun openSearch_withEmptyStringPrefilledUrl_whenOnWebPage_createsNewTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(1)
            val initialTabId = viewModel.uiState.value.activeTabId

            // OpenSearch with empty string when on a web page should default openInNewTab to true
            viewModel.onEvent(BrowserUiEvent.OpenSearch(""))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isNotEqualTo(initialTabId)
            assertThat(state.activeOverlay).isEqualTo(OverlayType.SEARCH)
        }

    @Test
    fun menu_homeScreen_isNotAutoExpandedOnBootAndNavigation() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Home screen on boot should NOT have menu expanded
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()

            // Navigate to a web page -> menu should follow web preference (default false)
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()

            // Navigate back to Home -> menu should remain collapsed
            viewModel.onEvent(BrowserUiEvent.OpenHome)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()
        }

    @Test
    fun menu_toggleOnWebPage_persistsStateAndAppliesToNewPages() =
        runTest(testDispatcher) {
            val tab = tabRepo.createTab(url = "https://example.com", title = "Example")
            tabRepo.setActiveTab(tab.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Default on web page is closed
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()

            // User opens menu on web page
            viewModel.onEvent(BrowserUiEvent.ToggleMenu())
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()

            // User closes menu on web page
            viewModel.onEvent(BrowserUiEvent.ToggleMenu())
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()
        }

    @Test
    fun menu_webScrollDown_closesMenuOnWebPage() =
        runTest(testDispatcher) {
            val tab = tabRepo.createTab(url = "https://example.com", title = "Example")
            tabRepo.setActiveTab(tab.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.SetMenuExpanded(true))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()

            // Scrolling down web page should close menu
            viewModel.onEvent(BrowserUiEvent.OnWebScroll(deltaY = 15f))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()
        }

    @Test
    fun menu_handleBackPress_closesMenuOnWebPageFirst() =
        runTest(testDispatcher) {
            val tab = tabRepo.createTab(url = "https://example.com", title = "Example")
            tabRepo.setActiveTab(tab.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.SetMenuExpanded(true))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()

            // Back press closes menu before navigating back
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()
            assertThat(viewModel.uiState.value.tabs).hasSize(1)
        }

    @Test
    fun search_onActiveTabWithExistingUrl_updatesUrlAndDismissesOverlay() =
        runTest(testDispatcher) {
            val tab = tabRepo.createTab(url = "https://example.com", title = "Example")
            tabRepo.setActiveTab(tab.id)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // User taps active tab to open search overlay
            viewModel.onEvent(BrowserUiEvent.OpenSearch(tab.url))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.SEARCH)
            assertThat(viewModel.uiState.value.searchQuery).isEqualTo("https://example.com")

            // User types new query and submits
            viewModel.onEvent(BrowserUiEvent.SubmitSearch("https://newsite.org"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.currentUrl).isEqualTo("https://newsite.org")
            assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
            assertThat(state.tabs.find { it.id == tab.id }?.url).isEqualTo("https://newsite.org")
            assertThat(state.activeThemeColor).isNull()
            assertThat(state.isVideoPlaying).isFalse()
        }

    @Test
    fun search_blankQuery_populatesRecentHistorySuggestions() =
        runTest(testDispatcher) {
            historyRepo.recordVisit("https://apple.com", "Apple")
            historyRepo.recordVisit("https://google.com", "Google")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()

            val suggestions = viewModel.uiState.value.searchHistorySuggestions
            assertThat(suggestions).hasSize(2)
            assertThat(suggestions.map { it.url }).containsExactly("https://google.com", "https://apple.com").inOrder()
        }

    @Test
    fun search_queryWithFilter_filtersHistorySuggestions() =
        runTest(testDispatcher) {
            historyRepo.recordVisit("https://apple.com", "Apple")
            historyRepo.recordVisit("https://google.com", "Google")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.UpdateSearchQuery("app"))
            advanceUntilIdle()

            val suggestions = viewModel.uiState.value.searchHistorySuggestions
            assertThat(suggestions).hasSize(1)
            assertThat(suggestions.first().title).isEqualTo("Apple")
        }

    @Test
    fun search_clearAllHistory_clearsHistoryAndSuggestions() =
        runTest(testDispatcher) {
            historyRepo.recordVisit("https://apple.com", "Apple")
            historyRepo.recordVisit("https://google.com", "Google")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(2)

            viewModel.onEvent(BrowserUiEvent.ClearAllHistory)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.searchHistorySuggestions).isEmpty()
        }

    @Test
    fun search_toggleSearchHistoryExpanded_updatesStateAndLoadsAllHistory() =
        runTest(testDispatcher) {
            for (i in 1..15) {
                historyRepo.recordVisit("https://site$i.com", "Site $i")
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isFalse()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(10)

            viewModel.onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isTrue()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(15)

            viewModel.onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isFalse()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(10)
        }

    @Test
    fun search_expandedHistory_backPressCollapsesHistoryFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.SEARCH)

            viewModel.onEvent(BrowserUiEvent.SetSearchHistoryExpanded(true))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isTrue()

            // First back press collapses history expansion
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isFalse()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.SEARCH)

            // Second back press closes search overlay
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun search_expandedHistory_paginationLoadsNextPageOnLoadMore() =
        runTest(testDispatcher) {
            for (i in 1..45) {
                historyRepo.recordVisit("https://site$i.com", "Site $i")
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isFalse()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(10)

            // Expanding history loads first page of 20 items
            viewModel.onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isTrue()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(20)
            assertThat(viewModel.uiState.value.hasMoreHistory).isTrue()
            assertThat(viewModel.uiState.value.isLoadingMoreHistory).isFalse()

            // Trigger LoadMoreSearchHistory loads page 2 (40 items total)
            viewModel.onEvent(BrowserUiEvent.LoadMoreSearchHistory)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(40)
            assertThat(viewModel.uiState.value.hasMoreHistory).isTrue()
            assertThat(viewModel.uiState.value.isLoadingMoreHistory).isFalse()

            // Trigger LoadMoreSearchHistory loads page 3 (remaining 5 items -> 45 total)
            viewModel.onEvent(BrowserUiEvent.LoadMoreSearchHistory)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(45)
            assertThat(viewModel.uiState.value.hasMoreHistory).isFalse()
            assertThat(viewModel.uiState.value.isLoadingMoreHistory).isFalse()

            // Trigger LoadMoreSearchHistory when hasMoreHistory == false does nothing
            viewModel.onEvent(BrowserUiEvent.LoadMoreSearchHistory)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(45)

            // Collapsing history restores recent 10 items
            viewModel.onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isFalse()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(10)
        }

    @Test
    fun search_expandedHistory_queryFilteringPagination() =
        runTest(testDispatcher) {
            for (i in 1..30) {
                historyRepo.recordVisit("https://site$i.com/android", "Android Topic $i")
            }
            for (i in 1..10) {
                historyRepo.recordVisit("https://other$i.com/web", "Other Site $i")
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.UpdateSearchQuery("Android"))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.SetSearchHistoryExpanded(true))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isSearchHistoryExpanded).isTrue()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(20)
            assertThat(viewModel.uiState.value.hasMoreHistory).isTrue()

            viewModel.onEvent(BrowserUiEvent.LoadMoreSearchHistory)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.searchHistorySuggestions).hasSize(30)
            assertThat(viewModel.uiState.value.hasMoreHistory).isFalse()
        }

    @Test
    fun linkContextMenu_showAndDismiss_updatesUiState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.linkContextMenuUrl).isNull()

            val testUrl = "https://example.com/target"
            viewModel.onEvent(BrowserUiEvent.ShowLinkContextMenu(testUrl, anchorX = 250f, anchorY = 400f))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.linkContextMenuUrl).isEqualTo(testUrl)
            assertThat(viewModel.uiState.value.contextMenuAnchor).isEqualTo(Offset(250f, 400f))

            viewModel.onEvent(BrowserUiEvent.DismissLinkContextMenu)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.linkContextMenuUrl).isNull()
            assertThat(viewModel.uiState.value.contextMenuAnchor).isNull()
        }

    @Test
    fun linkContextMenu_openInNewTab_createsAndSelectsTabAndClearsMenu() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val originalActiveTabId = viewModel.uiState.value.activeTabId
            val targetUrl = "https://example.com/new-active"

            viewModel.onEvent(BrowserUiEvent.ShowLinkContextMenu(targetUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.linkContextMenuUrl).isEqualTo(targetUrl)

            viewModel.onEvent(BrowserUiEvent.OpenNewTab(targetUrl))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.linkContextMenuUrl).isNull()
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isNotEqualTo(originalActiveTabId)
            assertThat(state.currentUrl).isEqualTo(targetUrl)
            assertThat(state.activeTab?.url).isEqualTo(targetUrl)
            assertThat(state.activeTab?.isActive).isTrue()
        }

    @Test
    fun linkContextMenu_openTabInBackground_createsTabWithoutSwitchingAndClearsMenu() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val originalActiveTabId = viewModel.uiState.value.activeTabId
            val originalUrl = viewModel.uiState.value.currentUrl
            val bgUrl = "https://example.com/background"

            viewModel.onEvent(BrowserUiEvent.ShowLinkContextMenu(bgUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.linkContextMenuUrl).isEqualTo(bgUrl)

            viewModel.onEvent(BrowserUiEvent.OpenTabInBackground(bgUrl))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.linkContextMenuUrl).isNull()
            assertThat(state.tabs).hasSize(2)
            // Screen remains on the original tab and URL
            assertThat(state.activeTabId).isEqualTo(originalActiveTabId)
            assertThat(state.currentUrl).isEqualTo(originalUrl)

            // Background tab was created
            val bgTab = state.tabs.firstOrNull { it.url == bgUrl }
            assertThat(bgTab).isNotNull()
            assertThat(bgTab?.isActive).isFalse()
        }

    @Test
    fun linkContextMenu_backPress_dismissesMenuFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val targetUrl = "https://example.com/back-test"
            viewModel.onEvent(BrowserUiEvent.ShowLinkContextMenu(targetUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.linkContextMenuUrl).isEqualTo(targetUrl)

            // Press back while context menu is open
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            // Context menu is dismissed, but tab count is unchanged
            assertThat(viewModel.uiState.value.linkContextMenuUrl).isNull()
            assertThat(viewModel.uiState.value.tabs).hasSize(1)
        }

    @Test
    fun switchAdjacentTab_whenSingleOrEmptyTabs_doesNotThrow() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(1)
            // Should not throw IllegalArgumentException when lastIndex <= 0
            viewModel.onEvent(BrowserUiEvent.SwipeNextTab)
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.SwipePrevTab)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(1)
        }

    @Test
    fun openNewTab_immediatelyOnRestore_mergesCleanlyWithoutCorruption() =
        runTest(testDispatcher) {
            tabRepo.createTab(url = "https://example.com/1", title = "Tab 1")
            tabRepo.createTab(url = "https://example.com/2", title = "Tab 2")

            val viewModel = createViewModel()
            // Immediately invoke OpenNewTab before advanceUntilIdle to simulate user tapping new tab upon restore
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com/immediate"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs.size).isAtLeast(3)
            assertThat(state.currentUrl).isEqualTo("https://example.com/immediate")
            assertThat(state.activeTab?.url).isEqualTo("https://example.com/immediate")
            assertThat(state.activeTab?.isActive).isTrue()
        }

    @Test
    fun clearAllHistory_clearsRecentTabsInMemoryAndRepository() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            recentTabRepo.recordRecentTab("https://example.com/recent", "Recent")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.recentTabs).isNotEmpty()

            viewModel.onEvent(BrowserUiEvent.ClearAllHistory)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.recentTabs).isEmpty()
        }

    @Test
    fun closeTab_savesRecentlyClosedTabAndShowsSnackbar() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com/tab2"))
            advanceUntilIdle()

            val stateBeforeClose = viewModel.uiState.value
            assertThat(stateBeforeClose.tabs).hasSize(2)
            val tabToClose = stateBeforeClose.activeTab!!

            viewModel.onEvent(BrowserUiEvent.CloseTab(tabToClose.id))
            advanceUntilIdle()

            val stateAfterClose = viewModel.uiState.value
            assertThat(stateAfterClose.tabs).hasSize(1)
            assertThat(stateAfterClose.recentlyClosedTab?.url).isEqualTo("https://example.com/tab2")
            assertThat(stateAfterClose.showUndoTabCloseSnackbar).isTrue()
        }

    @Test
    fun undoCloseTab_restoresTabAndDismissesSnackbar() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com/tab-to-restore"))
            advanceUntilIdle()

            val tabToClose = viewModel.uiState.value.activeTab!!
            viewModel.onEvent(BrowserUiEvent.CloseTab(tabToClose.id))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showUndoTabCloseSnackbar).isTrue()
            assertThat(
                viewModel.uiState.value.recentlyClosedTab
                    ?.url,
            ).isEqualTo("https://example.com/tab-to-restore")

            viewModel.onEvent(BrowserUiEvent.UndoCloseTab)
            advanceUntilIdle()

            val restoredState = viewModel.uiState.value
            assertThat(restoredState.showUndoTabCloseSnackbar).isFalse()
            assertThat(restoredState.recentlyClosedTab).isNull()
            assertThat(restoredState.tabs).hasSize(2)
            assertThat(restoredState.currentUrl).isEqualTo("https://example.com/tab-to-restore")
            assertThat(restoredState.activeTab?.url).isEqualTo("https://example.com/tab-to-restore")
            assertThat(restoredState.activeTab?.isActive).isTrue()
        }

    @Test
    fun dismissUndoTabCloseSnackbar_clearsRecentlyClosedTabAndHidesSnackbar() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com/dismiss-test"))
            advanceUntilIdle()

            val tabToClose = viewModel.uiState.value.activeTab!!
            viewModel.onEvent(BrowserUiEvent.CloseTab(tabToClose.id))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showUndoTabCloseSnackbar).isTrue()
            assertThat(viewModel.uiState.value.recentlyClosedTab).isNotNull()

            viewModel.onEvent(BrowserUiEvent.DismissUndoTabCloseSnackbar)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showUndoTabCloseSnackbar).isFalse()
            assertThat(viewModel.uiState.value.recentlyClosedTab).isNull()
        }

    @Test
    fun webViewPool_wiredToViewModelLifecycle() =
        runTest(testDispatcher) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val webViewPool =
                com.jestk.browser.engine
                    .WebViewPool(context, null)
            val viewModel =
                BrowserViewModel(
                    tabRepository = tabRepo,
                    historyRepository = historyRepo,
                    favoriteRepository = favoriteRepo,
                    settingsRepository = settingsRepo,
                    recentTabRepository = recentTabRepo,
                    webViewPool = webViewPool,
                    ioDispatcher = testDispatcher,
                )
            advanceUntilIdle()

            // 1. Verify download listener is wired
            assertThat(webViewPool.onDownloadStarted).isNotNull()
            val downloadItem =
                com.jestk.browser.domain.model.DownloadItem(
                    url = "https://example.com/file.zip",
                    fileName = "file.zip",
                    filePath = "/path/file.zip",
                    mimeType = "application/zip",
                    contentLength = 1024L,
                    timestamp = System.currentTimeMillis(),
                )
            webViewPool.onDownloadStarted?.invoke(downloadItem)
            assertThat(
                viewModel.uiState.value.lastDownloadNotification
                    ?.fileName,
            ).isEqualTo("file.zip")
            assertThat(viewModel.uiState.value.showDownloadSnackbar).isTrue()

            // 2. Create WebView in pool for active tab
            val state = viewModel.uiState.value
            val tabId = state.activeTabId!!
            val webView = webViewPool.getOrCreateWebView(tabId, context)
            assertThat(webViewPool.getWebView(tabId)).isEqualTo(webView)

            // 3. Close tab removes from WebViewPool
            viewModel.onEvent(BrowserUiEvent.CloseTab(tabId))
            advanceUntilIdle()
            assertThat(webViewPool.getWebView(tabId)).isNull()

            // 4. Close all tabs clears WebViewPool
            val newActiveTabId = viewModel.uiState.value.activeTabId!!
            webViewPool.getOrCreateWebView(newActiveTabId, context)
            assertThat(webViewPool.getWebView(newActiveTabId)).isNotNull()

            viewModel.onEvent(BrowserUiEvent.CloseAllTabs)
            advanceUntilIdle()
            assertThat(webViewPool.getWebView(newActiveTabId)).isNull()
        }

    @Test
    fun pageStartedAndFinished_ignoresAboutBlank() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val activeTabId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://example.com")
            assertThat(viewModel.uiState.value.isHome).isFalse()

            // When about:blank is received from engine, it should be ignored
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(activeTabId, "about:blank"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://example.com")
            assertThat(viewModel.uiState.value.isHome).isFalse()

            viewModel.onEvent(BrowserUiEvent.OnPageFinished(activeTabId, "about:blank", ""))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://example.com")
            assertThat(viewModel.uiState.value.isHome).isFalse()
        }

    @Test
    fun browserUiState_isHome_evaluatesCorrectly() {
        // Overlay is HOME
        val state1 = BrowserUiState(activeOverlay = OverlayType.HOME)
        assertThat(state1.isHome).isTrue()

        // Active tab has valid URL, currentUrl is loaded
        val tab = Tab(id = "1", url = "https://example.com", title = "Example")
        val state2 =
            BrowserUiState(
                activeOverlay = OverlayType.NONE,
                activeTabId = "1",
                tabs = listOf(tab),
                currentUrl = "https://example.com",
            )
        assertThat(state2.isHome).isFalse()

        // Active tab has valid URL, but currentUrl is temporarily empty during navigation transition
        val state3 =
            BrowserUiState(
                activeOverlay = OverlayType.NONE,
                activeTabId = "1",
                tabs = listOf(tab),
                currentUrl = "",
            )
        assertThat(state3.isHome).isFalse()

        // Search overlay is open
        val state4 = BrowserUiState(activeOverlay = OverlayType.SEARCH)
        assertThat(state4.isHome).isFalse()

        // Blank tab on home
        val blankTab = Tab(id = "2", url = "", title = "Home")
        val state5 =
            BrowserUiState(
                activeOverlay = OverlayType.NONE,
                activeTabId = "2",
                tabs = listOf(blankTab),
                currentUrl = "",
            )
        assertThat(state5.isHome).isTrue()
    }

    @Test
    fun imageContextMenu_showAndDismiss_updatesUiState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedImageUrl).isNull()

            val imageUrl = "https://example.com/photo.jpg"
            val targetLink = "https://example.com/target-link"
            viewModel.onEvent(
                BrowserUiEvent.ShowImageContextMenu(
                    imageUrl = imageUrl,
                    linkUrl = targetLink,
                    anchorX = 350f,
                    anchorY = 550f,
                )
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedImageUrl).isEqualTo(imageUrl)
            assertThat(viewModel.uiState.value.imageContextMenuLinkUrl).isEqualTo(targetLink)
            assertThat(viewModel.uiState.value.contextMenuAnchor).isEqualTo(Offset(350f, 550f))

            viewModel.onEvent(BrowserUiEvent.DismissImageContextMenu)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedImageUrl).isNull()
            assertThat(viewModel.uiState.value.imageContextMenuLinkUrl).isNull()
            assertThat(viewModel.uiState.value.contextMenuAnchor).isNull()
        }

    @Test
    fun imageContextMenu_previewInNewTab_createsAndSelectsTabAndClearsModal() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val originalActiveTabId = viewModel.uiState.value.activeTabId
            val imageUrl = "https://example.com/gallery/fullsize.png"

            viewModel.onEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedImageUrl).isEqualTo(imageUrl)

            viewModel.onEvent(BrowserUiEvent.PreviewImageInNewTab(imageUrl))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.selectedImageUrl).isNull()
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isNotEqualTo(originalActiveTabId)
            assertThat(state.currentUrl).isEqualTo(imageUrl)
            assertThat(state.activeTab?.url).isEqualTo(imageUrl)
            assertThat(state.activeTab?.isActive).isTrue()
            assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
        }

    @Test
    fun imageContextMenu_copyImageUrl_clearsModal() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val imageUrl = "https://example.com/art.jpg"
            viewModel.onEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedImageUrl).isEqualTo(imageUrl)

            viewModel.onEvent(BrowserUiEvent.CopyImageUrl(imageUrl))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedImageUrl).isNull()
        }

    @Test
    fun imageContextMenu_downloadImage_clearsModal() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val imageUrl = "https://example.com/wallpaper.webp"
            viewModel.onEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedImageUrl).isEqualTo(imageUrl)

            viewModel.onEvent(BrowserUiEvent.DownloadImage(imageUrl))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedImageUrl).isNull()
        }

    @Test
    fun imageContextMenu_backPress_dismissesMenuFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val imageUrl = "https://example.com/backpress-test.png"
            viewModel.onEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl, anchorX = 200f, anchorY = 300f))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedImageUrl).isEqualTo(imageUrl)
            assertThat(viewModel.uiState.value.contextMenuAnchor).isEqualTo(Offset(200f, 300f))

            // Press back while image context menu is open
            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            // Context menu is dismissed, but tabs remain intact
            assertThat(viewModel.uiState.value.selectedImageUrl).isNull()
            assertThat(viewModel.uiState.value.contextMenuAnchor).isNull()
            assertThat(viewModel.uiState.value.tabs).hasSize(1)
        }

    @Test
    fun linkContextMenu_withAnchor_backPress_dismissesMenuFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val linkUrl = "https://example.com/link-backpress"
            viewModel.onEvent(BrowserUiEvent.ShowLinkContextMenu(linkUrl, anchorX = 150f, anchorY = 250f))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.linkContextMenuUrl).isEqualTo(linkUrl)
            assertThat(viewModel.uiState.value.contextMenuAnchor).isEqualTo(Offset(150f, 250f))

            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.linkContextMenuUrl).isNull()
            assertThat(viewModel.uiState.value.contextMenuAnchor).isNull()
        }

    @Test
    fun closeTab_whenActiveTabClosed_selectsLeftAdjacentTab() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://example.com/1", title = "Tab 1")
            val tab2 = tabRepo.createTab(url = "https://example.com/2", title = "Tab 2")
            val tab3 = tabRepo.createTab(url = "https://example.com/3", title = "Tab 3")

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Select tab 2 (middle tab)
            viewModel.onEvent(BrowserUiEvent.SelectTab(tab2.id))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab2.id)

            // Close active middle tab -> should select tab1 (to the left)
            viewModel.onEvent(BrowserUiEvent.CloseTab(tab2.id))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab1.id)
        }

    @Test
    fun closeTab_whenFirstTabClosed_selectsRightAdjacentTab() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://example.com/1", title = "Tab 1")
            val tab2 = tabRepo.createTab(url = "https://example.com/2", title = "Tab 2")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.SelectTab(tab1.id))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab1.id)

            // Close active leftmost tab -> should select tab2 (to the right)
            viewModel.onEvent(BrowserUiEvent.CloseTab(tab1.id))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab2.id)
        }

    @Test
    fun closeTab_whenInactiveTabClosed_keepsActiveTabUnchanged() =
        runTest(testDispatcher) {
            val tab1 = tabRepo.createTab(url = "https://example.com/1", title = "Tab 1")
            val tab2 = tabRepo.createTab(url = "https://example.com/2", title = "Tab 2")
            val tab3 = tabRepo.createTab(url = "https://example.com/3", title = "Tab 3")

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Select tab 3
            viewModel.onEvent(BrowserUiEvent.SelectTab(tab3.id))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab3.id)

            // Close inactive tab 1
            viewModel.onEvent(BrowserUiEvent.CloseTab(tab1.id))
            advanceUntilIdle()

            // Active tab should still be tab 3!
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab3.id)
            assertThat(
                viewModel.uiState.value.tabs
                    .map { it.id },
            ).containsExactly(tab2.id, tab3.id)
        }

    @Test
    fun showAddFavoriteDialog_updatesState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showAddFavoriteDialog).isFalse()

            viewModel.onEvent(BrowserUiEvent.ShowAddFavoriteDialog)
            assertThat(viewModel.uiState.value.showAddFavoriteDialog).isTrue()

            viewModel.onEvent(BrowserUiEvent.DismissAddFavoriteDialog)
            assertThat(viewModel.uiState.value.showAddFavoriteDialog).isFalse()
        }

    @Test
    fun handleBackPress_dismissesAddFavoriteDialogFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.ShowAddFavoriteDialog)
            assertThat(viewModel.uiState.value.showAddFavoriteDialog).isTrue()

            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showAddFavoriteDialog).isFalse()
        }

    @Test
    fun addFavorite_normalizesUrlAndDerivesTitleWhenBlank() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.AddFavorite(title = "", url = "github.com/kotlin"))
            advanceUntilIdle()

            val list = favoriteRepo.getFavorites().first()
            assertThat(list).hasSize(1)
            assertThat(list[0].url).isEqualTo("https://github.com/kotlin")
            assertThat(list[0].title).isEqualTo("github.com")
        }

    @Test
    fun searchPageMatch_onSearchPage_returnsEngineAndQuery() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://www.google.com/search?q=apple"))
            advanceUntilIdle()

            val match = viewModel.uiState.value.searchPageMatch
            assertThat(match).isNotNull()
            assertThat(match?.first).isEqualTo(SearchEngine.GOOGLE)
            assertThat(match?.second).isEqualTo("apple")
        }

    @Test
    fun searchPageMatch_onHomePage_returnsNull() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isHome).isTrue()
            assertThat(viewModel.uiState.value.searchPageMatch).isNull()
        }

    @Test
    fun searchPageMatch_onRegularWebPage_returnsNull() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.searchPageMatch).isNull()
        }

    @Test
    fun switchSearchEngine_whenNoExistingTab_opensNewTabWithSearchUrl() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://www.google.com/search?q=apple"))
            advanceUntilIdle()

            val initialTabId = viewModel.uiState.value.activeTabId
            assertThat(viewModel.uiState.value.tabs).hasSize(1)

            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.DUCKDUCKGO,
                    query = "apple"
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isNotEqualTo(initialTabId)
            assertThat(state.currentUrl).isEqualTo("https://duckduckgo.com/?q=apple")
            assertThat(state.searchPageMatch?.first).isEqualTo(SearchEngine.DUCKDUCKGO)
            assertThat(state.searchPageMatch?.second).isEqualTo("apple")
        }

    @Test
    fun switchSearchEngine_whenExistingTabMatches_switchesToExistingTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 1. Start on Google search "apple" (tab 1)
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://www.google.com/search?q=apple"))
            advanceUntilIdle()
            val googleTabId = viewModel.uiState.value.activeTabId

            // 2. Switch to DuckDuckGo search "apple" (creates tab 2)
            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.DUCKDUCKGO,
                    query = "apple"
                )
            )
            advanceUntilIdle()

            val ddgTabId = viewModel.uiState.value.activeTabId
            assertThat(viewModel.uiState.value.tabs).hasSize(2)
            assertThat(ddgTabId).isNotEqualTo(googleTabId)

            // 3. From tab 2, switch back to Google search "apple"
            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.GOOGLE,
                    query = "apple"
                )
            )
            advanceUntilIdle()

            // Should switch back to tab 1 without creating tab 3!
            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(2)
            assertThat(state.activeTabId).isEqualTo(googleTabId)
            assertThat(state.currentUrl).isEqualTo("https://www.google.com/search?q=apple")
            assertThat(state.searchPageMatch?.first).isEqualTo(SearchEngine.GOOGLE)
        }

    @Test
    fun switchSearchEngine_whenClickingAlreadySelectedEngine_doesNothing() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://www.google.com/search?q=apple"))
            advanceUntilIdle()

            val tabId = viewModel.uiState.value.activeTabId
            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.GOOGLE,
                    query = "apple"
                )
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tabs).hasSize(1)
            assertThat(state.activeTabId).isEqualTo(tabId)
        }

    @Test
    fun switchSearchEngine_caseInsensitiveQueryMatch() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Tab 1: Google with query "Apple"
            viewModel.onEvent(BrowserUiEvent.LoadUrl("https://www.google.com/search?q=Apple"))
            advanceUntilIdle()
            val googleTabId = viewModel.uiState.value.activeTabId

            // Switch to DuckDuckGo with query "apple" (creates Tab 2)
            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.DUCKDUCKGO,
                    query = "apple"
                )
            )
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.tabs).hasSize(2)

            // Now switch to Google with query "apple" -> should match "Apple" case-insensitively
            viewModel.onEvent(
                BrowserUiEvent.SwitchSearchEngineForQuery(
                    targetEngine = SearchEngine.GOOGLE,
                    query = "apple"
                )
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.tabs).hasSize(2)
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(googleTabId)
        }

    @Test
    fun blockedStats_flowUpdatesUiState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.blockedAdsCount).isEqualTo(0L)
        assertThat(viewModel.uiState.value.blockedTrackersCount).isEqualTo(0L)

        viewModel.incrementBlockedAds(5)
        viewModel.incrementBlockedTrackers(3)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.blockedAdsCount).isEqualTo(5L)
        assertThat(viewModel.uiState.value.blockedTrackersCount).isEqualTo(3L)
    }

    @Test
    fun tabSwitching_whenTabAIsLoadingAndSwitchToTabB_isLoadingBecomesFalseImmediately() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Open Tab A
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-a.com"))
            advanceUntilIdle()
            val tabAId = viewModel.uiState.value.activeTabId!!

            // Tab A begins loading
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabAId, "https://tab-a.com"))
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 45))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isTrue()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(0.45f)

            // Open Tab B and complete its loading
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-b.com"))
            advanceUntilIdle()
            val tabBId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabBId, "https://tab-b.com"))
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabBId, 100))
            viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabBId, "https://tab-b.com", "Tab B"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(1.0f)

            // Make Tab A active and verify it was loading
            viewModel.onEvent(BrowserUiEvent.SelectTab(tabAId))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isLoading).isTrue()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(0.45f)

            // Switch to Tab B (loaded): isLoading becomes false immediately without leaking Tab A's progress
            viewModel.onEvent(BrowserUiEvent.SelectTab(tabBId))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tabBId)
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(1.0f)
        }

    @Test
    fun backgroundTabCompletion_updatesProgressAndSwitchingBackShowsNotLoading() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-a.com"))
            advanceUntilIdle()
            val tabAId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabAId, "https://tab-a.com"))
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 30))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-b.com"))
            advanceUntilIdle()
            val tabBId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabBId, "https://tab-b.com", "Tab B"))
            advanceUntilIdle()

            // Verify Tab A is still tracked as loading at 30
            assertThat(viewModel.tabCoordinator.tabLoadingProgress[tabAId]).isEqualTo(30)
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tabBId)
            assertThat(viewModel.uiState.value.isLoading).isFalse()

            // Tab A finishes in background while Tab B is active
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 100))
            viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabAId, "https://tab-a.com", "Tab A"))
            advanceUntilIdle()

            // Verify Tab A's progress in tabLoadingProgress is updated to 100
            assertThat(viewModel.tabCoordinator.tabLoadingProgress[tabAId]).isEqualTo(100)
            // Active tab (Tab B) state remains unchanged
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(1.0f)

            // Switch back to Tab A: shows isLoading = false
            viewModel.onEvent(BrowserUiEvent.SelectTab(tabAId))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tabAId)
            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(1.0f)
        }

    @Test
    fun switchingBackToTabA_whileStillLoading_restoresLoadingState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-a.com"))
            advanceUntilIdle()
            val tabAId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabAId, "https://tab-a.com"))
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 60))
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-b.com"))
            advanceUntilIdle()
            val tabBId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabBId, "https://tab-b.com", "Tab B"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()

            // While Tab B is active, Tab A's progress advances to 80 in background
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 80))
            advanceUntilIdle()
            assertThat(viewModel.tabCoordinator.tabLoadingProgress[tabAId]).isEqualTo(80)

            // Switch back to Tab A: correctly restores Tab A's loading state at 80%
            viewModel.onEvent(BrowserUiEvent.SelectTab(tabAId))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tabAId)
            assertThat(viewModel.uiState.value.isLoading).isTrue()
            assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(0.80f)
        }

    @Test
    fun closeTab_cleansUpTabLoadingProgress() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-a.com"))
            advanceUntilIdle()
            val tabAId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabAId, "https://tab-a.com"))
            advanceUntilIdle()

            assertThat(viewModel.tabCoordinator.tabLoadingProgress).containsKey(tabAId)

            viewModel.onEvent(BrowserUiEvent.CloseTab(tabAId))
            advanceUntilIdle()

            assertThat(viewModel.tabCoordinator.tabLoadingProgress).doesNotContainKey(tabAId)
        }

    @Test
    fun stopLoading_updatesTabLoadingProgressAndState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-a.com"))
            advanceUntilIdle()
            val tabAId = viewModel.uiState.value.activeTabId!!
            viewModel.onEvent(BrowserUiEvent.OnPageStarted(tabAId, "https://tab-a.com"))
            viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tabAId, 40))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isTrue()

            viewModel.onEvent(BrowserUiEvent.StopLoading)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.tabCoordinator.tabLoadingProgress[tabAId]).isEqualTo(100)

            // Open Tab B and switch back to Tab A
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab-b.com"))
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.SelectTab(tabAId))
            advanceUntilIdle()

            // Tab A remains stopped (not loading)
            assertThat(viewModel.uiState.value.isLoading).isFalse()
        }
}


