package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.Tab

data class BrowserUiState(
    // Tabs & Sessions
    val tabs: List<Tab> = emptyList(),
    val recentTabs: List<com.jestk.browser.domain.model.RecentTabItem> = emptyList(),
    val activeTabId: String? = null,
    val recentlyClosedTab: Tab? = null,
    val showUndoTabCloseSnackbar: Boolean = false,
    // Active Viewport / Overlay
    val activeOverlay: OverlayType = OverlayType.NONE,
    val activeScreen: ScreenType = ScreenType.NONE,
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
    // Downloads
    val downloads: List<DownloadItem> = emptyList(),
    val lastDownloadNotification: DownloadItem? = null,
    val showDownloadSnackbar: Boolean = false,
    val activeDownloadIds: Set<Long> = emptySet(),
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
    val fullscreenRequestedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    val linkContextMenuUrl: String? = null,
    val selectedImageUrl: String? = null,
    val isDefaultBrowser: Boolean = true,
    val showAddFavoriteDialog: Boolean = false,
) {
    val activeTab: Tab?
        get() = tabs.find { it.id == activeTabId }

    val activeTabIndex: Int
        get() = if (tabs.isEmpty()) 0 else tabs.indexOfFirst { it.id == activeTabId }.coerceIn(0, tabs.lastIndex)

    val bottomBarVisible: Boolean
        get() = isBottomBarVisible

    val loadProgress: Int
        get() = (loadingProgress * 100).toInt()

    val isHome: Boolean
        get() =
            activeOverlay == OverlayType.HOME ||
                (
                    activeOverlay == OverlayType.NONE &&
                        currentUrl.isEmpty() &&
                        (activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank")
                )

    /**
     * Settings/Downloads are opened by [com.jestk.browser.ui.navigation.ActionDock] via
     * `activeScreen`, and the UI renders them from this predicate. Keeping the check here
     * prevents the screen state and the render condition from drifting apart again.
     */
    val isSettingsVisible: Boolean
        get() = activeScreen == ScreenType.SETTINGS || activeOverlay == OverlayType.SETTINGS

    val isDownloadsVisible: Boolean
        get() = activeScreen == ScreenType.DOWNLOADS || activeOverlay == OverlayType.DOWNLOADS
}
