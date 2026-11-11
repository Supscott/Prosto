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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** Back in a tab that another app opened returns to that app, like Chrome. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExternalAppBackTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        viewModel = BrowserViewModel(
            tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
            historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.collectCommands(): List<NavigationCommand> {
        val commands = mutableListOf<NavigationCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigationCommands.collect { commands += it }
        }
        return commands
    }

    @Test
    fun backAtHistoryStart_closesExternalTabAndReturnsToCallerApp() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://mine.example.com"))
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://link.example.com", openedByExternalApp = true))
        advanceUntilIdle()
        val externalTabId = viewModel.uiState.value.activeTabId!!
        val tabCountBefore = viewModel.uiState.value.tabs.size
        val commands = collectCommands()

        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(commands).contains(NavigationCommand.ReturnToCallerApp)
        assertThat(state.tabs.map { it.id }).doesNotContain(externalTabId)
        assertThat(state.tabs).hasSize(tabCountBefore - 1)
        assertThat(state.externalAppTabIds).doesNotContain(externalTabId)
    }

    @Test
    fun backInOwnTab_keepsUserInBrowser() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://one.example.com"))
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://two.example.com"))
        advanceUntilIdle()
        val commands = collectCommands()

        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()

        assertThat(commands).doesNotContain(NavigationCommand.ReturnToCallerApp)
        assertThat(viewModel.uiState.value.externalAppTabIds).isEmpty()
    }

    @Test
    fun onlyExternalTab_isReplacedByHomeAndReturnsToCallerApp() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.uiState.value.tabs.forEach { viewModel.onEvent(BrowserUiEvent.CloseTab(it.id)) }
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://link.example.com", openedByExternalApp = true))
        advanceUntilIdle()
        viewModel.uiState.value.tabs.filter { it.id != viewModel.uiState.value.activeTabId }
            .forEach { viewModel.onEvent(BrowserUiEvent.CloseTab(it.id)) }
        advanceUntilIdle()
        val commands = collectCommands()

        viewModel.onEvent(BrowserUiEvent.HandleBackPress)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(commands).contains(NavigationCommand.ReturnToCallerApp)
        assertThat(state.tabs).hasSize(1)
        assertThat(state.tabs.single().url).isEmpty()
    }
}
