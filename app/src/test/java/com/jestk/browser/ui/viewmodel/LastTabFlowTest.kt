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

/** Returning after a long absence opens a Home tab and offers a way back to the last tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LastTabFlowTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: BrowserViewModel
    private var nowMs = 0L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        viewModel = BrowserViewModel(
            tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
            historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
            favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
            settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
            ioDispatcher = testDispatcher,
            currentTimeProvider = { nowMs },
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val state get() = viewModel.uiState.value

    private fun kotlinx.coroutines.test.TestScope.returnAfterLongAbsence(returnedWithLink: Boolean = false) {
        val stamp = BackgroundStamp(atMs = 0L, mediaActive = false)
        nowMs = ResumeNewTabPolicy.DEFAULT_THRESHOLD_MS
        viewModel.onReturnedToForeground(stamp, returnedWithLink, mediaActiveNow = false)
        advanceUntilIdle()
    }

    private fun kotlinx.coroutines.test.TestScope.openSite(): String {
        viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://site.example.com"))
        advanceUntilIdle()
        return state.activeTabId!!
    }

    @Test
    fun longAbsence_opensHomeTabAndShowsPill() = runTest(testDispatcher) {
        advanceUntilIdle()
        val siteId = openSite()

        returnAfterLongAbsence()

        val prompt = state.lastTabPrompt
        assertThat(prompt?.previousTabId).isEqualTo(siteId)
        assertThat(state.activeTabId).isEqualTo(prompt?.newTabId)
        assertThat(state.activeTab?.isHomeTab).isTrue()
    }

    @Test
    fun returnWithLink_opensNothing() = runTest(testDispatcher) {
        advanceUntilIdle()
        val siteId = openSite()

        returnAfterLongAbsence(returnedWithLink = true)

        assertThat(state.lastTabPrompt).isNull()
        assertThat(state.activeTabId).isEqualTo(siteId)
    }

    @Test
    fun tapPill_switchesBackAndClosesUntouchedHomeTab() = runTest(testDispatcher) {
        advanceUntilIdle()
        val siteId = openSite()
        returnAfterLongAbsence()
        val homeId = state.activeTabId!!

        viewModel.onEvent(RestoreLastTab)
        advanceUntilIdle()

        assertThat(state.activeTabId).isEqualTo(siteId)
        assertThat(state.tabs.map { it.id }).doesNotContain(homeId)
        assertThat(state.lastTabPrompt).isNull()
        assertThat(state.showUndoTabCloseSnackbar).isFalse()
    }

    @Test
    fun tapPill_keepsNewTabThatWasNavigated() = runTest(testDispatcher) {
        advanceUntilIdle()
        val siteId = openSite()
        returnAfterLongAbsence()
        val newTabId = state.activeTabId!!
        viewModel.onEvent(BrowserUiEvent.LoadUrl("https://other.example.com"))
        advanceUntilIdle()
        viewModel.onEvent(BrowserUiEvent.OnPageStarted(newTabId, "https://other.example.com"))
        advanceUntilIdle()
        assertThat(state.activeTab?.isHomeTab).isFalse()

        viewModel.onEvent(RestoreLastTab)
        advanceUntilIdle()

        assertThat(state.activeTabId).isEqualTo(siteId)
        assertThat(state.tabs.map { it.id }).contains(newTabId)
    }

    @Test
    fun dismiss_clearsPillAndKeepsNewTab() = runTest(testDispatcher) {
        advanceUntilIdle()
        openSite()
        returnAfterLongAbsence()
        val homeId = state.activeTabId!!

        viewModel.onEvent(DismissLastTabPrompt)

        assertThat(state.lastTabPrompt).isNull()
        assertThat(state.activeTabId).isEqualTo(homeId)
    }
}
