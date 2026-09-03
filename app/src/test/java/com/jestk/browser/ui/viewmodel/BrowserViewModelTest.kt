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
}
