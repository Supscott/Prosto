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
    // Callers use the parts directly: `tabCoordinator.opening.openNewTab(url)`.
    val externalAppPrompts = ExternalAppPrompts(ctx)
    val activation = TabActivation(ctx, externalAppPrompts)
    val opening = TabOpening(ctx, activation)
    val closing = TabClosing(ctx, activation)
    val folders = TabFolders(ctx, activation, opening, closing)
    val pageEvents = PageEvents(ctx)
    val pageAppearance = PageAppearance(ctx)
    val favorites = TabFavorites(ctx)
    val workspaces = Workspaces(ctx, activation)
    val sessionSync = TabSessionSync(ctx)
}
