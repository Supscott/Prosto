package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.local.entity.TabEntity
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BrowserViewModelStressTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository

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

    private fun createViewModel(): BrowserViewModel {
        return BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun rapidTabCreation_100Tabs_maintainsConsistencyAndOrder() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Initially 1 default home tab
        assertThat(viewModel.uiState.value.tabs).hasSize(1)

        val totalToSpawn = 100
        for (i in 1..totalToSpawn) {
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site$i.org"))
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(totalToSpawn + 1)
        assertThat(state.activeTabId).isEqualTo(state.tabs.last().id)
        assertThat(state.currentUrl).isEqualTo("https://site$totalToSpawn.org")

        // Verify all 101 tabs in DB
        val dbTabs = tabRepo.getInitialTabs()
        assertThat(dbTabs).hasSize(totalToSpawn + 1)
        val activeCount = dbTabs.count { it.isActive }
        assertThat(activeCount).isEqualTo(1)
        assertThat(dbTabs.find { it.isActive }?.id).isEqualTo(state.activeTabId)
    }

    @Test
    fun rapidTabClosures_toEmpty_autoRestoresSingleHomeTab() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create 20 tabs
        for (i in 1..20) {
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://tab$i.com"))
        }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tabs).hasSize(21)

        // Rapidly close all tabs one by one
        val tabsToClose = viewModel.uiState.value.tabs.map { it.id }
        for (tabId in tabsToClose) {
            viewModel.onEvent(BrowserUiEvent.CloseTab(tabId))
        }
        advanceUntilIdle()

        // UI state must never be in an empty or crash state; auto-creates 1 default home tab
        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.activeTabId).isNotNull()
        assertThat(state.activeTabId).isEqualTo(state.tabs.first().id)
        assertThat(state.currentUrl).isEmpty()
        assertThat(state.pageTitle).isEqualTo("Home")
        assertThat(state.activeOverlay).isEqualTo(OverlayType.HOME)
    }

    @Test
    fun rapidTabSwitching_bidirectionalAndRandomJumps_updatesActiveTabAndUrl() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val urls = listOf(
            "https://jestk.com",
            "https://kotlinlang.org",
            "https://android.com",
            "https://github.com",
            "https://developer.android.com"
        )
        for (url in urls) {
            viewModel.onEvent(BrowserUiEvent.OpenNewTab(url))
        }
        advanceUntilIdle()

        val currentTabs = viewModel.uiState.value.tabs
        assertThat(currentTabs).hasSize(6) // 1 initial home + 5 opened

        // Test swipe prev repeatedly
        for (i in 1..10) {
            viewModel.onEvent(BrowserUiEvent.SwipePrevTab)
        }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeTabIndex).isEqualTo(0)
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(currentTabs[0].id)

        // Test swipe next step by step
        for (i in 1..5) {
            viewModel.onEvent(BrowserUiEvent.SwipeNextTab)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.activeTabIndex).isEqualTo(i)
            assertThat(viewModel.uiState.value.activeTabId).isEqualTo(currentTabs[i].id)
        }

        // Direct random jumps
        val targetTab = currentTabs[2]
        viewModel.onEvent(BrowserUiEvent.SelectTab(targetTab.id))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(targetTab.id)
        assertThat(viewModel.uiState.value.currentUrl).isEqualTo(targetTab.url)
    }

    @Test
    fun coldBootRestoration_withMultipleTabsAndNoActiveFlag_selectsMostRecentTab() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val tab1 = Tab(
            id = "tab-1",
            url = "https://tab1.com",
            title = "Tab 1",
            lastActiveTimestamp = now - 5000,
            position = 0,
            isActive = false
        )
        val tab2 = Tab(
            id = "tab-2",
            url = "https://tab2.com",
            title = "Tab 2",
            lastActiveTimestamp = now, // most recent
            position = 1,
            isActive = false
        )
        val tab3 = Tab(
            id = "tab-3",
            url = "https://tab3.com",
            title = "Tab 3",
            lastActiveTimestamp = now - 10000,
            position = 2,
            isActive = false
        )

        db.tabDao().insertTabs(listOf(tab1.toEntity(), tab2.toEntity(), tab3.toEntity()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(3)
        assertThat(state.activeTabId).isEqualTo("tab-2")
        assertThat(state.currentUrl).isEqualTo("https://tab2.com")
        assertThat(state.pageTitle).isEqualTo("Tab 2")
    }

    @Test
    fun coldBootRestoration_withCorruptedMultipleActiveFlags_normalizesToSingleActiveTab() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        // Corrupted state: two tabs both marked isActive = 1
        val tab1 = TabEntity(
            id = "tab-corrupt-1",
            url = "https://tab1.com",
            title = "Tab 1",
            lastActiveTimestamp = now - 5000,
            position = 0,
            isActive = true
        )
        val tab2 = TabEntity(
            id = "tab-corrupt-2",
            url = "https://tab2.com",
            title = "Tab 2",
            lastActiveTimestamp = now,
            position = 1,
            isActive = true
        )
        db.tabDao().insertTabs(listOf(tab1, tab2))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(2)
        // One is selected
        assertThat(state.activeTabId).isNotNull()

        // Verify DB only has 1 active tab after restoration
        val dbTabs = db.tabDao().getAllTabs()
        val activeCount = dbTabs.count { it.isActive }
        assertThat(activeCount).isEqualTo(1)
    }

    @Test
    fun coldBootRestoration_withExtremeData_handlesCleanly() = runTest(testDispatcher) {
        val hugeUrl = "https://example.com/" + "a".repeat(20000)
        val sqlInjectionTitle = "'); DROP TABLE tabs; -- <script>alert('xss')</script>"
        val emojiUrl = "https://🎉.example.com/🌟?q=🦄"

        val extremeTabs = listOf(
            TabEntity(
                id = "extreme-1",
                url = hugeUrl,
                title = sqlInjectionTitle,
                lastActiveTimestamp = 1000L,
                snapshotPath = null,
                position = -5, // negative position index
                isIncognito = false,
                isActive = false,
                canGoBack = true,
                canGoForward = false
            ),
            TabEntity(
                id = "extreme-2",
                url = emojiUrl,
                title = "Emoji site",
                lastActiveTimestamp = Long.MAX_VALUE,
                snapshotPath = "/data/user/0/com.jestk.browser/files/tab_extreme-2.png",
                position = 100,
                isIncognito = true,
                isActive = false,
                canGoBack = false,
                canGoForward = true
            )
        )
        db.tabDao().insertTabs(extremeTabs)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(2)
        // Tab with Long.MAX_VALUE lastActiveTimestamp was selected
        assertThat(state.activeTabId).isEqualTo("extreme-2")
        assertThat(state.currentUrl).isEqualTo(emojiUrl)
    }

    @Test
    fun overlayStateTransitions_rapidCycling_maintainsStateCorrectness() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.HOME)

        val overlayTransitions = listOf(
            OverlayType.SEARCH,
            OverlayType.TAB_GRID,
            OverlayType.SETTINGS,
            OverlayType.HOME,
            OverlayType.NONE,
            OverlayType.SEARCH,
            OverlayType.SETTINGS,
            OverlayType.NONE
        )

        for (overlay in overlayTransitions) {
            viewModel.onEvent(BrowserUiEvent.OpenOverlay(overlay))
            assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(overlay)
        }

        // Open search with prefilled query
        viewModel.onEvent(BrowserUiEvent.OpenSearch("https://prefilled.org"))
        val searchState = viewModel.uiState.value
        assertThat(searchState.activeOverlay).isEqualTo(OverlayType.SEARCH)
        assertThat(searchState.searchQuery).isEqualTo("https://prefilled.org")

        // Dismiss overlay
        viewModel.onEvent(BrowserUiEvent.DismissOverlay)
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.NONE)
    }

    @Test
    fun handleBackPress_comprehensiveHierarchyTransitions() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. When an overlay is open -> dismiss overlay
        viewModel.onEvent(BrowserUiEvent.OpenOverlay(OverlayType.TAB_GRID))
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.TAB_GRID)
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.NONE)

        // 2. When canGoBack is true -> doesn't close tab, sends NavigationCommand.GoBack
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://jestk.com"))
        advanceUntilIdle()
        val currentTabId = viewModel.uiState.value.activeTabId!!
        viewModel.onEvent(BrowserUiEvent.OnNavigationStateChanged(currentTabId, canGoBack = true, canGoForward = false))
        assertThat(viewModel.uiState.value.canGoBack).isTrue()

        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        // Tab is still open
        assertThat(viewModel.uiState.value.tabs).hasSize(2)

        // 3. When canGoBack is false -> closes active tab
        viewModel.onEvent(BrowserUiEvent.OnNavigationStateChanged(currentTabId, canGoBack = false, canGoForward = false))
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tabs).hasSize(1)

        // 4. Closing the last remaining tab auto-spawns a clean home tab
        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tabs).hasSize(1)
        assertThat(viewModel.uiState.value.currentUrl).isEmpty()
        assertThat(viewModel.uiState.value.activeOverlay).isEqualTo(OverlayType.HOME)
    }

    @Test
    fun backgroundTabWebEvents_doNotCorruptForegroundActiveTabState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Open foreground Tab 1 and load Jestk
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://jestk.com"))
        advanceUntilIdle()
        val tab1Id = viewModel.uiState.value.activeTabId!!

        // Open Tab 2 in background
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://background.com"))
        advanceUntilIdle()
        val tab2Id = viewModel.uiState.value.activeTabId!!

        // Switch back to Tab 1 as active foreground tab
        viewModel.onEvent(BrowserUiEvent.SelectTab(tab1Id))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab1Id)

        // Foreground tab receives progress
        viewModel.onEvent(BrowserUiEvent.OnPageStarted(tab1Id, "https://jestk.com"))
        viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tab1Id, 50))
        assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://jestk.com")
        assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(0.5f)
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        // Background Tab 2 receives web events
        viewModel.onEvent(BrowserUiEvent.OnPageStarted(tab2Id, "https://background.com/loaded"))
        viewModel.onEvent(BrowserUiEvent.OnProgressChanged(tab2Id, 90))
        viewModel.onEvent(BrowserUiEvent.OnPageFinished(tab2Id, "https://background.com/loaded", "Background Page"))

        // Active foreground tab state MUST remain untouched
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(tab1Id)
        assertThat(viewModel.uiState.value.currentUrl).isEqualTo("https://jestk.com")
        assertThat(viewModel.uiState.value.loadingProgress).isEqualTo(0.5f)
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        // But Background tab MUST have its changes saved in database
        advanceUntilIdle()
        val backgroundTabInDb = tabRepo.getTabById(tab2Id)
        assertThat(backgroundTabInDb?.url).isEqualTo("https://background.com/loaded")
        assertThat(backgroundTabInDb?.title).isEqualTo("Background Page")
    }

    @Test
    fun adversarialUrlInputs_routeCorrectly() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val testCases = listOf(
            "" to "",
            "about:blank" to "",
            "   " to "",
            "jestk.com" to "https://jestk.com",
            "http://insecure.site" to "http://insecure.site",
            "https://secure.site/test" to "https://secure.site/test",
            "localhost:8080" to "https://localhost:8080",
            "192.168.1.100:3000" to "https://192.168.1.100:3000",
            "search query with spaces" to "https://duckduckgo.com/?q=search+query+with+spaces",
            "node.js express router" to "https://duckduckgo.com/?q=node.js+express+router"
        )

        for ((input, expectedUrlPrefix) in testCases) {
            viewModel.onEvent(BrowserUiEvent.LoadUrl(input))
            advanceUntilIdle()
            val state = viewModel.uiState.value
            if (expectedUrlPrefix.isEmpty()) {
                assertThat(state.currentUrl).isEmpty()
                assertThat(state.activeOverlay).isEqualTo(OverlayType.HOME)
            } else {
                assertThat(state.currentUrl).startsWith(expectedUrlPrefix)
                assertThat(state.activeOverlay).isEqualTo(OverlayType.NONE)
            }
        }
    }

    @Test
    fun webScroll_hysteresisThresholdStress_togglesBottomBarVisibilityProperly() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

        // Downward scroll maintains bottom bar visible
        viewModel.onEvent(BrowserUiEvent.OnWebScroll(15f))
        assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()

        // Upward scroll maintains bottom bar visible
        viewModel.onEvent(BrowserUiEvent.OnWebScroll(-15f))
        assertThat(viewModel.uiState.value.isBottomBarVisible).isTrue()
    }
}
