package com.jestk.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class SwipeToDismissPillTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = 0
    private var clicked = 0

    private fun showPill() {
        composeRule.setContent {
            Box(
                Modifier
                    .testTag("pill")
                    .size(200.dp, 48.dp)
                    .swipeToDismissPill("k", onDismiss = { dismissed++ })
                    .clickable { clicked++ },
            ) {}
        }
    }

    @Test
    fun swipeDown_dismisses() {
        showPill()
        composeRule.onNodeWithTag("pill").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun horizontalSwipe_doesNotDismiss() {
        showPill()
        composeRule.onNodeWithTag("pill").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun tap_clicksWithoutDismissing() {
        showPill()
        composeRule.onNodeWithTag("pill").performClick()
        assertThat(clicked).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun exposesDismissAccessibilityAction() {
        showPill()
        composeRule
            .onNodeWithTag("pill")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    }
}
