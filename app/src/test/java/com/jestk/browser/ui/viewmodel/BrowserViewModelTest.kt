package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        recentTabRepo = com.jestk.browser.data.repository.RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel {
        return BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            recentTabRepository = recentTabRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun coldBoot_withEmptyDatabase_createsDefaultHomeTab() = runTest(testDispatcher) {
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
    fun coldBoot_withExistingTabs_restoresActiveTab() = runTest(testDispatcher) {
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
    fun openNewTab_addsTabAndMakesItActive() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://kotlinlang.org"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(2)
        assertThat(state.currentUrl).isEqualTo("https://kotlinlang.org")
    }

    @Test
    fun closeTab_whenMultipleTabs_switchesToAdjacentTab() = runTest(testDispatcher) {
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
    fun closeTab_whenOnTabsScreen_doesNotDismissTabsScreen() = runTest(testDispatcher) {
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
    fun closeTab_whenLastTabClosedOnTabsScreen_doesNotDismissTabsScreen() = runTest(testDispatcher) {
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
    fun loadUrl_withDomain_prependsHttps() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("jestk.com"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.currentUrl).isEqualTo("https://jestk.com")
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
    }

    @Test
    fun loadUrl_withQuery_formatsAsSearchEngineUrl() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("android compose"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.currentUrl).startsWith("https://duckduckgo.com/?q=")
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
    }

    @Test
    fun handleBackPress_hierarchyAdherence() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. When overlay is open -> dismiss overlay
        viewModel.onEvent(BrowserUiEvent.OpenOverlay(OverlayType.SETTINGS))
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.SETTINGS)

        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.NONE)
    }

    @Test
    fun onWebScroll_togglesBottomBarVisibility() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

        // Scroll down
        viewModel.onEvent(BrowserUiEvent.OnWebScroll(25f))
        assertThat(viewModel.uiState.value.isBottomBarVisible).isFalse()

        // Scroll up
        viewModel.onEvent(BrowserUiEvent.OnWebScroll(-25f))
        assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
    }

    @Test
    fun settingsEvents_updateConfiguration() = runTest(testDispatcher) {
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
    fun loadUrl_setsFallbackFaviconForDomain() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("https://en.wikipedia.org/wiki/Kotlin"))
        advanceUntilIdle()

        val activeTab = viewModel.uiState.value.activeTab
        assertThat(activeTab).isNotNull()
        assertThat(activeTab?.faviconUrl).contains("en.wikipedia.org")
    }

    @Test
    fun updateFavicon_updatesActiveTabFavicon() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val activeId = viewModel.uiState.value.activeTabId!!
        viewModel.onEvent(BrowserUiEvent.UpdateFaviconUrl(activeId, "/data/user/0/favicons/favicon_$activeId.png"))
        advanceUntilIdle()

        val activeTab = viewModel.uiState.value.activeTab
        assertThat(activeTab?.faviconUrl).isEqualTo("/data/user/0/favicons/favicon_$activeId.png")
    }

    @Test
    fun handleBackPress_whenHistoryExhaustedOnWebsite_resetsToHome() = runTest(testDispatcher) {
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
    fun handleBackPress_whenAlreadyOnCleanHomeTab_emitsExitApp() = runTest(testDispatcher) {
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
    fun openRecentTab_whenAlreadyOpen_switchesToIt() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site1.com"))
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site2.com"))
        advanceUntilIdle()

        val item1 = com.jestk.browser.domain.model.RecentTabItem(url = "https://site1.com", title = "Site 1")
        viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.currentUrl).isEqualTo("https://site1.com")
        assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
    }

    @Test
    fun openRecentTab_whenClosedAndCurrentIsHome_loadsUrlInCurrentTab() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Current active tab is clean Home tab
        assertThat(viewModel.uiState.value.currentUrl).isEmpty()

        val item = com.jestk.browser.domain.model.RecentTabItem(url = "https://jestk.org", title = "Jestk")
        viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.currentUrl).isEqualTo("https://jestk.org")
    }

    @Test
    fun openRecentTab_whenClosedAndCurrentIsNonEmpty_opensNewTab() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(BrowserUiEvent.LoadUrl("https://existing.com"))
        advanceUntilIdle()

        val item = com.jestk.browser.domain.model.RecentTabItem(url = "https://newsite.com", title = "New")
        viewModel.onEvent(BrowserUiEvent.OpenRecentTab(item))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(2)
        assertThat(state.currentUrl).isEqualTo("https://newsite.com")
    }

    @Test
    fun closingTab_doesNotRemoveFromRecentTabs() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Open a tab and finish page load
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://persistent.com"))
        advanceUntilIdle()

        val tabId = viewModel.uiState.value.activeTabId!!
        viewModel.onEvent(BrowserUiEvent.OnPageFinished(tabId, "https://persistent.com", "Persistent Tab"))
        advanceUntilIdle()

        // Verify recentTabs contains this entry
        assertThat(viewModel.uiState.value.recentTabs.any { it.url == "https://persistent.com" }).isTrue()

        // Now close the tab
        viewModel.onEvent(BrowserUiEvent.CloseTab(tabId))
        advanceUntilIdle()

        // Tab is gone from active tabs, but still present in recentTabs!
        assertThat(viewModel.uiState.value.tabs.any { it.id == tabId }).isFalse()
        assertThat(viewModel.uiState.value.recentTabs.any { it.url == "https://persistent.com" }).isTrue()
    }

    @Test
    fun reorderTabs_updatesTabPositions() = runTest(testDispatcher) {
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
    fun openSearch_whenOnHome_doesNotCreateNewTab() = runTest(testDispatcher) {
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
    fun openSearch_whenOnWebPage_createsNewTabAndOpensSearchOverlay() = runTest(testDispatcher) {
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
    fun openSearch_withOpenInNewTabFalse_editsCurrentTabWithoutCreatingNewTab() = runTest(testDispatcher) {
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
    fun setFullscreenCustomView_updatesStateWithOrientation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val fakeView = Any()
        viewModel.onEvent(
            BrowserUiEvent.SetFullscreenCustomView(
                view = fakeView,
                isFullscreen = true,
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isFullscreenCustomView).isTrue()
        assertThat(state.fullscreenCustomView).isSameInstanceAs(fakeView)
        assertThat(state.fullscreenRequestedOrientation).isEqualTo(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    @Test
    fun handleBackPress_whenFullscreenCustomView_exitsFullscreen() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val fakeView = Any()
        viewModel.onEvent(
            BrowserUiEvent.SetFullscreenCustomView(
                view = fakeView,
                isFullscreen = true,
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            )
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
    fun openSearch_withEmptyStringPrefilledUrl_whenOnWebPage_createsNewTab() = runTest(testDispatcher) {
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
    fun menu_homeScreen_isAlwaysExpandedOnBootAndNavigation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Home screen on boot should have menu expanded
        assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()

        // Navigate to a web page -> menu should follow web preference (default false)
        viewModel.onEvent(BrowserUiEvent.LoadUrl("https://example.com"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()

        // Navigate back to Home -> menu should be expanded
        viewModel.onEvent(BrowserUiEvent.OpenHome)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()
    }

    @Test
    fun menu_toggleOnWebPage_persistsStateAndAppliesToNewPages() = runTest(testDispatcher) {
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

        // Verify it was saved in settings repo
        val savedSettings = settingsRepo.getCurrentSettings()
        assertThat(savedSettings.isMenuExpandedOnWeb).isTrue()

        // Opening a new web tab should now respect the remembered preference
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://kotlinlang.org"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isMenuExpanded).isTrue()

        // User closes menu on web page
        viewModel.onEvent(BrowserUiEvent.ToggleMenu())
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isMenuExpanded).isFalse()
        assertThat(settingsRepo.getCurrentSettings().isMenuExpandedOnWeb).isFalse()
    }

    @Test
    fun menu_webScrollDown_closesMenuOnWebPage() = runTest(testDispatcher) {
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
    fun menu_handleBackPress_closesMenuOnWebPageFirst() = runTest(testDispatcher) {
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
}
