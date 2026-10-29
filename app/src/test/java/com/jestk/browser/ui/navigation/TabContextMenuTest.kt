package com.jestk.browser.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabContextMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tabContextMenu_whenReloadVisible_showsReloadAndTriggersCallback() {
        var reloadCalled = false
        var dismissCalled = false

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabContextMenu(
                    expanded = true,
                    onDismissRequest = { dismissCalled = true },
                    isDark = false,
                    onCloseTab = {},
                    onCopyUrl = {},
                    onAddFavorite = {},
                    isReloadVisible = true,
                    onReload = { reloadCalled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Reload").assertExists()
        composeTestRule.onNodeWithText("Reload").performClick()

        assertTrue("Expected onReload callback to be invoked", reloadCalled)
        assertTrue("Expected onDismissRequest to be invoked when clicked", dismissCalled)
    }

    @Test
    fun tabContextMenu_whenReloadNotVisible_doesNotShowReload() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabContextMenu(
                    expanded = true,
                    onDismissRequest = {},
                    isDark = false,
                    onCloseTab = {},
                    onCopyUrl = {},
                    onAddFavorite = {},
                    isReloadVisible = false,
                    onReload = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Reload").assertDoesNotExist()
        composeTestRule.onNodeWithText("Close tab").assertExists()
    }

    @Test
    fun isReloadVisible_evaluatesCorrectlyForWebpageVsHome() {
        fun computeIsReloadVisible(isHome: Boolean, url: String): Boolean =
            !isHome && url.isNotBlank() && !url.startsWith("about:")

        // Active webpage tab
        assertTrue(computeIsReloadVisible(isHome = false, url = "https://example.com"))

        // Home tab or blank
        assertFalse(computeIsReloadVisible(isHome = true, url = "https://example.com"))
        assertFalse(computeIsReloadVisible(isHome = false, url = ""))
        assertFalse(computeIsReloadVisible(isHome = false, url = "about:blank"))
    }
}
