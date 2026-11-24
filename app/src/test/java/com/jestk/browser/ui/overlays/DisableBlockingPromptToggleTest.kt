package com.jestk.browser.ui.overlays

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.components.LocalDockPromptAnimateEnter
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisableBlockingPromptToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val label: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.disable_blocking_all_workspaces)

    private var confirmedAll: Boolean? = null

    private fun setPrompt() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDockPromptAnimateEnter provides false) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    DisableBlockingPrompt(
                        isDark = false,
                        host = "example.com",
                        onDismiss = {},
                        onConfirm = { confirmedAll = it },
                    )
                }
            }
        }
    }

    @Test
    fun clickOnLabelFlipsTheValue() {
        setPrompt()
        composeRule.onNodeWithText(label).assertIsOff()

        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).assertIsOn()

        val confirm = RuntimeEnvironment.getApplication().getString(R.string.disable_blocking_confirm)
        composeRule.onNodeWithText(confirm).performClick()
        assertThat(confirmedAll).isTrue()

        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).assertIsOff()
    }

    @Test
    fun rowExposesOneToggleableNodeCarryingTheLabel() {
        setPrompt()

        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onAllNodes(isToggleable() and hasText(label)).assertCountEquals(1)
    }
}
