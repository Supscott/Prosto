package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.OverlayType

private const val SCROLL_DELTA_THRESHOLD = 8f

/**
 * Dock visibility after a page scroll: scrolling down closes the dock menus; Home and the first
 * half-screen of a page always show the dock; below that, scrolling down hides it and scrolling
 * up shows it again.
 */
internal fun BrowserUiState.afterWebScroll(
    deltaY: Float,
    scrollY: Int = 0,
    viewportHeight: Int = 0,
): BrowserUiState {
    var state = this
    if (deltaY > SCROLL_DELTA_THRESHOLD && (state.isMenuExpanded || state.isSearchEngineSwitcherEditExpanded)) {
        state = state.copy(isMenuExpanded = false, isSearchEngineSwitcherEditExpanded = false)
    }

    val isHome = state.activeOverlay == OverlayType.HOME || state.currentUrl.isEmpty() || state.currentUrl == "about:blank"
    val isBelowHalfScreen = viewportHeight > 0 && scrollY > (viewportHeight / 2)
    return when {
        isHome || !isBelowHalfScreen -> if (state.isBottomBarVisible) state else state.copy(isBottomBarVisible = true)
        deltaY > SCROLL_DELTA_THRESHOLD -> if (!state.isBottomBarVisible) {
            state
        } else {
            state.copy(isBottomBarVisible = false, isMenuExpanded = false, isSearchEngineSwitcherEditExpanded = false)
        }
        deltaY < -SCROLL_DELTA_THRESHOLD -> if (state.isBottomBarVisible) state else state.copy(isBottomBarVisible = true)
        else -> state
    }
}
