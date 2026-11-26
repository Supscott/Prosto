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
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The three-dots menu grows to its widest label, so no label is cut with an ellipsis (PRO-187). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ActionPopupMenuWidthTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val page = "https://example.com/a"

    private fun state(
        saved: Boolean,
        blockingOff: Boolean,
    ) = BrowserUiState(
        currentUrl = page,
        pageTitle = "Example",
        tabs = listOf(Tab(id = "t", url = page, title = "Example", isActive = true)),
        activeTabId = "t",
        favorites = if (saved) listOf(FavoriteItem(id = 1, title = "Example", url = page)) else emptyList(),
        siteAllowlist = if (blockingOff) listOf(SiteAllowlistEntry("example.com", null, 1L)) else emptyList(),
    )

    private fun show(
        state: BrowserUiState,
        fontScale: Float,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        ActionPopupMenu(expanded = true, uiState = state, isDark = false, onEvent = {})
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

    private val savedAndBlocking = listOf(R.string.menu_remove_from_favorites, R.string.menu_disable_blocking)
    private val unsavedAndAllowed = listOf(R.string.menu_add_to_favorites, R.string.menu_enable_blocking)

    @Test
    fun englishLabelsAreNotCutAtLargeFontScale() {
        show(state(saved = true, blockingOff = false), fontScale = 1.3f)
        assertLabelsNotCut(savedAndBlocking)
    }

    @Test
    fun englishEnableBlockingIsNotCutAtLargeFontScale() {
        show(state(saved = false, blockingOff = true), fontScale = 1.3f)
        assertLabelsNotCut(unsavedAndAllowed)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
    fun russianLabelsAreNotCutAtLargeFontScale() {
        show(state(saved = true, blockingOff = false), fontScale = 1.3f)
        assertLabelsNotCut(savedAndBlocking)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
    fun russianEnableBlockingIsNotCutAtLargeFontScale() {
        show(state(saved = false, blockingOff = true), fontScale = 1.3f)
        assertLabelsNotCut(unsavedAndAllowed)
    }

    @Test
    fun defaultScaleShowsEveryLabelUncut() {
        show(state(saved = false, blockingOff = false), fontScale = 1f)
        assertLabelsNotCut(listOf(R.string.menu_add_to_favorites, R.string.menu_disable_blocking))
    }
}
