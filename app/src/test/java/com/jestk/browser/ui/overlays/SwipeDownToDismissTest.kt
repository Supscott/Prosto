package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of [swipeDownToDismiss], tested against a recording parent instead of the real IME
 * so the keyboard-drag path is observable without a WindowInsets animation controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeDownToDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dismissCount = 0
    private var scrolledY = 0f
    private var scrollSource: NestedScrollSource? = null
    private var flingCount = 0

    private fun setContent(keyboardVisible: Boolean) {
        val recorder = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scrolledY += available.y
                scrollSource = source
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                flingCount++
                return Velocity.Zero
            }
        }
        composeRule.setContent {
            val dispatcher = remember { NestedScrollDispatcher() }
            Box(Modifier.nestedScroll(recorder)) {
                Box(
                    Modifier
                        .size(240.dp, 52.dp)
                        .testTag("pill")
                        .nestedScroll(object : NestedScrollConnection {}, dispatcher)
                        .swipeDownToDismiss(
                            density = LocalDensity.current,
                            nestedScrollDispatcher = dispatcher,
                            isKeyboardVisible = { keyboardVisible },
                            onSwipeDown = { dismissCount++ },
                        ),
                )
            }
        }
    }

    private fun swipeDownBy(dp: Int) {
        composeRule.onNodeWithTag("pill").performTouchInput {
            swipeDown(startY = top + 4f, endY = top + 4f + dp.dp.toPx(), durationMillis = 200)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun keyboardVisible_dragGoesToKeyboardAndSettlesOnRelease_neverDismisses() {
        setContent(keyboardVisible = true)

        swipeDownBy(120)

        assertThat(scrolledY).isGreaterThan(0f)
        assertThat(scrollSource).isEqualTo(NestedScrollSource.UserInput)
        assertThat(flingCount).isEqualTo(1)
        assertThat(dismissCount).isEqualTo(0)
    }

    @Test
    fun keyboardVisible_repeatedShortSwipes_neverFallThroughToDismiss() {
        setContent(keyboardVisible = true)

        repeat(3) { swipeDownBy(30) }

        assertThat(flingCount).isEqualTo(3)
        assertThat(dismissCount).isEqualTo(0)
    }

    @Test
    fun keyboardHidden_swipePastThreshold_dismissesOnceWithoutTouchingKeyboard() {
        setContent(keyboardVisible = false)

        swipeDownBy(120)

        assertThat(dismissCount).isEqualTo(1)
        assertThat(scrolledY).isEqualTo(0f)
        assertThat(flingCount).isEqualTo(0)
    }

    @Test
    fun keyboardHidden_shortSwipe_doesNothing() {
        setContent(keyboardVisible = false)

        swipeDownBy(10)

        assertThat(dismissCount).isEqualTo(0)
    }

    @Test
    fun horizontalDrag_isIgnored() {
        setContent(keyboardVisible = false)

        composeRule.onNodeWithTag("pill").performTouchInput {
            swipeRight(startX = left + 4f, endX = right - 4f, durationMillis = 200)
        }
        composeRule.waitForIdle()

        assertThat(dismissCount).isEqualTo(0)
        assertThat(scrolledY).isEqualTo(0f)
    }

    @Test
    fun keyboardVisible_dragTurningHorizontal_unwindsTheKeyboardDrag() {
        setContent(keyboardVisible = true)

        composeRule.onNodeWithTag("pill").performTouchInput {
            val start = Offset(left + 4f, top + 4f)
            down(start)
            // Down past touch slop first, so part of the drag reaches the keyboard...
            moveTo(start + Offset(0f, 12.dp.toPx()))
            moveTo(start + Offset(0f, 24.dp.toPx()))
            // ...then mainly sideways: the engine carousel takes over.
            moveTo(start + Offset(60.dp.toPx(), 26.dp.toPx()))
            moveTo(start + Offset(120.dp.toPx(), 26.dp.toPx()))
            up()
        }
        composeRule.waitForIdle()

        assertThat(scrolledY).isEqualTo(0f)
        assertThat(flingCount).isEqualTo(0)
        assertThat(dismissCount).isEqualTo(0)
    }
}
