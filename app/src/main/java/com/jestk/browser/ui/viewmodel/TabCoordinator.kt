package com.jestk.browser.ui.viewmodel

import android.net.Uri
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewPool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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
    } catch (_: Throwable) {
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
) {
    suspend fun restoreSession(
        initialSettings: BrowserSettings,
        currentEngines: List<SearchEngine>
    ) {
        tabOperationMutex.withLock {
            val initialTabs = tabRepository.getInitialTabs()

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
                updateUiState { state ->
                    state.copy(
                        settings = effectiveSettings,
                        searchEngines = currentEngines,
                        activeThemeColor = initialThemeColor ?: state.activeThemeColor,
                        activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                    )
                }
                return@withLock
            }

            val activeTab: Tab =
                if (initialTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home")
                    tabRepository.setActiveTab(newTab.id)
                    newTab.copy(isActive = true)
                } else {
                    val found =
                        initialTabs.find { it.isActive }
                            ?: initialTabs.maxByOrNull { it.lastActiveTimestamp }
                            ?: initialTabs.first()
                    tabRepository.setActiveTab(found.id)
                    found.copy(isActive = true)
                }

            val currentTabs = tabRepository.getInitialTabs()
            val tabsWithActive = if (currentTabs.isEmpty()) listOf(activeTab) else currentTabs

            tabsWithActive.forEach { tab ->
                tab.themeColor?.let { color -> tabThemeColors[tab.id] = color }
            }

            webViewPool?.onTabActivated(activeTab.id, false)
            val hasWebUrl = activeTab.url.isNotEmpty() && activeTab.url != "about:blank"
            val initialThemeColor = if (hasWebUrl) (tabThemeColors[activeTab.id] ?: activeTab.themeColor) else null

            updateUiState { state ->
                state.copy(
                    tabs = tabsWithActive,
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    pageTitle = activeTab.title,
                    canGoBack = false,
                    canGoForward = false,
                    activeThemeColor = initialThemeColor,
                    isLoading = hasWebUrl,
                    loadingProgress = if (hasWebUrl) 0.15f else 0f,
                    settings = effectiveSettings,
                    searchEngines = currentEngines,
                    isMenuExpanded = false,
                    activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                )
            }
        }
    }

    fun observeDatabaseFlows() {
        scope.launch(ioDispatcher) {
            tabRepository.getAllTabs().collectLatest { tabs ->
                updateUiState { state ->
                    val activeId =
                        tabs.firstOrNull { it.isActive }?.id
                            ?: if (tabs.any { it.id == state.activeTabId }) {
                                state.activeTabId
                            } else {
                                tabs.firstOrNull()?.id
                            }
                    val enrichedTabs =
                        tabs.map { tab ->
                            val cached = tabThemeColors[tab.id] ?: state.tabs.find { it.id == tab.id }?.themeColor
                            if (cached != null) {
                                tabThemeColors[tab.id] = cached
                                tab.copy(themeColor = cached)
                            } else {
                                tab
                            }
                        }
                    val currentTab = enrichedTabs.find { it.id == activeId }
                    val isHome = currentTab?.url.isNullOrEmpty() || currentTab?.url == "about:blank"
                    val activeColor = if (activeId != null) tabThemeColors[activeId] else null
                    state.copy(
                        tabs = enrichedTabs,
                        activeTabId = activeId,
                        currentUrl = currentTab?.url ?: state.currentUrl,
                        pageTitle = currentTab?.title ?: state.pageTitle,
                        canGoBack = currentTab?.canGoBack ?: state.canGoBack,
                        canGoForward = currentTab?.canGoForward ?: state.canGoForward,
                        activeThemeColor = if (isHome) null else (activeColor ?: state.activeThemeColor),
                    )
                }
            }
        }

        if (recentTabRepository != null) {
            scope.launch(ioDispatcher) {
                recentTabRepository.getRecentTabs(10).collectLatest { recents ->
                    updateUiState { it.copy(recentTabs = recents) }
                }
            }
        } else {
            scope.launch(ioDispatcher) {
                tabRepository.getRecentTabs(6).collectLatest { recents ->
                    updateUiState { state ->
                        state.copy(
                            recentTabs =
                                recents.map { tab ->
                                    RecentTabItem(
                                        url = tab.url,
                                        title = tab.title,
                                        snapshotPath = tab.snapshotPath,
                                        faviconUrl = tab.faviconUrl,
                                        lastVisitedTimestamp = tab.lastActiveTimestamp,
                                    )
                                },
                        )
                    }
                }
            }
        }

        scope.launch(ioDispatcher) {
            favoriteRepository.getFavorites().collectLatest { favorites ->
                updateUiState { it.copy(favorites = favorites) }
            }
        }
    }

    fun openNewTabForSearch(onSearchOpened: () -> Unit) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created = tabRepository.createTab(url = "", title = "Search")
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true)).distinctBy { it.id }
                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = "",
                        pageTitle = "Search",
                        isLoading = false,
                        activeThemeColor = null,
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

    fun openNewTab(url: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val created = tabRepository.createTab(url = url, title = title)
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true)).distinctBy { it.id }
                val isHome = url.isEmpty() || url == "about:blank"
                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = url,
                        pageTitle = title,
                        isLoading = url.isNotEmpty(),
                        activeThemeColor = null,
                        isMenuExpanded = false,
                        activeOverlay = if (isHome) OverlayType.HOME else OverlayType.NONE,
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                        isBottomBarVisible = true,
                    )
                }
                if (url.isNotEmpty() && url != "about:blank") {
                    emitNavigation(NavigationCommand.LoadUrl(url))
                }
            }
        }
    }

    fun openTabInBackground(url: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val created = tabRepository.createTab(url = url, title = title)
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val updatedTabs = (baseTabs + created.copy(isActive = false)).distinctBy { it.id }
                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                    )
                }
            }
        }
    }

    fun openRecentTab(item: RecentTabItem, onNavigateUrl: (String) -> Unit) {
        val existingOpenTab = getCurrentState().tabs.firstOrNull { it.url == item.url }
        if (existingOpenTab != null) {
            selectTab(existingOpenTab.id)
            updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
        } else {
            val currentTab = getCurrentState().activeTab
            if (currentTab != null && (currentTab.url.isEmpty() || currentTab.url == "about:blank")) {
                onNavigateUrl(item.url)
                updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
            } else {
                openNewTab(item.url)
                updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
            }
        }
    }

    fun selectTab(tabId: String) {
        webViewPool?.onTabActivated(tabId, getCurrentState().isVideoPlaying)
        val selected = getCurrentState().tabs.find { it.id == tabId }
        if (selected != null) {
            val isHome = selected.url.isEmpty() || selected.url == "about:blank"
            val cachedColor = tabThemeColors[tabId] ?: selected.themeColor
            updateUiState { state ->
                val updatedTabs =
                    state.tabs.map { t ->
                        val c = tabThemeColors[t.id] ?: t.themeColor
                        t.copy(isActive = t.id == tabId, themeColor = c)
                    }
                state.copy(
                    tabs = updatedTabs,
                    activeTabId = tabId,
                    currentUrl = selected.url,
                    pageTitle = selected.title,
                    canGoBack = selected.canGoBack,
                    canGoForward = selected.canGoForward,
                    activeThemeColor = if (isHome) null else cachedColor,
                    isMenuExpanded = false,
                    activeOverlay = if (isHome) OverlayType.HOME else OverlayType.NONE,
                    isBottomBarVisible = true,
                )
            }
        }
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.setActiveTab(tabId)
            }
        }
    }

    fun closeTab(tabId: String) {
        tabThemeColors.remove(tabId)
        webViewPool?.removeWebView(tabId)
        val closingTab = getCurrentState().tabs.find { it.id == tabId }
        scope.launch(ioDispatcher) {
            SnapshotEngine.deleteTabSnapshot(tabId)
            FaviconManager.deleteTabFavicons(tabId)
            tabOperationMutex.withLock {
                val currentTabs = getCurrentState().tabs
                val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                val closedIdx = currentTabs.indexOfFirst { it.id == tabId }
                val wasActive = getCurrentState().activeTabId == tabId
                tabRepository.closeTab(tabId)
                val remainingTabs = currentTabs.filter { it.id != tabId }
                if (remainingTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home")
                    tabRepository.setActiveTab(newTab.id)
                    webViewPool?.onTabActivated(newTab.id, false)
                    updateUiState {
                        it.copy(
                            tabs = listOf(newTab.copy(isActive = true)),
                            activeTabId = newTab.id,
                            currentUrl = "",
                            pageTitle = "Home",
                            activeThemeColor = null,
                            activeOverlay = if (it.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                            recentlyClosedTab = tabToSave,
                            showUndoTabCloseSnackbar = tabToSave != null,
                        )
                    }
                } else {
                    val nextActive =
                        if (!wasActive && remainingTabs.any { it.id == getCurrentState().activeTabId }) {
                            remainingTabs.first { it.id == getCurrentState().activeTabId }
                        } else if (closedIdx > 0) {
                            currentTabs[closedIdx - 1]
                        } else {
                            remainingTabs.first()
                        }
                    val isNextHome = nextActive.url.isEmpty() || nextActive.url == "about:blank"
                    val nextColor = tabThemeColors[nextActive.id] ?: nextActive.themeColor
                    tabRepository.setActiveTab(nextActive.id)
                    webViewPool?.onTabActivated(nextActive.id, false)
                    updateUiState {
                        it.copy(
                            tabs = remainingTabs.map { t -> t.copy(isActive = t.id == nextActive.id) },
                            activeTabId = nextActive.id,
                            currentUrl = nextActive.url,
                            pageTitle = nextActive.title,
                            canGoBack = nextActive.canGoBack,
                            canGoForward = nextActive.canGoForward,
                            activeThemeColor = if (isNextHome) null else nextColor,
                            activeOverlay =
                                if (it.activeOverlay == OverlayType.TAB_GRID) {
                                    OverlayType.TAB_GRID
                                } else if (isNextHome) {
                                    OverlayType.HOME
                                } else {
                                    OverlayType.NONE
                                },
                            recentlyClosedTab = tabToSave,
                            showUndoTabCloseSnackbar = tabToSave != null,
                        )
                    }
                }
            }
        }
    }

    fun undoCloseTab() {
        val tabToRestore = getCurrentState().recentlyClosedTab ?: return
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created =
                    tabRepository.createTab(
                        url = tabToRestore.url,
                        title = tabToRestore.title,
                        isIncognito = tabToRestore.isIncognito,
                    )
                tabToRestore.snapshotPath?.let { tabRepository.updateSnapshot(created.id, it) }
                tabToRestore.faviconUrl?.let { tabRepository.updateFavicon(created.id, it) }
                tabToRestore.themeColor?.let {
                    tabThemeColors[created.id] = it
                    tabRepository.updateThemeColor(created.id, it)
                }
                tabRepository.setActiveTab(created.id)

                val restoredTab =
                    created.copy(
                        isActive = true,
                        snapshotPath = tabToRestore.snapshotPath,
                        faviconUrl = tabToRestore.faviconUrl,
                        themeColor = tabToRestore.themeColor,
                        canGoBack = tabToRestore.canGoBack,
                        canGoForward = tabToRestore.canGoForward,
                    )

                val updatedTabs =
                    (getCurrentState().tabs.map { it.copy(isActive = false) } + restoredTab)
                        .distinctBy { it.id }

                val isHome = restoredTab.url.isEmpty() || restoredTab.url == "about:blank"
                val themeColor = if (isHome) null else (tabThemeColors[created.id] ?: restoredTab.themeColor)

                updateUiState { state ->
                    state.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = restoredTab.url,
                        pageTitle = restoredTab.title,
                        canGoBack = restoredTab.canGoBack,
                        canGoForward = restoredTab.canGoForward,
                        activeThemeColor = themeColor,
                        recentlyClosedTab = null,
                        showUndoTabCloseSnackbar = false,
                        activeOverlay =
                            if (state.activeOverlay == OverlayType.TAB_GRID) {
                                OverlayType.TAB_GRID
                            } else if (isHome) {
                                OverlayType.HOME
                            } else {
                                OverlayType.NONE
                            },
                    )
                }

                if (restoredTab.url.isNotEmpty() && restoredTab.url != "about:blank") {
                    emitNavigation(NavigationCommand.LoadUrl(restoredTab.url))
                }
            }
        }
    }

    fun closeAllTabs() {
        tabThemeColors.clear()
        webViewPool?.clearAll()
        scope.launch(ioDispatcher) {
            SnapshotEngine.deleteAllSnapshots()
            FaviconManager.deleteAllFavicons()
            tabOperationMutex.withLock {
                tabRepository.closeAllTabs()
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                webViewPool?.onTabActivated(newTab.id, false)
                updateUiState {
                    it.copy(
                        tabs = listOf(newTab.copy(isActive = true)),
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        activeThemeColor = null,
                        activeOverlay = if (it.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                    )
                }
            }
        }
    }

    fun switchAdjacentTab(delta: Int) {
        val currentTabs = getCurrentState().tabs
        if (currentTabs.isEmpty() || currentTabs.size <= 1 || currentTabs.lastIndex <= 0) return
        val currentIndex = getCurrentState().activeTabIndex
        val maxIndex = currentTabs.lastIndex
        if (maxIndex < 0) return
        val targetIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        if (targetIndex in currentTabs.indices && targetIndex != currentIndex) {
            selectTab(currentTabs[targetIndex].id)
        }
    }

    fun reorderTabs(orderedTabs: List<Tab>) {
        scope.launch(ioDispatcher) {
            tabRepository.reorderTabs(orderedTabs)
        }
    }

    fun navigateToUrl(finalUrl: String, matchedShortcutEngine: SearchEngine? = null) {
        val activeId = getCurrentState().activeTabId ?: return
        webViewPool?.onTabActivated(activeId, false)
        if (finalUrl.isEmpty()) {
            updateUiState {
                val updatedTabs =
                    it.tabs.map { tab ->
                        if (tab.id == activeId) tab.copy(url = "", title = "Home", faviconUrl = null) else tab
                    }
                it.copy(
                    tabs = updatedTabs,
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    isMenuExpanded = false,
                    activeOverlay = OverlayType.HOME,
                )
            }
            scope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(activeId, "", "Home", canGoBack = false, canGoForward = false)
                tabRepository.updateFavicon(activeId, null)
            }
            return
        }

        val currentTab = getCurrentState().tabs.find { it.id == activeId }
        val sameDomain = currentTab != null && isSameDomain(currentTab.url, finalUrl)
        val newFavicon = if (sameDomain) currentTab?.faviconUrl else null

        updateUiState {
            val updatedTabs =
                it.tabs.map { tab ->
                    if (tab.id == activeId) tab.copy(url = finalUrl, title = finalUrl, faviconUrl = newFavicon) else tab
                }
            it.copy(
                tabs = updatedTabs,
                currentUrl = finalUrl,
                settings = if (matchedShortcutEngine != null) it.settings.copy(searchEngine = matchedShortcutEngine) else it.settings,
                isLoading = true,
                loadingProgress = 0.1f,
                activeThemeColor = tabThemeColors[activeId] ?: it.tabs.find { tab -> tab.id == activeId }?.themeColor,
                isVideoPlaying = false,
                isMenuExpanded = false,
                activeOverlay = OverlayType.NONE,
            )
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
            if (!sameDomain) {
                tabRepository.updateFavicon(activeId, null)
            }
        }
        emitNavigation(NavigationCommand.LoadUrl(finalUrl))
    }

    fun handlePageStarted(tabId: String, url: String) {
        if (url.isEmpty() || url == "about:blank") return
        val currentTab = getCurrentState().tabs.find { it.id == tabId }
        val sameDomain = currentTab != null && isSameDomain(currentTab.url, url)
        val newFavicon = if (sameDomain) currentTab?.faviconUrl else null

        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(url = url, faviconUrl = newFavicon) else tab
            }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    isLoading = true,
                    loadingProgress = 0.15f,
                    isBottomBarVisible = true,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
        if (!sameDomain) {
            scope.launch(ioDispatcher) {
                tabRepository.updateFavicon(tabId, null)
            }
        }
    }

    fun handlePageFinished(tabId: String, url: String, title: String) {
        if (url.isEmpty() || url == "about:blank") return
        val displayTitle = title.ifEmpty { url }
        if (getCurrentState().activeTabId == tabId) {
            updateUiState { state ->
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(url = url, title = displayTitle) else tab
                }
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                    loadingProgress = 1.0f,
                )
            }
        }
        scope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            tabRepository.updateTabDetails(
                id = tabId,
                url = url,
                title = displayTitle,
                canGoBack = tab?.canGoBack ?: getCurrentState().canGoBack,
                canGoForward = tab?.canGoForward ?: false,
            )
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                historyRepository.recordVisit(url, displayTitle)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = displayTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl,
                )
            }
        }
    }

    fun handleProgressChanged(tabId: String, progress: Int) {
        if (getCurrentState().activeTabId == tabId) {
            updateUiState {
                it.copy(
                    loadingProgress = (progress / 100f).coerceIn(0f, 1f),
                    isLoading = progress < 100,
                )
            }
        }
    }

    fun handleNavState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (getCurrentState().activeTabId == tabId) {
            updateUiState { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
        scope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            if (tab != null) {
                tabRepository.updateTabDetails(
                    id = tabId,
                    url = tab.url,
                    title = tab.title,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                )
            }
        }
    }

    fun handleSnapshot(tabId: String, path: String) {
        scope.launch(ioDispatcher) {
            tabRepository.updateSnapshot(tabId, path)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:")) {
                recentTabRepository?.updateSnapshot(tab.url, path)
            }
        }
    }

    fun handleUpdateFavicon(tabId: String, faviconUrl: String?) {
        updateUiState { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(faviconUrl = faviconUrl) else tab
                    },
            )
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateFavicon(tabId, faviconUrl)
            val tab = tabRepository.getTabById(tabId)
            if (tab != null && tab.url.isNotEmpty() && !tab.url.startsWith("about:") && !faviconUrl.isNullOrEmpty()) {
                recentTabRepository?.updateFavicon(tab.url, faviconUrl)
                if (favoriteRepository.isFavorite(tab.url)) {
                    val persistentIconPath = if (faviconUrl.startsWith("/") || faviconUrl.startsWith("file:")) {
                        FaviconManager.persistFaviconForFavorite(faviconUrl)
                    } else null
                    val iconToUpdate = persistentIconPath ?: faviconUrl
                    favoriteRepository.updateFavicon(tab.url, iconToUpdate)
                }
            }
        }
    }

    fun handleThemeColor(tabId: String, color: Int?) {
        if (color != null) {
            tabThemeColors[tabId] = color
        } else {
            tabThemeColors.remove(tabId)
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateThemeColor(tabId, color)
        }
        updateUiState { state ->
            val updatedTabs =
                state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(themeColor = color) else tab
                }
            if (state.activeTabId == tabId) {
                state.copy(tabs = updatedTabs, activeThemeColor = color)
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun addCurrentToFavorites() {
        val current = getCurrentState()
        if (current.currentUrl.isNotEmpty() && !current.currentUrl.startsWith("about:")) {
            val activeTab = current.tabs.find { it.id == current.activeTabId }
            val tabFavicon = activeTab?.faviconUrl
            scope.launch(ioDispatcher) {
                val persistentIconPath = if (!tabFavicon.isNullOrEmpty() && (tabFavicon.startsWith("/") || tabFavicon.startsWith("file:"))) {
                    FaviconManager.persistFaviconForFavorite(tabFavicon)
                } else null

                val iconUrl = persistentIconPath
                    ?: (if (!tabFavicon.isNullOrEmpty() && tabFavicon.startsWith("http")) tabFavicon else null)
                    ?: FaviconManager.extractFallbackFaviconUrl(current.currentUrl)

                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl,
                    iconUrl = iconUrl,
                )
            }
        }
    }

    fun addFavorite(title: String, url: String) {
        scope.launch(ioDispatcher) {
            val trimmed = url.trim()
            val normalizedUrl = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
            val derivedTitle = title.trim().ifBlank {
                normalizedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            }
            val icon = FaviconManager.extractFallbackFaviconUrl(normalizedUrl)
            favoriteRepository.addFavorite(derivedTitle, normalizedUrl, icon)
        }
    }

    fun deleteFavorite(id: Long) {
        scope.launch(ioDispatcher) {
            favoriteRepository.deleteFavorite(id)
        }
    }
}
