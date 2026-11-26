package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search history header in a narrow column with the longest (Russian) labels at font scale 1.3
 * (PRO-177): the "All"/"Collapse" and "Clear" actions must each stay on one line and whole, inside
 * the header; only the title may give way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru")
class SearchHistoryHeaderNarrowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val columnWidth = 220f

    private fun setHeader(isExpanded: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(modifier = Modifier.width(columnWidth.dp)) {
                        InsetGroupedHistoryCard(
                            items = listOf(HistoryItem(1L, "https://example.com/?q=kotlin", "Kotlin", 0L)),
                            isDark = false,
                            isExpanded = isExpanded,
                            onToggleExpand = {},
                            onItemClick = {},
                            onInsertQuery = {},
                            onClearHistory = {},
                        )
                    }
                }
            }
        }
    }

    private fun node(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertThat(action).isNotNull()
        assertThat(action!!(results)).isTrue()
        return results.single()
    }

    private fun assertActionStaysOnOneLine(label: String) {
        val action = node(label)
        val layout = action.layout()
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.isLineEllipsized(0)).isFalse()
        // The node is as wide as its text (nothing clipped) and lies inside the column.
        assertThat(action.fetchSemanticsNode().size.width).isAtLeast(layout.size.width)
        val bounds = action.getBoundsInRoot()
        assertThat(bounds.left.value).isAtLeast(0f)
        assertThat(bounds.right.value).isAtMost(columnWidth)
    }

    private fun assertHeaderHolds(isExpanded: Boolean, toggleLabel: String) {
        setHeader(isExpanded)
        composeRule.waitForIdle()

        assertActionStaysOnOneLine(toggleLabel)
        assertActionStaysOnOneLine("Очистить")
        // The title is the one that gives way: it ends before the toggle action starts.
        val title = node("История поиска")
        assertThat(title.layout().lineCount).isEqualTo(1)
        assertThat(title.getBoundsInRoot().right.value).isAtMost(node(toggleLabel).getBoundsInRoot().left.value)
    }

    @Test
    fun collapsedHeaderActionsStayOnOneLine() = assertHeaderHolds(isExpanded = false, toggleLabel = "Все")

    @Test
    fun expandedHeaderActionsStayOnOneLine() = assertHeaderHolds(isExpanded = true, toggleLabel = "Свернуть")
}
