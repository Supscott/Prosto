package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.IncognitoProfile
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.NavigationCommand
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

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
        publishActiveTab(created, url, pageTitle, cachedFavicon, isWebLoading, isHome, fallbackTabs)
    }

    /**
     * Makes a Home tab active in the UI state before this call returns, so the next frame already
     * shows it; the database write follows on the IO dispatcher. Returns the id of the active tab.
     */
    fun openHomeTabNow(): String {
        val state = getCurrentState()
        val existing = state.tabs.firstOrNull { it.isHomeTab && !it.isIncognito && it.workspaceId == state.activeWorkspaceId }
        val tab =
            existing ?: Tab(
                id = UUID.randomUUID().toString(),
                url = "",
                title = "",
                lastActiveTimestamp = System.currentTimeMillis(),
                position = (state.tabs.maxOfOrNull { it.position } ?: -1) + 1,
                workspaceId = state.activeWorkspaceId,
            )
        webViewPool?.onTabActivated(tab.id, false)
        publishActiveTab(tab, "", "", null, isWebLoading = false, isHome = true, fallbackTabs = emptyList())
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                if (existing == null) {
                    tabRepository.createTab(url = "", title = "", id = tab.id, workspaceId = tab.workspaceId)
                }
                tabRepository.setActiveTab(tab.id)
            }
        }
        return tab.id
    }

    private fun publishActiveTab(
        created: Tab,
        url: String,
        pageTitle: String,
        cachedFavicon: String?,
        isWebLoading: Boolean,
        isHome: Boolean,
        fallbackTabs: List<Tab>,
    ) {
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

    /**
     * Opens [url] in a new active tab. [isIncognito] null means a link opened from an incognito
     * tab stays incognito; an empty URL (the "+" button) and external-app links are regular.
     */
    fun openNewTab(url: String, openedByExternalApp: Boolean = false, isIncognito: Boolean? = null) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val isHome = url.isEmpty() || url == "about:blank"
                val title = ""
                val incognito = isIncognito
                    ?: (!isHome && !openedByExternalApp && getCurrentState().activeTab?.isIncognito == true)
                val cachedFavicon = if (!isHome) FaviconManager.getCachedFaviconForUrl(url) else null
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = url, title = title, isIncognito = incognito, workspaceId = activeWorkspaceId)
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
                // The popup WebView already exists; its profile says whether the opener was incognito.
                val incognito = IncognitoProfile.isIncognito(webViewPool?.getProfileForTab(tabId))
                val created = tabRepository.createTab(url = "", title = "", isIncognito = incognito, id = tabId, workspaceId = activeWorkspaceId)
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
                val incognito = getCurrentState().activeTab?.isIncognito == true
                val created = tabRepository.createTab(url = url, title = title, isIncognito = incognito, workspaceId = activeWorkspaceId)
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
