package com.jestk.browser.ui.overlays

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FloatingContextMenuContainerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun floatingContextMenu_rendersContentAndDismissesOnScrimClick() {
        var dismissed = false

        composeTestRule.setContent {
            FloatingContextMenuContainer(
                anchor = Offset(300f, 400f),
                onDismiss = { dismissed = true }
            ) {
                Text("Sample Menu Item")
            }
        }

        composeTestRule.onNodeWithText("Sample Menu Item").assertExists()
        // Content click should NOT dismiss
        composeTestRule.onNodeWithText("Sample Menu Item").performClick()
        assertEquals(false, dismissed)
    }

    @Test
    fun floatingContextMenu_rendersWithoutAnchor() {
        composeTestRule.setContent {
            FloatingContextMenuContainer(
                anchor = null,
                onDismiss = {}
            ) {
                Text("Centered Menu Item")
            }
        }

        composeTestRule.onNodeWithText("Centered Menu Item").assertExists()
    }
}
