package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tab menu grows to its widest label, so no label is cut with an ellipsis (PRO-187, moved from the three-dots menu). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class TabContextMenuWidthTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        allowlisted: Boolean,
        fontScale: Float,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        TabContextMenuCard(
                            onDismissRequest = {},
                            isDark = false,
                            onCloseTab = {},
                            onCopyUrl = {},
                            isFavorite = false,
                            onToggleFavorite = {},
                            isSiteAllowlisted = allowlisted,
                            onToggleBlocking = {},
                            onShareLink = {},
                            onReload = {},
                            folderId = null,
                            availableFolders = emptyList(),
                            onCreateFolder = {},
                            onAddToFolder = null,
                            onRemoveFromFolder = null,
                            availableWorkspaces = listOf(Workspace(id = "w", name = "Work", color = 0)),
                            onMoveToWorkspace = {},
                        )
                    }
                }
            }
        }
    }

    private fun assertLabelsNotCut(ids: List<Int>) {
        for (id in ids) {
            val label = RuntimeEnvironment.getApplication().getString(id)
            val layouts = mutableListOf<TextLayoutResult>()
            val node = composeRule.onNodeWithText(label).fetchSemanticsNode()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertThat(layouts).isNotEmpty()
            assertThat(layouts.first().hasVisualOverflow).isFalse()
            assertThat(layouts.first().lineCount).isAtMost(3)
        }
    }

    private val labels = listOf(R.string.menu_disable_blocking, R.string.tab_context_move_to_workspace, R.string.tab_context_new_folder)

    @Test
    fun englishLabelsAreNotCutAtLargeFontScale() {
        show(allowlisted = false, fontScale = 1.3f)
        assertLabelsNotCut(labels)
    }

    @Test
    fun englishEnableBlockingIsNotCutAtLargeFontScale() {
        show(allowlisted = true, fontScale = 1.3f)
        assertLabelsNotCut(listOf(R.string.menu_enable_blocking))
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
    fun russianLabelsAreNotCutAtLargeFontScale() {
        show(allowlisted = false, fontScale = 1.3f)
        assertLabelsNotCut(labels)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
    fun russianEnableBlockingIsNotCutAtLargeFontScale() {
        show(allowlisted = true, fontScale = 1.3f)
        assertLabelsNotCut(listOf(R.string.menu_enable_blocking))
    }

    @Test
    fun defaultScaleShowsEveryLabelUncut() {
        show(allowlisted = false, fontScale = 1f)
        assertLabelsNotCut(labels)
    }
}
