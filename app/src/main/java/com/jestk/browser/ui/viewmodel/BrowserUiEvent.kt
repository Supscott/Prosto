package com.jestk.browser.ui.viewmodel

import android.content.Context
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode

sealed interface BrowserUiEvent {
    // Tab Management
    data class OpenNewTab(val url: String = "") : BrowserUiEvent
    data class OpenRecentTab(val item: com.jestk.browser.domain.model.RecentTabItem) : BrowserUiEvent
    data class SelectTab(val tabId: String) : BrowserUiEvent
    data class CloseTab(val tabId: String) : BrowserUiEvent
    data object CloseAllTabs : BrowserUiEvent
    data class ReorderTabs(val orderedTabs: List<Tab>) : BrowserUiEvent
    data object SwipeNextTab : BrowserUiEvent
    data object SwipePrevTab : BrowserUiEvent
    data object UndoCloseTab : BrowserUiEvent
    data object DismissUndoTabCloseSnackbar : BrowserUiEvent

    // Overlays
    data class OpenOverlay(val overlay: OverlayType) : BrowserUiEvent
    data object DismissOverlay : BrowserUiEvent
    data object OpenHome : BrowserUiEvent
    data class OpenSearch(
        val prefilledUrl: String? = null,
        val openInNewTab: Boolean = prefilledUrl.isNullOrEmpty()
    ) : BrowserUiEvent
    data object OpenTabGrid : BrowserUiEvent
    data object OpenSettings : BrowserUiEvent
    data object OpenDownloads : BrowserUiEvent
    data class DeleteDownload(val id: Long) : BrowserUiEvent

    // Web Navigation & Actions
    data class LoadUrl(val url: String) : BrowserUiEvent
    data class SubmitSearch(val query: String) : BrowserUiEvent
    data class UpdateSearchQuery(val query: String) : BrowserUiEvent
    data object GoBack : BrowserUiEvent
    data object GoForward : BrowserUiEvent
    data object Reload : BrowserUiEvent
    data object StopLoading : BrowserUiEvent
    data class SwitchSearchEngineForQuery(val targetEngine: SearchEngine, val query: String? = null) : BrowserUiEvent

    // Web Callbacks
    data class OnPageStarted(val tabId: String, val url: String) : BrowserUiEvent
    data class OnPageFinished(val tabId: String, val url: String, val title: String) : BrowserUiEvent
    data class OnProgressChanged(val tabId: String, val progress: Int) : BrowserUiEvent
    data class OnNavigationStateChanged(val tabId: String, val canGoBack: Boolean, val canGoForward: Boolean) : BrowserUiEvent
    data class OnSnapshotCaptured(val tabId: String, val snapshotPath: String) : BrowserUiEvent
    data class OnFaviconReceived(val tabId: String, val icon: android.graphics.Bitmap) : BrowserUiEvent
    data class UpdateFaviconUrl(val tabId: String, val faviconUrl: String?) : BrowserUiEvent
    data class OnThemeColorReceived(val tabId: String, val color: Int?) : BrowserUiEvent

    // Favorites & History
    data object ShowAddFavoriteDialog : BrowserUiEvent
    data object DismissAddFavoriteDialog : BrowserUiEvent
    data object AddCurrentTabToFavorites : BrowserUiEvent
    data class AddFavorite(val title: String, val url: String) : BrowserUiEvent
    data class RemoveFavorite(val id: Long) : BrowserUiEvent
    data object ToggleFavoritesExpanded : BrowserUiEvent
    data object ToggleSearchHistoryExpanded : BrowserUiEvent
    data class SetSearchHistoryExpanded(val expanded: Boolean) : BrowserUiEvent
    data object LoadMoreSearchHistory : BrowserUiEvent
    data class DeleteHistoryItem(val id: Long) : BrowserUiEvent
    data object ClearAllHistory : BrowserUiEvent
    data class SelectHistorySuggestion(val item: HistoryItem) : BrowserUiEvent

    // Settings
    data class SetThemeMode(val mode: ThemeMode) : BrowserUiEvent
    data class SetSearchEngine(val engine: SearchEngine) : BrowserUiEvent
    data class SetAdBlockerEnabled(val enabled: Boolean) : BrowserUiEvent

    // Search Engine Management
    data class ShowAddSearchEngineDialog(val engineToEdit: SearchEngine? = null) : BrowserUiEvent
    data object DismissAddSearchEngineDialog : BrowserUiEvent
    data class SaveSearchEngine(
        val id: String?,
        val title: String,
        val shortcut: String?,
        val urlTemplate: String
    ) : BrowserUiEvent
    data class DeleteSearchEngine(val id: String) : BrowserUiEvent
    data class ReorderSearchEngines(val orderedEngines: List<SearchEngine>) : BrowserUiEvent

    // Dynamic UI Translation
    data class OnWebScroll(
        val deltaY: Float,
        val scrollY: Int = 0,
        val viewportHeight: Int = 0,
    ) : BrowserUiEvent
    data class SetBottomBarVisibility(val visible: Boolean) : BrowserUiEvent
    data class ToggleMenu(val expanded: Boolean? = null) : BrowserUiEvent
    data class SetMenuExpanded(val expanded: Boolean) : BrowserUiEvent

    // Media & Fullscreen
    data class SetVideoPlaying(val isPlaying: Boolean) : BrowserUiEvent
    data class SetFullscreenCustomView(
        val view: Any?,
        val isFullscreen: Boolean,
        val requestedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ) : BrowserUiEvent
    data object ExitFullscreen : BrowserUiEvent

    // System Back Handling
    data object HandleBackPress : BrowserUiEvent

    // Link Context Menu
    data class ShowLinkContextMenu(
        val url: String,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
    ) : BrowserUiEvent
    data object DismissLinkContextMenu : BrowserUiEvent
    data class OpenTabInBackground(val url: String) : BrowserUiEvent

    // Default Browser
    data class UpdateDefaultBrowserStatus(val isDefault: Boolean) : BrowserUiEvent
    data class CheckDefaultBrowser(val context: Context) : BrowserUiEvent

    // Image Context Menu
    data class ShowImageContextMenu(
        val imageUrl: String,
        val linkUrl: String? = null,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
    ) : BrowserUiEvent
    data object DismissImageContextMenu : BrowserUiEvent
    data class CopyImageUrl(val imageUrl: String) : BrowserUiEvent
    data class DownloadImage(val imageUrl: String) : BrowserUiEvent
    data class PreviewImageInNewTab(val imageUrl: String) : BrowserUiEvent
}
