package com.jestk.browser.ui.navigation

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
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchEngineSwitcherEditPopupToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO)
    private var hidden by mutableStateOf(setOf<String>())
    private val calls = mutableListOf<Pair<String, Boolean>>()

    private fun setPopup() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchEngineSwitcherEditPopup(
                    expanded = true,
                    engines = engines,
                    hiddenEngineIds = hidden,
                    isDark = false,
                    onToggleEngine = { id, visible ->
                        calls += id to visible
                        hidden = if (visible) hidden - id else hidden + id
                    },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun clickOnEngineNameFlipsVisibility() {
        setPopup()
        val name = SearchEngine.DUCKDUCKGO.displayName
        composeRule.onNodeWithText(name).assertIsOn()

        composeRule.onNodeWithText(name).performClick()
        composeRule.waitForIdle()

        assertThat(calls).containsExactly(SearchEngine.DUCKDUCKGO.id to false)
        composeRule.onNodeWithText(name).assertIsOff()
        composeRule.onNodeWithText(SearchEngine.GOOGLE.displayName).assertIsOn()

        composeRule.onNodeWithText(name).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(name).assertIsOn()
    }

    @Test
    fun eachEngineRowExposesOneToggleableNode() {
        setPopup()

        composeRule.onAllNodes(isToggleable()).assertCountEquals(engines.size)
        engines.forEach { engine ->
            composeRule
                .onAllNodes(isToggleable() and hasText(engine.displayName))
                .assertCountEquals(1)
        }
    }
}
