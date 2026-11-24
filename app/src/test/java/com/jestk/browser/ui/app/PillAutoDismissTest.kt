package com.jestk.browser.ui.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PillAutoDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeAccessibilityManager(
        private val recommendedMs: Long,
    ) : AccessibilityManager {
        var lastControls: Boolean? = null

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            lastControls = containsControls
            return recommendedMs
        }
    }

    private fun setContent(
        manager: AccessibilityManager?,
        onDismiss: () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                PillAutoDismissEffect(
                    active = true,
                    restartKey = "tab",
                    baseTimeoutMs = 4500,
                    containsIcons = false,
                    containsControls = true,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun dismissesAtBaseTimeoutWithoutAccessibilityManager() {
        var dismissed = 0
        composeRule.mainClock.autoAdvance = false
        setContent(manager = null) { dismissed++ }
        composeRule.mainClock.advanceTimeBy(4400)
        assertThat(dismissed).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(200)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun staysPastBaseTimeoutWhenAccessibilityRecommendsLonger() {
        var dismissed = 0
        val manager = FakeAccessibilityManager(recommendedMs = 20_000)
        composeRule.mainClock.autoAdvance = false
        setContent(manager) { dismissed++ }
        composeRule.mainClock.advanceTimeBy(4600)
        assertThat(dismissed).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(15_000)
        assertThat(dismissed).isEqualTo(0)
        composeRule.mainClock.advanceTimeBy(600)
        assertThat(dismissed).isEqualTo(1)
        assertThat(manager.lastControls).isTrue()
    }
}
