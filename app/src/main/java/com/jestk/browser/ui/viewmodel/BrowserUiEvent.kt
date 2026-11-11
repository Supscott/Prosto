package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.InsecureNavigationTarget
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace

sealed interface BrowserUiEvent {
    // Tab Management
    data class OpenNewTab(
        val url: String = "",
        /** A link another app sent us: Back at the start of its history returns to that app. */
        val openedByExternalApp: Boolean = false,
    ) : TabUiEvent

    data class OpenPopupTab(
        val tabId: String,
    ) : TabUiEvent

    data class OpenRecentTab(
        val item: com.jestk.browser.domain.model.RecentTabItem,
    ) : TabUiEvent

    data class SelectTab(
        val tabId: String,
    ) : TabUiEvent

    data class CloseTab(
        val tabId: String,
    ) : TabUiEvent

    data object CloseAllTabs : TabUiEvent

    data class ReorderTabs(
        val orderedTabs: List<Tab>,
        val orderedFolders: List<TabFolder>? = null,
    ) : TabUiEvent

    data object SwipeNextTab : TabUiEvent

    data object SwipePrevTab : TabUiEvent

    data object UndoCloseTab : TabUiEvent

    data object DismissUndoTabCloseSnackbar : TabUiEvent

    // Overlays
    data class OpenOverlay(
        val overlay: OverlayType,
    ) : OverlayUiEvent

    data object DismissOverlay : OverlayUiEvent

    data object OpenHome : OverlayUiEvent

    data class OpenSearch(
        val prefilledUrl: String? = null,
        val openInNewTab: Boolean = prefilledUrl.isNullOrEmpty(),
    ) : OverlayUiEvent

    data object OpenTabGrid : OverlayUiEvent

    data object OpenSettings : OverlayUiEvent

    data object OpenDownloads : OverlayUiEvent

    data class DeleteDownload(
        val id: Long,
        val deleteFile: Boolean = true,
    ) : SettingsUiEvent

    data class PauseDownload(
        val id: Long,
    ) : SettingsUiEvent

    data class ResumeDownload(
        val id: Long,
    ) : SettingsUiEvent

    data class CancelDownload(
        val id: Long,
    ) : SettingsUiEvent

    data class RetryDownload(
        val id: Long,
    ) : SettingsUiEvent

    data object DismissDownloadSnackbar : SettingsUiEvent

    // Web Navigation & Actions
    data class LoadUrl(
        val url: String,
    ) : PageUiEvent

    data class SubmitSearch(
        val query: String,
    ) : PageUiEvent

    data class UpdateSearchQuery(
        val query: String,
    ) : PageUiEvent

    data object GoBack : PageUiEvent

    data object GoForward : PageUiEvent

    data object Reload : PageUiEvent

    data object StopLoading : PageUiEvent

    data class SwitchSearchEngineForQuery(
        val targetEngine: SearchEngine,
        val query: String? = null,
    ) : PageUiEvent

    // Web Callbacks
    data class OnPageStarted(
        val tabId: String,
        val url: String,
    ) : PageUiEvent

    data class OnPageFinished(
        val tabId: String,
        val url: String,
        val title: String,
    ) : PageUiEvent

    data class OnTitleReceived(
        val tabId: String,
        val title: String,
    ) : PageUiEvent

    data class OnProgressChanged(
        val tabId: String,
        val progress: Int,
    ) : PageUiEvent

