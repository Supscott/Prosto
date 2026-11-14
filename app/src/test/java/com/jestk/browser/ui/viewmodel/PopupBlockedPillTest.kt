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
import com.jestk.browser.engine.WindowTransportHandler
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PopupBlockedPillTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel() = BrowserViewModel(
        tabRepository = TabRepositoryImpl(db.tabDao(), testDispatcher),
        historyRepository = HistoryRepositoryImpl(db.historyDao(), testDispatcher),
        favoriteRepository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher),
        settingsRepository = SettingsRepositoryImpl(db.settingDao(), testDispatcher),
        ioDispatcher = testDispatcher,
    )

    @Test
    fun blockedPopupShowsThePillAndDismissClearsIt() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShowPopupBlocked("https://pill-dismiss.example/watch"))
        assertThat(viewModel.uiState.value.popupBlocked?.openerUrl).isEqualTo("https://pill-dismiss.example/watch")

        viewModel.onEvent(DismissPopupBlocked)
        assertThat(viewModel.uiState.value.popupBlocked).isNull()
        assertThat(WindowTransportHandler.sessionPopupAllowlist.allows("https://pill-dismiss.example/")).isFalse()
    }

    @Test
    fun allowForThisSiteFillsTheSessionAllowlistAndHidesThePill() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ShowPopupBlocked("https://pill-allow.example/watch"))
        viewModel.onEvent(AllowPopupsForSite)

        assertThat(viewModel.uiState.value.popupBlocked).isNull()
        assertThat(WindowTransportHandler.popupAllowlist.allows("https://pill-allow.example/other")).isTrue()
        assertThat(WindowTransportHandler.popupAllowlist.allows("https://elsewhere.example/")).isFalse()
    }
}
