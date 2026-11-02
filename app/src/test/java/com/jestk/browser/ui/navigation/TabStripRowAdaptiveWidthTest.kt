package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabStripRowAdaptiveWidthTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private val testVibration = object : VibrationFeedback {
        override fun tick() {}
        override fun confirm() {}
    }

    private val testUiState = mutableStateOf<BrowserUiState?>(null)

    private fun render(
        tabs: List<Tab>,
        activeTabId: String,
        dockWidth: Dp = 360.dp,
    ) {
        events.clear()
        val active = tabs.first { it.id == activeTabId }
        testUiState.value = BrowserUiState(
            tabs = tabs,
            activeTabId = activeTabId,
            currentUrl = active.url,
            pageTitle = active.title,
        )
        composeRule.setContent {
            val state = testUiState.value ?: return@setContent
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .width(dockWidth)
                            .height(54.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = { events += it },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun shortTitle_adaptsWidthSnuglyAroundContent() {
        val dockWidth = 360.dp
        val maxAllowedWidth = dockWidth * 0.45f // 162.dp

        val tabs = listOf(
            Tab(id = "tab1", title = "Home", url = "", isActive = true),
            Tab(id = "tab2", title = "Tab 2", url = "https://example.com", isActive = false),
        )
        render(tabs = tabs, activeTabId = "tab1", dockWidth = dockWidth)

        val pillNode = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true)
        val bounds = pillNode.fetchSemanticsNode().boundsInRoot
        val pillWidthDp = bounds.width / 3f // density 3.0

        // Adaptive width for "Home" should be compact: >= 80.dp and significantly less than max 162.dp
        assertThat(pillWidthDp).isAtLeast(80f)
        assertThat(pillWidthDp).isLessThan(maxAllowedWidth.value - 20f)
    }

    @Test
    fun webTabWithLongTitleAndShortDomain_adaptsToShortDomain() {
        val dockWidth = 360.dp
        val maxAllowedWidthDp = (dockWidth * 0.45f).value // 162.dp

        // X.com with actual website title "X. It’s what’s happening / X"
        val fullTitle = "X. It’s what’s happening / X"
        val tabs = listOf(
            Tab(id = "tab1", title = fullTitle, url = "https://x.com", isActive = true),
            Tab(id = "tab2", title = "Tab 2", url = "https://example.com", isActive = false),
        )
        render(tabs = tabs, activeTabId = "tab1", dockWidth = dockWidth)

        val pillNode = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true)
        val bounds = pillNode.fetchSemanticsNode().boundsInRoot
        val pillWidthDp = bounds.width / 3f

        // Should adapt to short domain ("x.com") and be well below maxAllowedWidthDp (162.dp), around 110-115 dp
        assertThat(pillWidthDp).isAtLeast(80f)
        assertThat(pillWidthDp).isLessThan(maxAllowedWidthDp - 30f)
    }

    @Test
    fun longTitle_clampedAtMaximumWidth() {
        val dockWidth = 360.dp
        val maxAllowedWidthDp = (dockWidth * 0.45f).value // 162.dp

        val longTitle = "A Very Long Page Title That Definitely Exceeds The Maximum Allowed Tab Width Capsule Limit In Prosto Browser"
        val tabs = listOf(
            Tab(id = "tab1", title = longTitle, url = "https://very-long-domain-name-that-also-exceeds-the-width.example.com", isActive = true),
            Tab(id = "tab2", title = "Tab 2", url = "https://example.com", isActive = false),
        )
        render(tabs = tabs, activeTabId = "tab1", dockWidth = dockWidth)

        val pillNode = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true)
        val bounds = pillNode.fetchSemanticsNode().boundsInRoot
        val pillWidthDp = bounds.width / 3f

        // Long title must be capped at 45% maximum width
        assertThat(pillWidthDp).isWithin(2f).of(maxAllowedWidthDp)
    }

    @Test
    fun closeButton_isDedicatedVisibleAndClickable() {
        val tabs = listOf(
            Tab(id = "tab1", title = "Short", url = "https://short.com", isActive = true),
            Tab(id = "tab2", title = "Tab 2", url = "https://example.com", isActive = false),
        )
        render(tabs = tabs, activeTabId = "tab1")

        val pillBounds = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val closeBtn = composeRule.onNodeWithContentDescription("Close tab", useUnmergedTree = true)
        val closeBtnBounds = closeBtn.fetchSemanticsNode().boundsInRoot

        // Close button must be placed inside the pill and towards the right edge
        assertThat(closeBtnBounds.right).isAtMost(pillBounds.right)
        assertThat(closeBtnBounds.left).isGreaterThan(pillBounds.left + pillBounds.width * 0.5f)

        // Close button must be clickable and trigger CloseTab event
        closeBtn.performClick()
        assertThat(events).contains(BrowserUiEvent.CloseTab("tab1"))
    }

    @Test
    fun tabSwitch_updatesActiveTabWidthDynamically() {
        val dockWidth = 360.dp
        val longTitle = "A Very Long Page Title That Definitely Exceeds The Maximum Allowed Tab Width Capsule Limit In Prosto Browser"
        val tabLong = Tab(id = "tab1", title = longTitle, url = "https://very-long-domain.example.com", isActive = true)
        val tabShort = Tab(id = "tab2", title = "VK", url = "https://vk.com", isActive = false)
        val tabs = listOf(tabLong, tabShort)

        render(tabs = tabs, activeTabId = "tab1", dockWidth = dockWidth)

        val longPillNode = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true)
        val initialLongWidthDp = longPillNode.fetchSemanticsNode().boundsInRoot.width / 3f
        assertThat(initialLongWidthDp).isWithin(2f).of((dockWidth * 0.45f).value)

        // Switch to short tab
        testUiState.value = BrowserUiState(
            tabs = listOf(tabLong.copy(isActive = false), tabShort.copy(isActive = true)),
            activeTabId = "tab2",
            currentUrl = tabShort.url,
            pageTitle = tabShort.title,
        )
        composeRule.waitForIdle()

        val shortPillNode = composeRule.onNodeWithTag("tab_tab2", useUnmergedTree = true)
        val newShortWidthDp = shortPillNode.fetchSemanticsNode().boundsInRoot.width / 3f

        // Active tab capsule width dynamically adapts and shrinks for the newly selected short tab
        assertThat(newShortWidthDp).isLessThan(initialLongWidthDp - 20f)
        assertThat(newShortWidthDp).isAtLeast(80f)
    }
}
