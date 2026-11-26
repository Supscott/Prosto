package com.jestk.browser.ui.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The three-dots menu keeps Home, Tabs, Downloads and Settings; its rows read once to TalkBack (PRO-194, PRO-192). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionPopupMenuItemsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show() {
        val page = "https://example.com/a"
        val state =
            BrowserUiState(
                currentUrl = page,
                pageTitle = "Page",
                tabs = listOf(Tab(id = "t", url = page, title = "Page", isActive = true)),
                activeTabId = "t",
            )
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                ActionPopupMenu(expanded = true, uiState = state, isDark = false, onEvent = {})
            }
        }
    }

    @Test
    fun keepsOnlyHomeTabsDownloadsAndSettings() {
        show()

        for (id in listOf(R.string.tab_home_title, R.string.menu_tabs, R.string.downloads_title, R.string.settings)) {
            composeRule.onNodeWithText(string(id)).assertExists()
        }
        for (id in listOf(R.string.dock_search, R.string.menu_add_to_favorites, R.string.menu_remove_from_favorites, R.string.menu_disable_blocking, R.string.menu_enable_blocking)) {
            composeRule.onNodeWithText(string(id)).assertDoesNotExist()
        }
    }

    @Test
    fun rowIsOneButtonNodeWithTheIconLeftDecorative() {
        show()

        val label = string(R.string.settings)
        composeRule.onAllNodesWithContentDescription(label).assertCountEquals(0)
        composeRule.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
