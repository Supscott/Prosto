package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab

data class BrowserUiState(
    // Tabs & Sessions
    val tabs: List<Tab> = emptyList(),
    val recentTabs: List<com.jestk.browser.domain.model.RecentTabItem> = emptyList(),
    val activeTabId: String? = null,

    // Active Viewport / Overlay
    val activeOverlay: OverlayType = OverlayType.NONE,

    // Web Navigation & Progress (for active tab)
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,

    // Search Overlay State
    val searchQuery: String = "",
    val searchHistorySuggestions: List<HistoryItem> = emptyList(),

    // Favorites & Recents
    val favorites: List<FavoriteItem> = emptyList(),
    val isFavoritesExpanded: Boolean = false,

    // Settings
    val settings: BrowserSettings = BrowserSettings(),

    // UI Dynamics & Gestures
    val isBottomBarVisible: Boolean = true,
    val bottomBarOffsetY: Float = 0f,
    val activeThemeColor: Int? = null,
    val isMenuExpanded: Boolean = false,

    // Media & Fullscreen
    val isVideoPlaying: Boolean = false,
    val isFullscreenCustomView: Boolean = false,
    val fullscreenCustomView: Any? = null,
    val fullscreenRequestedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
) {
    val activeTab: Tab?
        get() = tabs.find { it.id == activeTabId }

    val activeTabIndex: Int
        get() = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    val bottomBarVisible: Boolean
        get() = isBottomBarVisible

    val loadProgress: Int
        get() = (loadingProgress * 100).toInt()
}
