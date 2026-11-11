package com.jestk.browser.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.TabFolderRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.domain.repository.WorkspaceRepository
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.tabs.ExternalAppPrompts
import com.jestk.browser.ui.viewmodel.tabs.PageAppearance
import com.jestk.browser.ui.viewmodel.tabs.PageEvents
import com.jestk.browser.ui.viewmodel.tabs.TabActivation
import com.jestk.browser.ui.viewmodel.tabs.TabClosing
import com.jestk.browser.ui.viewmodel.tabs.TabCoordinatorContext
import com.jestk.browser.ui.viewmodel.tabs.TabFavorites
import com.jestk.browser.ui.viewmodel.tabs.TabFolders
import com.jestk.browser.ui.viewmodel.tabs.TabOpening
import com.jestk.browser.ui.viewmodel.tabs.TabSessionSync
import com.jestk.browser.ui.viewmodel.tabs.Workspaces
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

private const val TAG = "TabCoordinator"

/**
 * Checks whether two URLs belong to the same top-level domain / host.
 */
internal fun isSameDomain(url1: String?, url2: String?): Boolean {
    if (url1.isNullOrBlank() || url2.isNullOrBlank()) return false
    if (url1 == "about:blank" || url2 == "about:blank") return false
    return try {
        val cleanUrl1 = if (url1.startsWith("http://") || url1.startsWith("https://")) url1 else "https://$url1"
        val cleanUrl2 = if (url2.startsWith("http://") || url2.startsWith("https://")) url2 else "https://$url2"
        val uri1 = Uri.parse(cleanUrl1)
        val uri2 = Uri.parse(cleanUrl2)
        val host1 = uri1.host?.lowercase()?.removePrefix("www.")
        val host2 = uri2.host?.lowercase()?.removePrefix("www.")
        !host1.isNullOrBlank() && host1 == host2
    } catch (e: Exception) {
        Log.w(TAG, "Failed to compare URL hosts", e)
        false
    }
}

/**
 * Coordinator for tab lifecycle (create, select, close, undo, reorder),
 * web navigation state handling, tab snapshots, favicons, theme colors, and favorites.
 */
