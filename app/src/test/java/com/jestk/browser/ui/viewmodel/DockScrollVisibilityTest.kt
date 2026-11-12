package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertWithMessage
import com.jestk.browser.domain.model.OverlayType
import org.junit.Test

class DockScrollVisibilityTest {
    private val page = BrowserUiState(currentUrl = "https://example.com", activeOverlay = OverlayType.NONE, isBottomBarVisible = true)

    private data class Case(val name: String, val state: BrowserUiState, val deltaY: Float, val scrollY: Int, val dockVisible: Boolean, val menuOpen: Boolean = false)

    @Test
    fun afterWebScroll_table() {
        val cases = listOf(
            Case("down below half-screen hides the dock and its menu", page.copy(isMenuExpanded = true), 20f, 1500, dockVisible = false),
            Case("down in the first half-screen keeps the dock", page, 20f, 300, dockVisible = true),
            Case("up below half-screen shows the dock", page.copy(isBottomBarVisible = false), -20f, 1500, dockVisible = true),
            Case("back in the first half-screen shows the dock", page.copy(isBottomBarVisible = false), 20f, 100, dockVisible = true),
            Case("tiny scroll changes nothing", page.copy(isBottomBarVisible = false), 3f, 1500, dockVisible = false),
            Case("Home always shows the dock", page.copy(activeOverlay = OverlayType.HOME, isBottomBarVisible = false), 20f, 1500, dockVisible = true),
            Case("down closes the menu even when the dock stays", page.copy(isMenuExpanded = true), 20f, 300, dockVisible = true),
            Case("up keeps an open menu", page.copy(isMenuExpanded = true), -20f, 300, dockVisible = true, menuOpen = true),
        )
        for (case in cases) {
            val result = case.state.afterWebScroll(case.deltaY, case.scrollY, viewportHeight = 2000)
            assertWithMessage("${case.name}: dock").that(result.isBottomBarVisible).isEqualTo(case.dockVisible)
            assertWithMessage("${case.name}: menu").that(result.isMenuExpanded).isEqualTo(case.menuOpen)
        }
    }
}
