package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.NavigationCommand
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val TAG = "TabCoordinator"

internal class TabOpening(
    ctx: TabCoordinatorContext,
    private val activation: TabActivation,
) {
    private val tabRepository = ctx.tabRepository
    private val webViewPool = ctx.webViewPool
    private val tabOperationMutex = ctx.tabOperationMutex
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val emitNavigation = ctx.emitNavigation
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains

    fun openNewTabForSearch(onSearchOpened: () -> Unit) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = "", title = "", workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created search tab ${created.id}")
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val fallbackTabs = loadFallbackTabs(created.id, activeWorkspaceId)
                tabLoadingProgress[created.id] = 100
                updateUiState {
                    it.copy(
                        tabs = it.tabsWithNewTab(created.copy(isActive = true), fallbackTabs),
                        activeTabId = created.id,
                        currentUrl = "",
                        pageTitle = "",
                        isLoading = false,
                        loadingProgress = 0f,
                        activeThemeColor = null,
                        activeBottomThemeColor = null,
                        activeOverlay = OverlayType.SEARCH,
                        searchQuery = "",
                        isSearchHistoryExpanded = false,
                        canGoBack = false,
                        canGoForward = false,
                    )
                }
                onSearchOpened()
            }
        }
    }

    /** Tabs to show when the UI has none yet (cold start); empty otherwise. */
    private suspend fun loadFallbackTabs(excludeId: String, workspaceId: String): List<Tab> =
        if (getCurrentState().tabs.isEmpty()) {
            tabRepository.getInitialTabs().filter { it.id != excludeId && it.workspaceId == workspaceId }
        } else {
            emptyList()
        }

    suspend fun activateNewTabState(
        created: Tab,
        url: String,
        pageTitle: String,
        cachedFavicon: String?,
        isWebLoading: Boolean,
        isHome: Boolean
    ) {
        tabRepository.setActiveTab(created.id)
        dismissedExternalAppDomains.remove(created.id)
        pendingExternalAppPrompts.remove(created.id)
        timedOutExternalAppDomains.remove(created.id)
        if (cachedFavicon != null) {
            tabRepository.updateFavicon(created.id, cachedFavicon)
        }
        webViewPool?.onTabActivated(created.id, false)
        val fallbackTabs = loadFallbackTabs(created.id, getCurrentState().activeWorkspaceId)
        tabLoadingProgress[created.id] = if (isWebLoading) 15 else 100
        updateUiState {
            it.copy(
                tabs = it.tabsWithNewTab(created.copy(isActive = true, faviconUrl = cachedFavicon), fallbackTabs),
                activeTabId = created.id,
                currentUrl = url,
                pageTitle = pageTitle,
                isLoading = isWebLoading,
                loadingProgress = if (isWebLoading) 0.15f else 0f,
                activeThemeColor = null,
                activeBottomThemeColor = null,
                isMenuExpanded = false,
                isSearchEngineSwitcherEditExpanded = false,
                externalAppPrompt = null,
                activeOverlay = if (isHome) OverlayType.HOME else OverlayType.NONE,
                linkContextMenuUrl = null,
                selectedImageUrl = null,
                isBottomBarVisible = true,
            )
        }
    }

    fun openNewTab(url: String, openedByExternalApp: Boolean = false) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val isHome = url.isEmpty() || url == "about:blank"
                val title = ""
                val cachedFavicon = if (!isHome) FaviconManager.getCachedFaviconForUrl(url) else null
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = url, title = title, workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created tab ${created.id} for $url")
                val isWebLoading = !isHome && url.isNotEmpty()
                activateNewTabState(created, url, title, cachedFavicon, isWebLoading, isHome)
                if (openedByExternalApp) {
                    updateUiState { it.copy(externalAppTabIds = it.externalAppTabIds + created.id) }
                }
                if (url.isNotEmpty() && url != "about:blank") {
                    emitNavigation(NavigationCommand.LoadUrl(url))
                }
            }
        }
    }

    fun openPopupTab(tabId: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = "", title = "", id = tabId, workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created popup tab ${created.id}")
                activateNewTabState(created, "", "", null, isWebLoading = true, isHome = false)
            }
        }
    }

    fun openTabInBackground(url: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = ""
                val cachedFavicon = if (url.isNotEmpty() && url != "about:blank") {
                    FaviconManager.getCachedFaviconForUrl(url)
                } else null
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = url, title = title, workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created background tab ${created.id} for $url")
                if (cachedFavicon != null) {
                    tabRepository.updateFavicon(created.id, cachedFavicon)
                }
                val fallbackTabs = loadFallbackTabs(created.id, activeWorkspaceId)
                updateUiState {
                    it.copy(
                        tabs = it.tabsWithNewTab(created.copy(isActive = false, faviconUrl = cachedFavicon), fallbackTabs),
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                    )
                }
            }
        }
    }

    fun openRecentTab(
        item: RecentTabItem,
        onNavigateUrl: (String) -> Unit,
        onOpenNewTab: (String) -> Unit = { openNewTab(it) },
    ) {
        Log.d(TAG, "Opening recent tab: ${item.url}")
        val existingOpenTab = getCurrentState().tabs.firstOrNull { it.url == item.url }
        if (existingOpenTab != null) {
            activation.selectTab(existingOpenTab.id)
            updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
        } else {
            val currentTab = getCurrentState().activeTab
            if (currentTab != null && (currentTab.url.isEmpty() || currentTab.url == "about:blank")) {
                onNavigateUrl(item.url)
                updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
            } else {
                onOpenNewTab(item.url)
                updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
            }
        }
    }
}

/**
 * The tab list with [newTab] added, built from the state being updated so that concurrent
 * title/favicon/DB updates are not overwritten. An active [newTab] deactivates the others.
 */
internal fun BrowserUiState.tabsWithNewTab(newTab: Tab, fallbackTabs: List<Tab>): List<Tab> {
    val base = (tabs.ifEmpty { fallbackTabs }).filter { it.id != newTab.id }
    val others = if (newTab.isActive) base.map { it.copy(isActive = false) } else base
    return (others + newTab).distinctBy { it.id }
}