    data class OnNavigationStateChanged(
        val tabId: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : PageUiEvent

    data class OnSnapshotCaptured(
        val tabId: String,
        val snapshotPath: String,
    ) : PageUiEvent

    data class UpdateFaviconUrl(
        val tabId: String,
        val faviconUrl: String?,
        val pageUrl: String? = null,
    ) : PageUiEvent

    data class OnThemeColorReceived(
        val tabId: String,
        val color: Int?,
    ) : PageUiEvent

    data class OnThemeColorsReceived(
        val tabId: String,
        val topColor: Int?,
        val bottomColor: Int?,
    ) : PageUiEvent

    // Favorites & History
    data object ShowAddFavoriteDialog : LibraryUiEvent

    data object DismissAddFavoriteDialog : LibraryUiEvent

    data object AddCurrentTabToFavorites : LibraryUiEvent

    data class AddFavorite(
        val title: String,
        val url: String,
    ) : LibraryUiEvent

    data class RemoveFavorite(
        val id: Long,
    ) : LibraryUiEvent

    data object ToggleFavoritesExpanded : LibraryUiEvent

    data object ToggleSearchHistoryExpanded : LibraryUiEvent

    data class SetSearchHistoryExpanded(
        val expanded: Boolean,
    ) : LibraryUiEvent

    data object LoadMoreSearchHistory : LibraryUiEvent

    data object ClearAllHistory : LibraryUiEvent

    // Settings
    data class SetThemeMode(
        val mode: ThemeMode,
    ) : SettingsUiEvent

    data class SetSearchEngine(
        val engine: SearchEngine,
    ) : SettingsUiEvent

    data class SetAdBlockerEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetAdRedirectProtectionEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetAutoConsentEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetCleanYouTubePlayerEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetTrackingProtectionEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetVibrationEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class SetGrayUnloadedTabsEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    // Update
    data class SetAutoUpdateEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    data class CheckForUpdate(
        val manual: Boolean = false,
    ) : SettingsUiEvent

    data object RedirectToUpdate : SettingsUiEvent

    data object SkipUpdate : SettingsUiEvent

    data object DismissUpdate : SettingsUiEvent

    // Search Engine Management
    data class ShowAddSearchEngineDialog(
        val engineToEdit: SearchEngine? = null,
    ) : LibraryUiEvent

    data object DismissAddSearchEngineDialog : LibraryUiEvent

    data class SaveSearchEngine(
        val id: String?,
        val title: String,
        val shortcut: String?,
        val urlTemplate: String,
    ) : LibraryUiEvent

    data class DeleteSearchEngine(
        val id: String,
    ) : LibraryUiEvent

    data class ReorderSearchEngines(
        val orderedEngines: List<SearchEngine>,
    ) : LibraryUiEvent

    data class ToggleSearchPageEngineVisibility(
        val engineId: String,
        val isVisible: Boolean,
    ) : LibraryUiEvent

    data class SetSearchEngineSwitcherEditExpanded(
        val expanded: Boolean,
    ) : LibraryUiEvent

    // Dynamic UI Translation
    data class OnWebScroll(
        val deltaY: Float,
        val scrollY: Int = 0,
        val viewportHeight: Int = 0,
    ) : OverlayUiEvent

    data class SetBottomBarVisibility(
        val visible: Boolean,
    ) : OverlayUiEvent

    data class ToggleMenu(
        val expanded: Boolean? = null,
    ) : OverlayUiEvent

    data class SetMenuExpanded(
        val expanded: Boolean,
    ) : OverlayUiEvent

    // Media & Fullscreen
    data class SetVideoPlaying(
        val isPlaying: Boolean,
    ) : MediaUiEvent

    data class SetFullscreenCustomView(
        val view: android.view.View?,
        val isFullscreen: Boolean,
        val requestedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    ) : MediaUiEvent

    data class UpdateFullscreenOrientation(
        val orientation: Int,
    ) : MediaUiEvent

    data object ExitFullscreen : MediaUiEvent

    // Clean YouTube Player
    data class OpenCleanPlayer(
        val videoId: String,
        val originalUrl: String,
        val startSeconds: Int = 0,
    ) : MediaUiEvent

    data object CloseCleanPlayer : MediaUiEvent

    data class OpenCleanPlayerOnWeb(
        val url: String,
    ) : MediaUiEvent

    // System Back Handling
    data object HandleBackPress : BrowserUiEvent

    // Link Context Menu
    data class ShowLinkContextMenu(
        val url: String,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
    ) : OverlayUiEvent

    data object DismissLinkContextMenu : OverlayUiEvent

    data class OpenTabInBackground(
        val url: String,
    ) : TabUiEvent

    // Default Browser
    data class UpdateDefaultBrowserStatus(
        val isDefault: Boolean,
    ) : SettingsUiEvent

    // Image Context Menu
    data class ShowImageContextMenu(
        val imageUrl: String,
        val linkUrl: String? = null,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
    ) : OverlayUiEvent

    data object DismissImageContextMenu : OverlayUiEvent

    data class CopyImageUrl(
        val imageUrl: String,
    ) : OverlayUiEvent

    data class DownloadImage(
        val imageUrl: String,
    ) : OverlayUiEvent

    data class PreviewImageInNewTab(
        val imageUrl: String,
    ) : OverlayUiEvent

    // External App Prompts
    data class OnExternalAppOffered(
        val tabId: String,
        val prompt: com.jestk.browser.domain.model.ExternalAppPrompt,
        val userInitiated: Boolean = false,
    ) : SafetyUiEvent

    data class DismissExternalAppPrompt(
        val reason: ExternalAppDismissReason = ExternalAppDismissReason.EXPLICIT,
    ) : SafetyUiEvent

    data class OpenExternalApp(
        val prompt: com.jestk.browser.domain.model.ExternalAppPrompt,
    ) : SafetyUiEvent

    data class SetOpenInAppsEnabled(
        val enabled: Boolean,
    ) : SettingsUiEvent

    // Insecure HTTP Warning
    data class ShowInsecureHttpWarning(
        val url: String,
        val tabId: String? = null,
        val target: InsecureNavigationTarget = InsecureNavigationTarget.ACTIVE_TAB,
    ) : SafetyUiEvent

    data object ConfirmInsecureHttpNavigation : SafetyUiEvent

    data object DismissInsecureHttpWarning : SafetyUiEvent

    // Blocked Ad Navigation
    data class ShowBlockedAdNavigation(
        val url: String,
        val tabId: String,
    ) : SafetyUiEvent

    data object AllowBlockedAdNavigation : SafetyUiEvent

    data object DismissBlockedAdNavigation : SafetyUiEvent

    // Feedback & Community Rating
    data object DismissFeedbackPrompt : SettingsUiEvent

    data class SnoozeFeedbackPrompt(
        val durationMs: Long = 3 * 24 * 60 * 60 * 1000L,
    ) : SettingsUiEvent

    data object OpenFeedbackSheet : SettingsUiEvent

    data object CloseFeedbackSheet : SettingsUiEvent

    // Tab Folders
    data class CreateFolderFromTabs(
        val tabId1: String,
        val tabId2: String,
        val color: Int? = null,
    ) : TabUiEvent

    data class OpenCreateFolderSheet(
        val tabId: String,
    ) : TabUiEvent

    data class OpenEditFolderSheet(
        val folderId: String,
    ) : TabUiEvent

    data class SaveFolder(
        val title: String,
        val color: Int,
    ) : TabUiEvent

    data object DismissFolderSheet : TabUiEvent

    data class ToggleFolderCollapse(
        val folderId: String,
        val fromDock: Boolean = true,
    ) : TabUiEvent

    data class CloseFolder(
        val folderId: String,
    ) : TabUiEvent

    data class UngroupFolder(
        val folderId: String,
    ) : TabUiEvent

    data class AddTabToFolder(
        val tabId: String,
        val folderId: String,
    ) : TabUiEvent

    data class RemoveTabFromFolder(
        val tabId: String,
    ) : TabUiEvent

    data class CreateTabInFolder(
        val folderId: String,
    ) : TabUiEvent

    // Workspaces
    data object OpenWorkspacesOverview : TabUiEvent

    data object CloseWorkspacesOverview : TabUiEvent

    data class SwitchWorkspace(
        val workspaceId: String,
    ) : TabUiEvent

    data class CreateWorkspace(
        val name: String,
        val color: Int,
    ) : TabUiEvent

    data class UpdateWorkspace(
        val workspace: Workspace,
    ) : TabUiEvent

    data class DeleteWorkspace(
        val workspaceId: String,
    ) : TabUiEvent

    data class MoveTabToWorkspace(
        val tabId: String,
        val targetWorkspaceId: String,
    ) : TabUiEvent

    data class MoveFavoriteToWorkspace(
        val favoriteId: Long,
        val targetWorkspaceId: String,
    ) : LibraryUiEvent
}

enum class ExternalAppDismissReason {
    /** User explicitly dismissed the prompt (e.g. tapped close button). Suppresses the domain until navigation. */
    EXPLICIT,

    /** Prompt timed out automatically. Suppresses only passive offers; deliberate taps bypass it. */
    TIMEOUT,

    /** Transient dismissal without remembering suppression. */
    TRANSIENT,
}
