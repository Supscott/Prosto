package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A quick vertical swipe over a search engine button must not count as a tap on it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineButtonTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    private fun setContent() {
        composeRule.setContent {
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val state = remember {
                SearchEngineCarouselState(
                    initialEngine = SearchEngine.GOOGLE,
                    density = density,
                    coroutineScope = scope,
                    vibration = object : VibrationFeedback {
                        override fun tick() {}
                        override fun confirm() {}
                    },
                    onEngineSelected = {},
                )
            }
            Box(
                Modifier
                    .size(200.dp)
                    .testTag("button")
                    .searchEngineButtonGestures(state, onClick = { clicks++ }),
            )
        }
    }

    @Test
    fun tap_clicks() {
        setContent()
        composeRule.onNodeWithTag("button").performTouchInput { click() }
        composeRule.waitForIdle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun quickVerticalSwipe_doesNotClick() {
        setContent()
        composeRule.onNodeWithTag("button").performTouchInput { swipeDown(durationMillis = 80) }
        composeRule.onNodeWithTag("button").performTouchInput { swipeUp(durationMillis = 80) }
        composeRule.waitForIdle()
        assertThat(clicks).isEqualTo(0)
    }
}
