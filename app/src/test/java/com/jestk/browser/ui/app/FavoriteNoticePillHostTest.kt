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
import com.jestk.browser.ui.viewmodel.DismissFavoriteNotice
import com.jestk.browser.ui.viewmodel.FavoriteNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The favorite notice pill's timer: accessibility timeout, and a new notice replacing the current one (PRO-195). */
@RunWith(RobolectricTestRunner::class)
class FavoriteNoticePillHostTest {
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
                    FavoriteNoticePillHost(
                        uiState = uiState,
                        onEvent = {
                            events += it
                            if (it == DismissFavoriteNotice) uiState = uiState.copy(favoriteNotice = null)
                        },
                        isDark = false,
                        activeWorkspaceColor = null,
                        baseBottomOffset = 0.dp,
                    )
                }
            }
        }
    }

    private fun show(notice: FavoriteNotice) {
        composeRule.runOnUiThread {
            uiState = uiState.copy(favoriteNotice = notice)
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun dismissals() = events.count { it == DismissFavoriteNotice }

    @Test
    fun dismissesAfterTheBaseTimeoutWithoutAccessibilityManager() {
        setContent(manager = null)
        show(FavoriteNotice(1, added = true))
        composeRule.mainClock.advanceTimeBy(FAVORITE_NOTICE_PILL_TIMEOUT_MS - 200)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(400)
        assertThat(dismissals()).isEqualTo(1)
    }

    @Test
    fun staysForTheInteractiveTimeoutWhenAccessibilityRecommendsLonger() {
        setContent(ControlsOnlyAccessibilityManager(interactiveMs = 20_000))
        show(FavoriteNotice(1, added = true))
        composeRule.mainClock.advanceTimeBy(FAVORITE_NOTICE_PILL_TIMEOUT_MS + 1_000)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(15_000)
        assertThat(dismissals()).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(1_500)
        assertThat(dismissals()).isEqualTo(1)
    }

    @Test
    fun aNewNoticeReplacesTheCurrentOneAndRestartsTheTimer() {
        setContent(manager = null)
        show(FavoriteNotice(1, added = true))
        composeRule.mainClock.advanceTimeBy(2_000)
        show(FavoriteNotice(2, added = false))

        // The first notice would have gone at 3 s: the second one holds the pill until 2 s + 3 s.
        composeRule.mainClock.advanceTimeBy(2_500)
        assertThat(dismissals()).isEqualTo(0)
        assertThat(uiState.favoriteNotice).isEqualTo(FavoriteNotice(2, added = false))
        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(dismissals()).isEqualTo(1)
        assertThat(uiState.favoriteNotice).isNull()
    }

    @Test
    fun aNoticeAfterADismissalGetsAFullTimeoutEvenWhileTheOldPillFadesOut() {
        setContent(manager = null)
        show(FavoriteNotice(1, added = true))
        var frames = 0
        while (dismissals() == 0 && frames++ < 400) composeRule.mainClock.advanceTimeByFrame()
        assertThat(dismissals()).isEqualTo(1)

        // The pill is still fading out here; the next notice must not inherit the spent timer.
        show(FavoriteNotice(2, added = false))
        composeRule.mainClock.advanceTimeBy(FAVORITE_NOTICE_PILL_TIMEOUT_MS - 500)
        assertThat(dismissals()).isEqualTo(1)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(dismissals()).isEqualTo(2)
    }
}
