package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.domain.model.ExternalAppPrompt
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
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.NavigationCommand
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Constructor dependencies and the state shared by all tab coordinator parts: the per-tab maps and
 * caches, the single tab operation mutex, and the helpers more than one part needs.
 */
internal class TabCoordinatorContext(
    val tabRepository: TabRepository,
    val recentTabRepository: RecentTabRepository?,
    val favoriteRepository: FavoriteRepository,
    val historyRepository: HistoryRepository,
    val webViewPool: WebViewPool?,
    val tabOperationMutex: Mutex,
    val tabThemeColors: ConcurrentHashMap<String, Int>,
    val getCurrentState: () -> BrowserUiState,
    val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    val emitNavigation: (NavigationCommand) -> Unit,
    val scope: CoroutineScope,
    val ioDispatcher: CoroutineDispatcher,
    val tabLoadingProgress: MutableMap<String, Int>,
    val dismissedExternalAppDomains: MutableMap<String, MutableSet<String>>,
    val pendingExternalAppPrompts: MutableMap<String, ExternalAppPrompt>,
    val timedOutExternalAppDomains: MutableMap<String, MutableSet<String>>,
    val tabBottomThemeColors: ConcurrentHashMap<String, Int>,
    val tabFolderRepository: TabFolderRepository?,
    val workspaceRepository: WorkspaceRepository?,
) {
    @Volatile
    var cachedAllTabs: List<Tab> = emptyList()

    @Volatile
    var cachedAllFolders: List<TabFolder> = emptyList()

    val recentlyClosedTabsByWorkspace = ConcurrentHashMap<String, Tab>()
    val activeWorkspaceIdFlow = MutableStateFlow(Workspace.DEFAULT_PERSONAL_ID)

    /**
     * A tab activated without a live WebView gets a fresh one that shows nothing until its first
     * frame; marking it up front lets the UI cover it with the tab snapshot from the first frame.
     */
    fun holdUntilFirstPaintIfRecreated(tabId: String, url: String) {
        val pool = webViewPool ?: return
        if (url.isEmpty() || url == "about:blank" || pool.getWebView(tabId) != null) return
        pool.markAwaitingFirstPaint(tabId)
    }

    fun checkAndPruneEmptyFolder(folderId: String, remainingTabs: List<Tab>) {
        val remainingInFolder = remainingTabs.filter { it.folderId == folderId }
        if (remainingInFolder.isEmpty()) {
            updateUiState { state ->
                state.copy(folders = state.folders.filter { it.id != folderId })
            }
            scope.launch(ioDispatcher) {
                tabFolderRepository?.deleteFolder(folderId)
            }
        }
    }
}
