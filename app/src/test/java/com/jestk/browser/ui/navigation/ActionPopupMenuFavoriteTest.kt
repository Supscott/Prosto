package com.jestk.browser.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ToggleCurrentPageFavorite
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The "Add to favorites" / "Remove from favorites" row of the three-dots menu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionPopupMenuFavoriteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(state: BrowserUiState) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                ActionPopupMenu(expanded = true, uiState = state, isDark = false, onEvent = { events += it })
            }
        }
    }

    private fun pageState(
        url: String,
        favorites: List<FavoriteItem> = emptyList(),
        overlay: OverlayType = OverlayType.NONE,
    ) = BrowserUiState(
        currentUrl = url,
        pageTitle = "Page",
        tabs = listOf(Tab(id = "t", url = url, title = "Page", isActive = true)),
        activeTabId = "t",
        activeOverlay = overlay,
        favorites = favorites,
    )

    @Test
    fun webPageOffersAddAndTapSendsTheToggle() {
        show(pageState("https://example.com/a"))

        composeRule.onNodeWithText(string(R.string.menu_add_to_favorites)).performClick()

        assertThat(events).contains(ToggleCurrentPageFavorite)
        assertThat(events).contains(BrowserUiEvent.SetMenuExpanded(false))
    }

    @Test
    fun savedPageOffersRemove() {
        val saved = FavoriteItem(id = 7, title = "Page", url = "https://example.com/a", workspaceId = Workspace.DEFAULT_PERSONAL_ID)
        show(pageState("https://example.com/a", favorites = listOf(saved)))

        composeRule.onNodeWithText(string(R.string.menu_remove_from_favorites)).assertExists()
        composeRule.onNodeWithText(string(R.string.menu_add_to_favorites)).assertDoesNotExist()
    }

    @Test
    fun homeAndInternalPagesHaveNoFavoriteRow() {
        show(pageState("", overlay = OverlayType.HOME))

        composeRule.onNodeWithText(string(R.string.menu_add_to_favorites)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.menu_remove_from_favorites)).assertDoesNotExist()
    }

    @Test
    fun nonWebUrlHasNoFavoriteRow() {
        show(pageState("chrome://version"))

        composeRule.onNodeWithText(string(R.string.menu_add_to_favorites)).assertDoesNotExist()
    }
}
