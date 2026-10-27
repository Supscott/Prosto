package com.jestk.browser.ui.overlays

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenTabSwitchCarouselTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun openTabSwitchCarousel_displaysMatchingTabs_andInvokesClick() {
        val tabs = listOf(
            Tab(id = "tab-1", title = "YouTube Video", url = "https://youtube.com/watch?v=123"),
            Tab(id = "tab-2", title = "GitHub Repo", url = "https://github.com/torvalds/linux"),
        )
        var clickedTab: Tab? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                OpenTabSwitchCarousel(
                    tabs = tabs,
                    isDark = false,
                    onTabClick = { clickedTab = it },
                )
            }
        }

        // Verify title & domain exist
        composeTestRule.onNodeWithText("YouTube Video").assertExists()
        composeTestRule.onNodeWithText("youtube.com").assertExists()

        // Verify badge exists
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Switch"))[0].assertExists()

        // Click on the first tab
        composeTestRule.onNodeWithText("YouTube Video").performClick()
        assertEquals("tab-1", clickedTab?.id)
    }
}