internal class TabCoordinator(
    private val tabRepository: TabRepository,
    private val recentTabRepository: RecentTabRepository?,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
    private val webViewPool: WebViewPool?,
    private val tabOperationMutex: Mutex,
    private val tabThemeColors: ConcurrentHashMap<String, Int>,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    internal val tabLoadingProgress: MutableMap<String, Int> = ConcurrentHashMap(),
    internal val dismissedExternalAppDomains: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(),
    internal val pendingExternalAppPrompts: MutableMap<String, ExternalAppPrompt> = ConcurrentHashMap(),
    internal val timedOutExternalAppDomains: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(),
    internal val tabBottomThemeColors: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    private val tabFolderRepository: TabFolderRepository? = null,
    private val workspaceRepository: WorkspaceRepository? = null,
) {
    private val ctx = TabCoordinatorContext(
        tabRepository = tabRepository,
        recentTabRepository = recentTabRepository,
        favoriteRepository = favoriteRepository,
        historyRepository = historyRepository,
        webViewPool = webViewPool,
        tabOperationMutex = tabOperationMutex,
        tabThemeColors = tabThemeColors,
        getCurrentState = getCurrentState,
        updateUiState = updateUiState,
        emitNavigation = emitNavigation,
        scope = scope,
        ioDispatcher = ioDispatcher,
        tabLoadingProgress = tabLoadingProgress,
        dismissedExternalAppDomains = dismissedExternalAppDomains,
        pendingExternalAppPrompts = pendingExternalAppPrompts,
        timedOutExternalAppDomains = timedOutExternalAppDomains,
        tabBottomThemeColors = tabBottomThemeColors,
        tabFolderRepository = tabFolderRepository,
        workspaceRepository = workspaceRepository,
    )
    private val externalAppPrompts = ExternalAppPrompts(ctx)
    private val activation = TabActivation(ctx, externalAppPrompts)
    private val opening = TabOpening(ctx, activation)
    private val closing = TabClosing(ctx, activation)
    private val folders = TabFolders(ctx, activation, opening, closing)
    private val pageEvents = PageEvents(ctx)
    private val pageAppearance = PageAppearance(ctx)
    private val favorites = TabFavorites(ctx)
    private val workspaces = Workspaces(ctx, activation)
    private val sessionSync = TabSessionSync(ctx)

    fun handleExternalAppOffered(
        tabId: String,
        prompt: ExternalAppPrompt,
        userInitiated: Boolean = false
    ) = externalAppPrompts.handleExternalAppOffered(tabId, prompt, userInitiated)

    fun dismissExternalAppPrompt(reason: ExternalAppDismissReason = ExternalAppDismissReason.EXPLICIT) = externalAppPrompts.dismissExternalAppPrompt(reason)

    suspend fun restoreSession(
        initialSettings: BrowserSettings,
        currentEngines: List<SearchEngine>
    ) = sessionSync.restoreSession(initialSettings, currentEngines)

    fun observeDatabaseFlows() = sessionSync.observeDatabaseFlows()

    fun openNewTabForSearch(onSearchOpened: () -> Unit) = opening.openNewTabForSearch(onSearchOpened)

    fun openNewTab(url: String, openedByExternalApp: Boolean = false) = opening.openNewTab(url, openedByExternalApp)

    fun openPopupTab(tabId: String) = opening.openPopupTab(tabId)

    fun openTabInBackground(url: String) = opening.openTabInBackground(url)

    fun openRecentTab(
        item: RecentTabItem,
        onNavigateUrl: (String) -> Unit,
        onOpenNewTab: (String) -> Unit = { openNewTab(it) },
    ) = opening.openRecentTab(item, onNavigateUrl, onOpenNewTab)

    fun selectTab(tabId: String) = activation.selectTab(tabId)

    fun closeTab(tabId: String) = closing.closeTab(tabId)

    fun undoCloseTab() = closing.undoCloseTab()

    fun closeAllTabs() = closing.closeAllTabs()

    fun switchAdjacentTab(delta: Int) = activation.switchAdjacentTab(delta)

    fun reorderTabsAndFolders(
        orderedTabs: List<Tab>,
        orderedFolders: List<TabFolder>? = null,
    ) = folders.reorderTabsAndFolders(orderedTabs, orderedFolders)

    fun navigateToUrl(finalUrl: String, matchedShortcutEngine: SearchEngine? = null) = pageEvents.navigateToUrl(finalUrl, matchedShortcutEngine)

    fun handlePageStarted(tabId: String, url: String) = pageEvents.handlePageStarted(tabId, url)

    fun handlePageFinished(tabId: String, url: String, title: String) = pageEvents.handlePageFinished(tabId, url, title)

    fun handleTitleReceived(tabId: String, title: String) = pageEvents.handleTitleReceived(tabId, title)

    fun handleProgressChanged(tabId: String, progress: Int) = pageEvents.handleProgressChanged(tabId, progress)

    fun handleStopLoading(tabId: String? = null) = pageEvents.handleStopLoading(tabId)

    fun handleNavState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) = pageEvents.handleNavState(tabId, canGoBack, canGoForward)

    fun handleSnapshot(tabId: String, path: String) = pageAppearance.handleSnapshot(tabId, path)

    fun handleUpdateFavicon(tabId: String, faviconUrl: String?, pageUrl: String? = null) = pageAppearance.handleUpdateFavicon(tabId, faviconUrl, pageUrl)

    fun handleThemeColor(tabId: String, color: Int?) = pageAppearance.handleThemeColor(tabId, color)

    fun handleThemeColors(tabId: String, topColor: Int?, bottomColor: Int?) = pageAppearance.handleThemeColors(tabId, topColor, bottomColor)

    fun addCurrentToFavorites() = favorites.addCurrentToFavorites()

    fun addFavorite(title: String, url: String, workspaceId: String? = null) = favorites.addFavorite(title, url, workspaceId)

    fun deleteFavorite(id: Long) = favorites.deleteFavorite(id)

    suspend fun createFolder(tabId: String, title: String = "", color: Int): TabFolder = folders.createFolder(tabId, title, color)

    suspend fun createFolderFromTabs(tabId1: String, tabId2: String, color: Int? = null): TabFolder = folders.createFolderFromTabs(tabId1, tabId2, color)

    suspend fun updateFolder(folderId: String, title: String, color: Int) = folders.updateFolder(folderId, title, color)

    suspend fun toggleFolderCollapse(folderId: String, fromDock: Boolean = false) = folders.toggleFolderCollapse(folderId, fromDock)

    suspend fun addTabToFolder(tabId: String, folderId: String) = folders.addTabToFolder(tabId, folderId)

    suspend fun removeTabFromFolder(tabId: String) = folders.removeTabFromFolder(tabId)

    suspend fun ungroupFolder(folderId: String) = folders.ungroupFolder(folderId)

    suspend fun closeFolder(folderId: String) = folders.closeFolder(folderId)

    fun createTabInFolder(folderId: String) = folders.createTabInFolder(folderId)

    fun switchWorkspace(targetWorkspaceId: String) = workspaces.switchWorkspace(targetWorkspaceId)

    fun createWorkspace(name: String, color: Int) = workspaces.createWorkspace(name, color)

    fun updateWorkspace(workspace: Workspace) = workspaces.updateWorkspace(workspace)

    fun deleteWorkspace(workspaceId: String) = workspaces.deleteWorkspace(workspaceId)

    fun moveTabToWorkspace(tabId: String, targetWorkspaceId: String) = workspaces.moveTabToWorkspace(tabId, targetWorkspaceId)

    fun clearRecentlyClosedTab(workspaceId: String = getCurrentState().activeWorkspaceId) = closing.clearRecentlyClosedTab(workspaceId)

    fun moveFavoriteToWorkspace(id: Long, targetWorkspaceId: String) = favorites.moveFavoriteToWorkspace(id, targetWorkspaceId)
}
