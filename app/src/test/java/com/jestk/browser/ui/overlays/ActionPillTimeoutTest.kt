package com.jestk.browser.ui.overlays

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ExternalAppPrompt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The action pills stretch their auto-dismiss timeout to Android's "Time to take action" value. */
@RunWith(RobolectricTestRunner::class)
class ActionPillTimeoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeAccessibilityManager(
        private val recommendedMs: Long,
    ) : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = recommendedMs
    }

    @Test
    fun popupBlockedPillStaysUntilRecommendedTimeout() {
        var dismissed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides FakeAccessibilityManager(20_000)) {
                PopupBlockedPill(
                    isDark = false,
                    promptKey = "k",
                    accent = null,
                    onAllow = {},
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(POPUP_BLOCKED_PILL_TIMEOUT_MS + 1_000)
        assertThat(dismissed).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(12_000)
        assertThat(dismissed).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(1_500)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun externalAppPillCountdownUsesRecommendedTimeout() {
        var timedOut = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides FakeAccessibilityManager(12_000)) {
                ExternalAppNotificationPill(
                    prompt = ExternalAppPrompt(
                        id = "p1",
                        tabId = "t1",
                        appName = "YouTube",
                        appPackage = "com.google.android.youtube",
                        intentUri = "https://youtube.com/watch?v=1",
                        isInstalled = true,
                        domain = "youtube.com",
                    ),
                    onOpen = {},
                    onDismiss = {},
                    onTimeout = { timedOut++ },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(6_000)
        assertThat(timedOut).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(7_000)
        assertThat(timedOut).isEqualTo(1)
    }

    @Test
    fun externalAppPillCountdownFollowsTimeoutChangedMidPrompt() {
        var timedOut = 0
        var recommendedMs by mutableLongStateOf(6_000L)
        val manager = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long = recommendedMs
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                ExternalAppNotificationPill(
                    prompt = ExternalAppPrompt(
                        id = "p1",
                        tabId = "t1",
                        appName = "YouTube",
                        appPackage = "com.google.android.youtube",
                        intentUri = "https://youtube.com/watch?v=1",
                        isInstalled = true,
                        domain = "youtube.com",
                    ),
                    onOpen = {},
                    onDismiss = {},
                    onTimeout = { timedOut++ },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        recommendedMs = 12_000L
        composeRule.mainClock.advanceTimeBy(6_000)
        assertThat(timedOut).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(4_000)
        assertThat(timedOut).isEqualTo(1)
    }
}
