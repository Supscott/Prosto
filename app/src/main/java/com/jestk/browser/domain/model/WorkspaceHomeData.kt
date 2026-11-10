package com.jestk.browser.domain.model

/**
 * Preview-relevant home data for a single workspace.
 *
 * Collected by the ViewModel for every workspace so overview cards can render
 * the real Home page (favorites, recent tabs) instead of a mockup or a
 * screenshot of whatever workspace is currently active.
 */
data class WorkspaceHomeData(
    val favorites: List<FavoriteItem> = emptyList(),
    val recentTabs: List<RecentTabItem> = emptyList(),
)
