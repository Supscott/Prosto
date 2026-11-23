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
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.adblock.BlockPage
import com.jestk.browser.ui.viewmodel.events.SafetyEventHandler
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

/** The three promises of the `$document` block page: per-tab allowance, no history entry, working buttons. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BlockedDocumentFlowTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var webViewPool: WebViewPool
    private lateinit var coordinator: TabCoordinator
    private lateinit var safety: SafetyEventHandler

    private var uiState = BrowserUiState()
    private val navigationCommands = mutableListOf<NavigationCommand>()
    private val blockTitle = "Page blocked${BlockPage.TITLE_MARKER}"
    private val blockedUrl = "https://doc-blocked.example/landing"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        FaviconManager.init(context)
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
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
            emitNavigation = { navigationCommands.add(it) },
            scope = testScope,
            ioDispatcher = testDispatcher,
        )
        safety = SafetyEventHandler(
            tabCoordinator = coordinator,
            getCurrentState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            loadUrl = {},
            emitNavigation = { navigationCommands.add(it) },
        )
    }

    @After
    fun teardown() {
        webViewPool.clearAll()
        FaviconManager.deleteAllFavicons()
        Dispatchers.resetMain()
        db.close()
    }

    private fun setCanGoBack(tabId: String, canGoBack: Boolean) {
        uiState = uiState.copy(tabs = uiState.tabs.map { if (it.id == tabId) it.copy(canGoBack = canGoBack) else it })
    }

    @Test
    fun proceedAnyway_allowsTheHostInThatTabOnlyAndLoadsThePage() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        val tabA = uiState.activeTabId!!
        coordinator.opening.openNewTab("https://start-b.example")
        advanceUntilIdle()
        val tabB = uiState.activeTabId!!
        coordinator.activation.selectTab(tabA)
        advanceUntilIdle()
        navigationCommands.clear()

        safety.handle(AllowBlockedDocument(tabId = tabA, url = blockedUrl))
        advanceUntilIdle()

        assertThat(uiState.documentAllowedHostsByTab.allowsDocument(tabA, blockedUrl)).isTrue()
        assertThat(uiState.documentAllowedHostsByTab.allowsDocument(tabB, blockedUrl)).isFalse()
        assertThat(uiState.allowedAdNavigationUrls).isEmpty()
        assertThat(navigationCommands).containsExactly(NavigationCommand.LoadUrl(blockedUrl))
    }

    @Test
    fun proceedAnyway_allowanceIsDroppedWhenTheTabCloses() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        val tabA = uiState.activeTabId!!
        coordinator.opening.openNewTab("https://start-b.example")
        advanceUntilIdle()
        val tabB = uiState.activeTabId!!
        safety.handle(AllowBlockedDocument(tabId = tabA, url = blockedUrl))
        safety.handle(AllowBlockedDocument(tabId = tabB, url = blockedUrl))
        advanceUntilIdle()

        coordinator.closing.closeTab(tabA)
        advanceUntilIdle()

        assertThat(uiState.documentAllowedHostsByTab).doesNotContainKey(tabA)
        assertThat(uiState.documentAllowedHostsByTab.allowsDocument(tabB, blockedUrl)).isTrue()

        coordinator.closing.closeAllTabs()
        advanceUntilIdle()

        assertThat(uiState.documentAllowedHostsByTab).isEmpty()
    }

    @Test
    fun blockPage_isNotRecordedInHistory() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        val tab = uiState.activeTabId!!

        coordinator.pageEvents.handlePageStarted(tab, blockedUrl)
        coordinator.pageEvents.handleTitleReceived(tab, blockTitle)
        coordinator.pageEvents.handlePageFinished(tab, blockedUrl, blockTitle)
        advanceUntilIdle()

        assertThat(historyRepo.getAllHistory().first().map { it.url }).doesNotContain(blockedUrl)
        // The tab still shows what the WebView shows, without the invisible marker.
        assertThat(uiState.tabs.first { it.id == tab }.title).isEqualTo("Page blocked")
        assertThat(uiState.pageTitle).isEqualTo("Page blocked")
        assertThat(uiState.isLoading).isFalse()
    }

    @Test
    fun realPageAtTheSameUrl_isRecordedAsUsual() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        val tab = uiState.activeTabId!!

        coordinator.pageEvents.handlePageFinished(tab, blockedUrl, "Landing page")
        advanceUntilIdle()

        assertThat(historyRepo.getAllHistory().first().map { it.url }).contains(blockedUrl)
    }

    @Test
    fun goBackOnTheBlockPage_navigatesBackWhenThereIsAPageBehind() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        setCanGoBack(tab, true)
        navigationCommands.clear()

        safety.handle(GoBackFromBlockedDocument(tab))
        advanceUntilIdle()

        assertThat(navigationCommands).containsExactly(NavigationCommand.GoBack)
        assertThat(uiState.tabs.map { it.id }).contains(tab)
    }

    @Test
    fun goBackOnTheBlockPage_closesTheTabWhenNothingIsBehind() = runTest(testDispatcher) {
        coordinator.sessionSync.restoreSession(BrowserSettings(), SearchEngine.DEFAULT_ENGINES)
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-a.example")
        advanceUntilIdle()
        coordinator.opening.openNewTab("https://start-b.example")
        advanceUntilIdle()
        val tab = uiState.activeTabId!!
        setCanGoBack(tab, false)
        navigationCommands.clear()

        safety.handle(GoBackFromBlockedDocument(tab))
        advanceUntilIdle()

        assertThat(navigationCommands).doesNotContain(NavigationCommand.GoBack)
        assertThat(uiState.tabs.map { it.id }).doesNotContain(tab)
    }
}
