package com.jestk.browser.ui.overlays

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockedAdNavigationBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun autoDismiss_waitsUntilAHeldBannerIsReleased() {
        var dismissals = 0
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BlockedAdNavigationBanner(
                isDark = false,
                url = "https://ads.example.com/click",
                onDismiss = { dismissals++ },
                onConfirm = {},
                modifier = Modifier.testTag("banner"),
            )
        }

        // Start a small horizontal drag (below the swipe-away threshold) and keep holding.
        composeTestRule.onNodeWithTag("banner").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(6_000L)
        assertEquals(0, dismissals)

        composeTestRule.onNodeWithTag("banner").performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        assertEquals(1, dismissals)
    }
}
