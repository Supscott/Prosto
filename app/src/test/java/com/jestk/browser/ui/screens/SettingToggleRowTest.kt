package com.jestk.browser.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingToggleRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var checked by mutableStateOf(false)
    private var changes = 0

    private fun setRow() {
        composeRule.setContent {
            SettingToggleRow(
                icon = Icons.Default.Download,
                title = "Ask before downloading",
                summary = "Show a prompt for every file",
                checked = checked,
                onCheckedChange = {
                    changes++
                    checked = it
                },
                isDark = false,
            )
        }
    }

    @Test
    fun clickOnTitleTogglesRow() {
        setRow()

        composeRule.onNodeWithText("Ask before downloading").performClick()
        composeRule.waitForIdle()

        assertThat(checked).isTrue()
        assertThat(changes).isEqualTo(1)
        composeRule.onNodeWithText("Ask before downloading").assertIsOn()

        composeRule.onNodeWithText("Show a prompt for every file").performClick()
        composeRule.waitForIdle()

        assertThat(checked).isFalse()
        composeRule.onNodeWithText("Ask before downloading").assertIsOff()
    }

    @Test
    fun exactlyOneToggleableNodeCarriesTitleAndSummary() {
        setRow()

        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule
            .onAllNodes(isToggleable() and hasText("Ask before downloading"))
            .assertCountEquals(1)
        composeRule
            .onAllNodes(isToggleable() and hasText("Show a prompt for every file"))
            .assertCountEquals(1)
    }
}
