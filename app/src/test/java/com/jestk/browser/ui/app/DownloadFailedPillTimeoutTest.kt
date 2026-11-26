package com.jestk.browser.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissDownloadFailure
import com.jestk.browser.ui.viewmodel.DownloadFailure
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The download-failed pill's timer follows Android's interactive accessibility timeout (PRO-201). */
@RunWith(RobolectricTestRunner::class)
class DownloadFailedPillTimeoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Like Android: the long timeout applies only to content that contains controls. */
    private class ControlsOnlyAccessibilityManager(
        private val interactiveMs: Long,
    ) : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = if (containsControls) interactiveMs else originalTimeoutMillis
    }

    private var uiState by mutableStateOf(BrowserUiState())
    private val events = mutableListOf<BrowserUiEvent>()

    private fun setContent(manager: AccessibilityManager?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                Box(Modifier.fillMaxSize()) {
                    DownloadFailedPill(
                        uiState = uiState,
                        onEvent = {
                            events += it
                            if (it == DismissDownloadFailure) uiState = uiState.copy(downloadFailure = null)
                        },
                        isDark = false,
                        activeWorkspaceColor = null,
                        baseBottomOffset = 0.dp,
                    )
                }
            }
        }
    }

    private fun show(failure: DownloadFailure) {
        composeRule.runOnUiThread {
            uiState = uiState.copy(downloadFailure = failure)
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun dismissals() = events.count { it == DismissDownloadFailure }

    @Test
    fun dismissesAfterTheBaseTimeoutWithoutAccessibilityManager() {
        setContent(manager = null)
        show(DownloadFailure(1, "a.zip", "Network error"))
        composeRule.mainClock.advanceTimeBy(DOWNLOAD_FAILED_PILL_TIMEOUT_MS - 200)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(400)
        assertThat(dismissals()).isEqualTo(1)
    }

    @Test
    fun staysForTheInteractiveTimeoutWhenAccessibilityRecommendsLonger() {
        setContent(ControlsOnlyAccessibilityManager(interactiveMs = 20_000))
        show(DownloadFailure(1, "a.zip", "Network error"))
        composeRule.mainClock.advanceTimeBy(DOWNLOAD_FAILED_PILL_TIMEOUT_MS + 1_000)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(13_000)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(2_500)
        assertThat(dismissals()).isEqualTo(1)
    }
}
