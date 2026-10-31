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
import com.jestk.browser.domain.model.FeedbackPromptState
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
class FeedbackPromptViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository
    private var mockCurrentTime: Long = 1_000_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        mockCurrentTime = 1_000_000L
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
            ioDispatcher = testDispatcher,
            currentTimeProvider = { mockCurrentTime },
        )
    }

    @Test
    fun initialFeedbackState_cardIsVisibleAndSheetIsClosed() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFeedbackCardVisible).isTrue()
            assertThat(viewModel.uiState.value.isFeedbackSheetVisible).isFalse()
        }

    @Test
    fun dismissFeedbackPrompt_persistsDismissedAndHidesCard() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.DismissFeedbackPrompt)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFeedbackCardVisible).isFalse()
            assertThat(settingsRepo.getCurrentSettings().feedbackPromptState)
                .isEqualTo(FeedbackPromptState.DISMISSED)

            // Re-instantiate ViewModel to simulate app restart
            val restoredViewModel = createViewModel()
            advanceUntilIdle()
            assertThat(restoredViewModel.uiState.value.isFeedbackCardVisible).isFalse()
        }

    @Test
    fun snoozeFeedbackPrompt_hidesCardUntilDurationExpires() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val snoozeDuration = 3 * 24 * 60 * 60 * 1000L // 3 days
            viewModel.onEvent(BrowserUiEvent.SnoozeFeedbackPrompt(snoozeDuration))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFeedbackCardVisible).isFalse()
            val expectedSnoozeUntil = mockCurrentTime + snoozeDuration
            assertThat(settingsRepo.getCurrentSettings().feedbackPromptSnoozedUntil)
                .isEqualTo(expectedSnoozeUntil)

            // Time advances 1 day (still snoozed)
            mockCurrentTime += 24 * 60 * 60 * 1000L
            viewModel.onAppResume()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isFeedbackCardVisible).isFalse()

            // Time advances past 3 days -> onAppResume restores visibility
            mockCurrentTime += 3 * 24 * 60 * 60 * 1000L
            viewModel.onAppResume()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isFeedbackCardVisible).isTrue()
        }

    @Test
    fun openAndCloseFeedbackSheet_togglesState() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenFeedbackSheet)
            assertThat(viewModel.uiState.value.isFeedbackSheetVisible).isTrue()

            viewModel.onEvent(BrowserUiEvent.CloseFeedbackSheet)
            assertThat(viewModel.uiState.value.isFeedbackSheetVisible).isFalse()
        }

    @Test
    fun handleBackNavigation_dismissesFeedbackSheetFirst() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(BrowserUiEvent.OpenFeedbackSheet)
            assertThat(viewModel.uiState.value.isFeedbackSheetVisible).isTrue()

            viewModel.onEvent(BrowserUiEvent.HandleBackPress)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFeedbackSheetVisible).isFalse()
        }
}
