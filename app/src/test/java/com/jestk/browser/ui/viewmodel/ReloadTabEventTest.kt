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

/** The per-tab reload event of the tab menu (PRO-200): it names its tab instead of meaning "the active one". */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReloadTabEventTest {
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
    fun reloadOfAnInactiveTabNamesThatTabAndLeavesTheActiveTabAlone() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://one.example.com"))
        advanceUntilIdle()
        val inactiveId = viewModel.uiState.value.activeTabId!!
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://two.example.com"))
        advanceUntilIdle()
        val activeId = viewModel.uiState.value.activeTabId!!
        assertThat(activeId).isNotEqualTo(inactiveId)
        val commands = collectCommands()

        viewModel.onEvent(ReloadTab(inactiveId))
        advanceUntilIdle()

        assertThat(commands).containsExactly(NavigationCommand.ReloadTab(inactiveId))
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(activeId)
    }
}
