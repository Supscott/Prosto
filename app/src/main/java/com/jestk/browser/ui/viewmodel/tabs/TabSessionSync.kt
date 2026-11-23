package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.domain.model.WorkspaceHomeData
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.WebViewStateStore
import com.jestk.browser.ui.viewmodel.LastTabPrompt
import com.jestk.browser.util.UrlSecurityUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val TAG = "TabCoordinator"

internal class TabSessionSync(
    private val ctx: TabCoordinatorContext,
) {
    private val tabRepository = ctx.tabRepository
    private val favoriteRepository = ctx.favoriteRepository
    private val webViewPool = ctx.webViewPool
    private val tabOperationMutex = ctx.tabOperationMutex
    private val tabThemeColors = ctx.tabThemeColors
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val tabBottomThemeColors = ctx.tabBottomThemeColors
    private val tabFolderRepository = ctx.tabFolderRepository
    private val workspaceRepository = ctx.workspaceRepository
    private val activeWorkspaceIdFlow = ctx.activeWorkspaceIdFlow
    private var cachedAllTabs: List<Tab>
        get() = ctx.cachedAllTabs
        set(value) {
            ctx.cachedAllTabs = value
        }
    private var cachedAllFolders: List<TabFolder>
        get() = ctx.cachedAllFolders
        set(value) {
            ctx.cachedAllFolders = value
        }

    /**
     * Asked once while the session loads (with the restored active tab); true opens a fresh Home
     * tab as the active one so the restored tab is never shown. Cleared by its owner after use.
     */
    @Volatile
    var resumeOnRestore: ((Tab) -> Boolean)? = null

    suspend fun restoreSession(
        initialSettings: BrowserSettings,
        currentEngines: List<SearchEngine>
    ) {
        tabOperationMutex.withLock {
            val initialTabs = tabRepository.getInitialTabs()
            cachedAllTabs = initialTabs

            // Prepopulate tabThemeColors cache from DB-persisted colors
            initialTabs.forEach { tab ->
                tab.themeColor?.let { color -> tabThemeColors[tab.id] = color }
            }

            val resolvedEngine = currentEngines.firstOrNull { it.id == initialSettings.searchEngine.id }
                ?: currentEngines.first()
            val effectiveSettings = initialSettings.copy(searchEngine = resolvedEngine)

            // If tabs were already populated in state (e.g. openNewTab ran first during quick user interaction)
            val currentUiTabs = getCurrentState().tabs
            if (currentUiTabs.isNotEmpty()) {
                val currentActiveTab = getCurrentState().activeTab ?: currentUiTabs.first()
                webViewPool?.onTabActivated(currentActiveTab.id, false)
                val hasWebUrl = currentActiveTab.url.isNotEmpty() && currentActiveTab.url != "about:blank"
                val initialThemeColor = if (hasWebUrl) (tabThemeColors[currentActiveTab.id] ?: currentActiveTab.themeColor) else null
                if (hasWebUrl) {
                    tabLoadingProgress[currentActiveTab.id] = 15
                }
                updateUiState { state ->
                    state.copy(
                        settings = effectiveSettings,
                        searchEngines = currentEngines,
                        activeThemeColor = initialThemeColor ?: state.activeThemeColor,
                        activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                        isLoading = if (hasWebUrl) true else state.isLoading,
                        loadingProgress = if (hasWebUrl) 0.15f else state.loadingProgress,
                    )
                }
                return@withLock
            }

            val activeWorkspaceId = getCurrentState().activeWorkspaceId
            val workspaceTabs = initialTabs.filter { it.workspaceId == activeWorkspaceId }

            val restoredActive: Tab =
                if (workspaceTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "", workspaceId = activeWorkspaceId)
                    tabRepository.setActiveTab(newTab.id)
                    newTab.copy(isActive = true)
                } else {
                    val found =
                        workspaceTabs.find { it.isActive }
                            ?: workspaceTabs.maxByOrNull { it.lastActiveTimestamp }
                            ?: workspaceTabs.first()
                    tabRepository.setActiveTab(found.id)
                    found.copy(isActive = true)
                }

            // The app came back after a long absence: the restored session opens on a fresh Home tab.
            val resumeHome =
                if (workspaceTabs.isNotEmpty() && resumeOnRestore?.invoke(restoredActive) == true) {
                    tabRepository.createTab(url = "", title = "", workspaceId = activeWorkspaceId)
                        .also { tabRepository.setActiveTab(it.id) }
                } else {
                    null
                }
            val activeTab = resumeHome?.copy(isActive = true) ?: restoredActive

            val currentTabs = tabRepository.getInitialTabs().filter { it.workspaceId == activeWorkspaceId }
            val rawTabsWithActive = if (currentTabs.isEmpty()) listOf(activeTab) else currentTabs
            val tabsWithActive = rawTabsWithActive.map { tab ->
                val tabFavicon = tab.faviconUrl
                val resolvedFavicon = if (!tabFavicon.isNullOrBlank()) {
                    val fileName = java.io.File(tabFavicon).name
                    if (fileName.startsWith("domain_") && fileName.endsWith(".png")) {
                        val fileHost = fileName.removePrefix("domain_").removeSuffix(".png")
                        val currentHost = FaviconManager.extractHost(tab.url)?.let { FaviconManager.sanitizeHost(it) }
                        if (currentHost != null && fileHost != currentHost) {
                            FaviconManager.getCachedFaviconForUrl(tab.url)
                        } else {
                            tabFavicon
                        }
                    } else {
                        tabFavicon
                    }
                } else {
                    FaviconManager.getCachedFaviconForUrl(tab.url)
                }
                tab.copy(faviconUrl = resolvedFavicon)
            }

            tabsWithActive.forEach { tab ->
                tab.themeColor?.let { color -> tabThemeColors[tab.id] = color }
            }

            webViewPool?.onTabActivated(activeTab.id, false)
            Log.d(TAG, "Restored session with active tab ${activeTab.id} (${tabsWithActive.size} tabs)")
            val hasWebUrl = activeTab.url.isNotEmpty() && activeTab.url != "about:blank"
            val initialThemeColor = if (hasWebUrl) (tabThemeColors[activeTab.id] ?: activeTab.themeColor) else null
            if (hasWebUrl) {
                tabLoadingProgress[activeTab.id] = 15
            } else {
                tabLoadingProgress[activeTab.id] = 100
            }

            val restoredInsecureHosts = tabsWithActive
                .map { it.url }
                .filter { UrlSecurityUtils.isHttpUrl(it) }
                .map { UrlSecurityUtils.extractHost(it) }
                .filter { it.isNotEmpty() }
                .toSet()

            ctx.holdUntilFirstPaintIfRecreated(activeTab.id, activeTab.url)
            WebViewStateStore.retainOnly(initialTabs.mapTo(HashSet()) { it.id })
            updateUiState { state ->
                state.copy(
                    tabs = tabsWithActive,
                    allTabs = initialTabs,
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    pageTitle = activeTab.title,
                    canGoBack = false,
                    canGoForward = false,
                    activeThemeColor = initialThemeColor,
                    allowedInsecureHosts = state.allowedInsecureHosts + restoredInsecureHosts,
                    isLoading = hasWebUrl,
                    loadingProgress = if (hasWebUrl) 0.15f else 0f,
                    settings = effectiveSettings,
                    searchEngines = currentEngines,
                    isMenuExpanded = false,
                    isSearchEngineSwitcherEditExpanded = false,
                    activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                    lastTabPrompt =
                        resumeHome?.let { LastTabPrompt(restoredActive.id, it.id) } ?: state.lastTabPrompt,
                )
            }
        }
    }

    fun observeDatabaseFlows() {
        workspaceRepository?.let { repo ->
            scope.launch(ioDispatcher) {
                repo.getAllWorkspaces().collectLatest { wsList ->
                    val effective = if (wsList.isNotEmpty()) wsList else Workspace.DEFAULT_WORKSPACES
                    updateUiState { state ->
                        val activeId = if (effective.any { it.id == state.activeWorkspaceId }) {
                            state.activeWorkspaceId
                        } else {
                            effective.first().id
                        }
                        state.copy(
                            workspaces = effective,
                            activeWorkspaceId = activeId,
                        )
                    }
                }
            }
        }

        tabFolderRepository?.let { repo ->
            scope.launch(ioDispatcher) {
                repo.getAllFolders().collectLatest { allFolders ->
                    cachedAllFolders = allFolders
                    updateUiState { state ->
                        state.copy(folders = allFolders.filter { it.workspaceId == state.activeWorkspaceId })
                    }
                }
            }
        }

        scope.launch(ioDispatcher) {
            tabRepository.getAllTabs().collectLatest { allTabs ->
                cachedAllTabs = allTabs
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val tabs = allTabs.filter { it.workspaceId == activeWorkspaceId }
                updateUiState { state ->
                    val currentTabInCurrentWorkspace = state.tabs.find { it.id == state.activeTabId && it.workspaceId == state.activeWorkspaceId }
                    if (currentTabInCurrentWorkspace != null && tabs.none { it.id == state.activeTabId }) {
                        return@updateUiState state.copy(allTabs = allTabs)
                    }
                    val activeId = if (tabs.any { it.id == state.activeTabId }) {
                        state.activeTabId
                    } else {
                        tabs.firstOrNull { it.isActive }?.id ?: tabs.firstOrNull()?.id
                    }
                    val isSameAsActive = activeId != null && activeId == state.activeTabId
                    val enrichedTabs =
                        tabs.map { tab ->
                            val inMemoryTab = state.tabs.find { it.id == tab.id }
                            val cached = tabThemeColors[tab.id] ?: inMemoryTab?.themeColor
                            val isActive = tab.id == activeId
                            val tabUrl = if (isActive && isSameAsActive) state.currentUrl else tab.url
                            val tabTitle = if (isActive && isSameAsActive) state.pageTitle else tab.title
                            val tabFavicon = if (isActive && isSameAsActive && inMemoryTab != null) inMemoryTab.faviconUrl else tab.faviconUrl
                            val resolvedFavicon = if (!tabFavicon.isNullOrBlank()) {
                                val fileName = java.io.File(tabFavicon).name
                                if (fileName.startsWith("domain_") && fileName.endsWith(".png")) {
                                    val fileHost = fileName.removePrefix("domain_").removeSuffix(".png")
                                    val currentHost = FaviconManager.extractHost(tabUrl)?.let { FaviconManager.sanitizeHost(it) }
                                    if (currentHost != null && fileHost != currentHost) {
                                        FaviconManager.getCachedFaviconForUrl(tabUrl)
                                    } else {
                                        tabFavicon
                                    }
                                } else {
                                    tabFavicon
                                }
                            } else {
                                FaviconManager.getCachedFaviconForUrl(tabUrl)
                            }
                            if (cached != null) {
                                tabThemeColors[tab.id] = cached
                            }
                            tab.copy(
                                url = tabUrl,
                                title = tabTitle,
                                isActive = isActive,
                                themeColor = cached ?: tab.themeColor,
                                bottomThemeColor = tabBottomThemeColors[tab.id] ?: tab.bottomThemeColor,
                                faviconUrl = resolvedFavicon,
                            )
                        }
                    val currentTab = enrichedTabs.find { it.id == activeId }
                    val isHome = currentTab?.url.isNullOrEmpty() || currentTab?.url == "about:blank"
                    val activeColor = if (activeId != null) tabThemeColors[activeId] else null
                    val activeBottomColor = if (activeId != null) tabBottomThemeColors[activeId] else null
                    val resolvedUrl = if (isSameAsActive) state.currentUrl else (currentTab?.url ?: if (activeId == null) "" else state.currentUrl)
                    val resolvedTitle = if (isSameAsActive) state.pageTitle else (currentTab?.title ?: if (activeId == null) "" else state.pageTitle)
                    state.copy(
                        tabs = enrichedTabs,
                        allTabs = allTabs,
                        activeTabId = activeId,
                        currentUrl = resolvedUrl,
                        pageTitle = resolvedTitle,
                        canGoBack = currentTab?.canGoBack ?: state.canGoBack,
                        canGoForward = currentTab?.canGoForward ?: state.canGoForward,
                        activeThemeColor = if (isHome) null else (activeColor ?: state.activeThemeColor),
                        activeBottomThemeColor = if (isHome) null else (activeBottomColor ?: state.activeBottomThemeColor),
                    )
                }
            }
        }

        scope.launch(ioDispatcher) {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            activeWorkspaceIdFlow
                .flatMapLatest { wsId -> favoriteRepository.getFavorites(wsId) }
                .collectLatest { favorites ->
                    updateUiState { it.copy(favorites = favorites) }
                }
        }

        scope.launch(ioDispatcher) {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val workspacesFlow = workspaceRepository?.getAllWorkspaces()
                ?: flowOf(getCurrentState().workspaces)
            workspacesFlow
                .flatMapLatest { wsList ->
                    val effective = if (wsList.isNotEmpty()) wsList else Workspace.DEFAULT_WORKSPACES
                    val perWorkspace = effective.map { ws ->
                        favoriteRepository.getFavorites(ws.id).map { favorites ->
                            ws.id to WorkspaceHomeData(favorites)
                        }
                    }
                    if (perWorkspace.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(*perWorkspace.toTypedArray()) { pairs ->
                            @Suppress("UNCHECKED_CAST")
                            (pairs as Array<Pair<String, WorkspaceHomeData>>).toMap()
                        }
                    }
                }
                .collectLatest { data ->
                    updateUiState { it.copy(workspaceHomeData = data) }
                }
        }
    }
}
