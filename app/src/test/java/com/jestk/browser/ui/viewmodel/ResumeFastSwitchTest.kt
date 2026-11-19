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

/** The resume tab is the first thing the UI state shows: the left tab is never the active one. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResumeFastSwitchTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepository: TabRepositoryImpl
    private var nowMs = 0L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun newViewModel() =
        BrowserViewModel(
            tabRepository = tabRepository,
            historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
            ioDispatcher = testDispatcher,
            currentTimeProvider = { nowMs },
        )

    private val longAbsence get() = BackgroundStamp(atMs = 0L, mediaActive = false)

    @Test
    fun warmStart_newTabIsActiveBeforeAnyDispatch() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site.example.com"))
        advanceUntilIdle()
        val siteId = viewModel.uiState.value.activeTabId!!
        nowMs = ResumeNewTabPolicy.DEFAULT_THRESHOLD_MS

        viewModel.onReturnedToForeground(longAbsence, returnedWithLink = false, mediaActiveNow = false)

        // No advanceUntilIdle: this is the state the first frame after onStart renders.
        val state = viewModel.uiState.value
        assertThat(state.activeTabId).isNotEqualTo(siteId)
        assertThat(state.activeTab?.isHomeTab).isTrue()
        assertThat(state.lastTabPrompt).isEqualTo(LastTabPrompt(siteId, state.activeTabId!!))

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(state.activeTabId)
        assertThat(tabRepository.getTabById(state.activeTabId!!)?.isActive).isTrue()
    }

    @Test
    fun coldStart_restoredSessionOpensOnNewTab() = runTest(testDispatcher) {
        val site = tabRepository.createTab(url = "https://site.example.com", title = "Site")
        tabRepository.setActiveTab(site.id)
        val viewModel = newViewModel()
        val activeIds = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { activeIds += it.activeTabId }
        }
        nowMs = ResumeNewTabPolicy.DEFAULT_THRESHOLD_MS

        // The Activity starts before the session has loaded.
        viewModel.onReturnedToForeground(longAbsence, returnedWithLink = false, mediaActiveNow = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(activeIds).doesNotContain(site.id)
        assertThat(state.activeTab?.isHomeTab).isTrue()
        assertThat(state.lastTabPrompt).isEqualTo(LastTabPrompt(site.id, state.activeTabId!!))
        assertThat(state.tabs.map { it.id }).contains(site.id)
    }

    @Test
    fun coldStart_shortAbsenceKeepsRestoredTab() = runTest(testDispatcher) {
        val site = tabRepository.createTab(url = "https://site.example.com", title = "Site")
        tabRepository.setActiveTab(site.id)
        val viewModel = newViewModel()
        nowMs = 1_000L

        viewModel.onReturnedToForeground(longAbsence, returnedWithLink = false, mediaActiveNow = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeTabId).isEqualTo(site.id)
        assertThat(viewModel.uiState.value.lastTabPrompt).isNull()
    }
}
