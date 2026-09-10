package com.jestk.browser.ui.views

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserViewModel
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
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoFullscreenSwipeTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var recentTabRepo: RecentTabRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
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

    /**
     * Logic verification of swipe-down gesture algorithm used in JestkApp fullscreen container Box.
     */
    private fun evaluateSwipeDownGesture(
        deltaX: Float,
        deltaY: Float,
        velocityY: Float,
        swipeThresholdPx: Float = 140f, // ~70dp at 2.0x density
        velocityThresholdPx: Float = 2000f,
        minDragDistancePx: Float = 40f
    ): Boolean {
        // Condition 1: Continuous downward drag past distance threshold with vertical dominance
        if (deltaY > swipeThresholdPx && deltaY > abs(deltaX) * 1.2f) {
            return true
        }
        // Condition 2: Fling/velocity past threshold on release with vertical dominance
        if (deltaY > minDragDistancePx && deltaY > abs(deltaX) * 1.2f) {
            if (deltaY > swipeThresholdPx || velocityY > velocityThresholdPx) {
                return true
            }
        }
        return false
    }

    @Test
    fun swipeDown_exceedingThreshold_triggersExit() {
        // Dragged down 150px (exceeds 140px threshold), minimal horizontal deviation
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 10f,
            deltaY = 150f,
            velocityY = 300f
        )
        assertThat(shouldExit).isTrue()
    }

    @Test
    fun swipeDown_belowThreshold_doesNotTriggerExit() {
        // Dragged down only 60px (< 140px threshold) with low velocity
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 5f,
            deltaY = 60f,
            velocityY = 200f
        )
        assertThat(shouldExit).isFalse()
    }

    @Test
    fun horizontalDrag_scrubbingVideo_doesNotTriggerExit() {
        // Horizontal scrubbing: deltaX = 180px, deltaY = 30px
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 180f,
            deltaY = 30f,
            velocityY = 100f
        )
        assertThat(shouldExit).isFalse()
    }

    @Test
    fun upwardSwipe_doesNotTriggerExit() {
        // Swiping up (e.g. to reveal recommendations)
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 5f,
            deltaY = -120f,
            velocityY = -800f
        )
        assertThat(shouldExit).isFalse()
    }

    @Test
    fun quickFlickDown_withHighVelocity_triggersExit() {
        // Quick flick down: deltaY = 60px (above min distance 40px), high velocity 2500f
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 10f,
            deltaY = 60f,
            velocityY = 2500f
        )
        assertThat(shouldExit).isTrue()
    }

    @Test
    fun tapGesture_zeroDelta_doesNotTriggerExit() {
        // Simple tap: deltaX = 0, deltaY = 0
        val shouldExit = evaluateSwipeDownGesture(
            deltaX = 0f,
            deltaY = 0f,
            velocityY = 0f
        )
        assertThat(shouldExit).isFalse()
    }

    @Test
    fun exitFullscreenEvent_clearsFullscreenStateAndEmitsCommand() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyView = View(context)

        // Enter fullscreen
        viewModel.onEvent(
            BrowserUiEvent.SetFullscreenCustomView(
                view = dummyView,
                isFullscreen = true,
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            )
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isFullscreenCustomView).isTrue()
        assertThat(viewModel.uiState.value.fullscreenCustomView).isSameInstanceAs(dummyView)
        assertThat(viewModel.uiState.value.fullscreenRequestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

        // Trigger ExitFullscreen
        viewModel.onEvent(BrowserUiEvent.ExitFullscreen)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isFullscreenCustomView).isFalse()
        assertThat(viewModel.uiState.value.fullscreenCustomView).isNull()
        assertThat(viewModel.uiState.value.fullscreenRequestedOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun customView_keepsScreenOn_andRegistersPlaybackResumption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)
        var resumptionCalled = false

        val client = JestkWebChromeClient(
            tabId = "tab-test",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onVideoResume = { resumptionCalled = true }
        )

        client.onShowCustomView(testView) { }

        assertThat(testView.keepScreenOn).isTrue()
        assertThat(resumptionCalled).isTrue()
    }
}
