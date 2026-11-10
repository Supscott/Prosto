package com.jestk.browser.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.jestk.browser.R
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.ScreenType
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
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.util.UrlSecurityUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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
    @Volatile
    private var cachedAllTabs: List<Tab> = emptyList()

    @Volatile
    private var cachedAllFolders: List<TabFolder> = emptyList()

    private val recentlyClosedTabsByWorkspace = ConcurrentHashMap<String, Tab>()
    private val activeWorkspaceIdFlow = MutableStateFlow(Workspace.DEFAULT_PERSONAL_ID)

    fun handleExternalAppOffered(
        tabId: String,
        prompt: ExternalAppPrompt,
        userInitiated: Boolean = false
    ) {
        val current = getCurrentState()
        if (current.tabs.none { it.id == tabId }) return

        if (isExternalAppDomainSuppressed(tabId, prompt.domain, userInitiated = userInitiated)) {
            return
        }

        if (current.activeTabId != tabId) {
            // Keep the offer for a background tab instead of dropping it: the user may switch
            // to that tab right after the link was opened in the background.
            pendingExternalAppPrompts.putIfAbsent(tabId, prompt)
            return
        }

        val existing = current.externalAppPrompt
        if (existing != null && areExternalAppDomainsEqual(existing.domain, prompt.domain)) {
            return
        }
        updateUiState { it.copy(externalAppPrompt = prompt) }
    }

    private fun areExternalAppDomainsEqual(domain1: String, domain2: String): Boolean =
        domain1 == domain2 ||
            com.jestk.browser.engine.ExternalAppResolver.areDomainAliases(domain1, domain2)

    private fun isExternalAppDomainSuppressed(
        tabId: String,
        domain: String,
        userInitiated: Boolean
    ): Boolean {
        // Explicit dismissal suppresses regardless of whether the navigation was user-initiated.
        if (isDomainInSuppressionMap(tabId, domain, dismissedExternalAppDomains)) {
            return true
        }
        // Timeout suppression suppresses passive offers, but deliberate user gestures bypass it.
        if (!userInitiated && isDomainInSuppressionMap(tabId, domain, timedOutExternalAppDomains)) {
            return true
        }
        return false
    }

    private fun isDomainInSuppressionMap(
        tabId: String,
        domain: String,
        suppression: MutableMap<String, MutableSet<String>>
    ): Boolean {
        val suppressed = suppression[tabId] ?: return false
        return suppressed.contains(domain) ||
            suppressed.any {
                com.jestk.browser.engine.ExternalAppResolver.areDomainAliases(it, domain)
            }
    }

    private fun publishPendingExternalAppPrompt(tabId: String) {
        val prompt = pendingExternalAppPrompts.remove(tabId) ?: return
        if (isExternalAppDomainSuppressed(tabId, prompt.domain, userInitiated = false)) return
        updateUiState { it.copy(externalAppPrompt = prompt) }
    }

    fun dismissExternalAppPrompt(reason: ExternalAppDismissReason = ExternalAppDismissReason.EXPLICIT) {
        val current = getCurrentState()
        val prompt = current.externalAppPrompt
        if (prompt != null) {
            when (reason) {
                ExternalAppDismissReason.TIMEOUT -> timedOutExternalAppDomains
                    .getOrPut(prompt.tabId) { ConcurrentHashMap.newKeySet() }
                    .add(prompt.domain)
                ExternalAppDismissReason.EXPLICIT -> dismissedExternalAppDomains
                    .getOrPut(prompt.tabId) { ConcurrentHashMap.newKeySet() }
                    .add(prompt.domain)
                ExternalAppDismissReason.TRANSIENT -> Unit
            }
            pendingExternalAppPrompts.remove(prompt.tabId)
        }
        updateUiState { it.copy(externalAppPrompt = null) }
    }
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

            val activeTab: Tab =
                if (workspaceTabs.isEmpty()) {
                    val newTab = tabRepository.createTab(url = "", title = "Home", workspaceId = activeWorkspaceId)
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

        if (recentTabRepository != null) {
            scope.launch(ioDispatcher) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                activeWorkspaceIdFlow
                    .flatMapLatest { wsId -> recentTabRepository.getRecentTabs(10, wsId) }
                    .collectLatest { recents ->
                        updateUiState { it.copy(recentTabs = recents) }
                    }
            }
        } else {
            scope.launch(ioDispatcher) {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                activeWorkspaceIdFlow
                    .flatMapLatest { wsId -> tabRepository.getRecentTabs(6, wsId) }
                    .collectLatest { recents ->
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
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            activeWorkspaceIdFlow
                .flatMapLatest { wsId -> favoriteRepository.getFavorites(wsId) }
                .collectLatest { favorites ->
                    updateUiState { it.copy(favorites = favorites) }
                }
        }
    }

    fun openNewTabForSearch(onSearchOpened: () -> Unit) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = "", title = "Search", workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created search tab ${created.id}")
                tabRepository.setActiveTab(created.id)
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id && it.workspaceId == activeWorkspaceId }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true)).distinctBy { it.id }
                tabLoadingProgress[created.id] = 100
                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = "",
                        pageTitle = "Search",
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

    private suspend fun activateNewTabState(
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
        val activeWorkspaceId = getCurrentState().activeWorkspaceId
        val baseTabs =
            if (getCurrentState().tabs.isEmpty()) {
                tabRepository.getInitialTabs().filter { it.id != created.id && it.workspaceId == activeWorkspaceId }
            } else {
                getCurrentState().tabs.filter { it.id != created.id }
            }
        val currentTabs = baseTabs.map { it.copy(isActive = false) }
        val updatedTabs = (currentTabs + created.copy(isActive = true, faviconUrl = cachedFavicon)).distinctBy { it.id }
        tabLoadingProgress[created.id] = if (isWebLoading) 15 else 100
        updateUiState {
            it.copy(
                tabs = updatedTabs,
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

    fun openNewTab(url: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val isHome = url.isEmpty() || url == "about:blank"
                val title = if (isHome) "Home" else ""
                val cachedFavicon = if (!isHome) FaviconManager.getCachedFaviconForUrl(url) else null
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = url, title = title, workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created tab ${created.id} for $url")
                val isWebLoading = !isHome && url.isNotEmpty()
                activateNewTabState(created, url, title, cachedFavicon, isWebLoading, isHome)
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
                val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
                val cachedFavicon = if (url.isNotEmpty() && url != "about:blank") {
                    FaviconManager.getCachedFaviconForUrl(url)
                } else null
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = url, title = title, workspaceId = activeWorkspaceId)
                Log.d(TAG, "Created background tab ${created.id} for $url")
                if (cachedFavicon != null) {
                    tabRepository.updateFavicon(created.id, cachedFavicon)
                }
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id && it.workspaceId == activeWorkspaceId }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val updatedTabs = (baseTabs + created.copy(isActive = false, faviconUrl = cachedFavicon)).distinctBy { it.id }
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

    fun openRecentTab(
        item: RecentTabItem,
        onNavigateUrl: (String) -> Unit,
        onOpenNewTab: (String) -> Unit = { openNewTab(it) },
    ) {
        Log.d(TAG, "Opening recent tab: ${item.url}")
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
                onOpenNewTab(item.url)
                updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
            }
        }
    }

    fun selectTab(tabId: String) {
        val selected = getCurrentState().tabs.find { it.id == tabId }
        if (selected == null) {
            Log.w(TAG, "Select tab $tabId requested but the tab is not in the current state")
            return
        }
        activateTabInternal(
            selected = selected,
            tabsList = getCurrentState().tabs,
            keepOverlayIfTabGrid = false,
        )
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.setActiveTab(tabId)
            }
        }
    }

    private fun activateTabInternal(
        selected: Tab,
        tabsList: List<Tab>,
        keepOverlayIfTabGrid: Boolean = false,
        recentlyClosedTab: Tab? = null,
    ) {
        val tabId = selected.id
        selected.folderId?.let { folderId ->
            val folder = getCurrentState().getFolder(folderId)
            if (folder != null && folder.isCollapsed) {
                val expanded = folder.copy(isCollapsed = false)
                updateUiState { state ->
                    state.copy(folders = state.folders.map { if (it.id == folderId) expanded else it })
                }
                scope.launch(ioDispatcher) {
                    tabFolderRepository?.updateFolder(expanded)
                }
            }
        }
        webViewPool?.onTabActivated(tabId, getCurrentState().isVideoPlaying)

        val liveWebView = webViewPool?.getWebView(tabId)
        val liveUrl = liveWebView?.url?.takeIf { it.isNotBlank() && it != "about:blank" }
        val liveTitle = liveWebView?.title?.takeIf { it.isNotBlank() }

        val effectiveUrl = liveUrl ?: selected.url
        val effectiveTitle = liveTitle ?: (if (liveUrl != null && (selected.title.isEmpty() || selected.title == "Home")) liveUrl else selected.title)
        val effectiveCanGoBack = liveWebView?.canGoBack() ?: selected.canGoBack
        val effectiveCanGoForward = liveWebView?.canGoForward() ?: selected.canGoForward

        val isHome = effectiveUrl.isEmpty() || effectiveUrl == "about:blank"
        val cachedColor = tabThemeColors[tabId] ?: selected.themeColor
        val cachedBottomColor = tabBottomThemeColors[tabId] ?: selected.bottomThemeColor
        val progress = tabLoadingProgress[tabId]
        val isTabLoading = progress != null && progress < 100
        val tabProgress = when {
            isTabLoading -> (progress!! / 100f).coerceIn(0f, 1f)
            isHome -> 0f
            else -> 1.0f
        }

        updateUiState { state ->
            val updatedTabs =
                tabsList.map { t ->
                    val c = tabThemeColors[t.id] ?: t.themeColor
                    val bc = tabBottomThemeColors[t.id] ?: t.bottomThemeColor
                    if (t.id == tabId) {
                        t.copy(
                            isActive = true,
                            url = effectiveUrl,
                            title = effectiveTitle,
                            canGoBack = effectiveCanGoBack,
                            canGoForward = effectiveCanGoForward,
                            themeColor = c,
                            bottomThemeColor = bc,
                        )
                    } else {
                        t.copy(isActive = false, themeColor = c, bottomThemeColor = bc)
                    }
                }
            val targetOverlay = when {
                keepOverlayIfTabGrid && state.activeOverlay == OverlayType.TAB_GRID -> OverlayType.TAB_GRID
                isHome && !isTabLoading -> OverlayType.HOME
                else -> OverlayType.NONE
            }
            state.copy(
                tabs = updatedTabs,
                activeTabId = tabId,
                currentUrl = effectiveUrl,
                pageTitle = effectiveTitle,
                isLoading = isTabLoading,
                loadingProgress = tabProgress,
                canGoBack = effectiveCanGoBack,
                canGoForward = effectiveCanGoForward,
                activeThemeColor = if (isHome) null else cachedColor,
                activeBottomThemeColor = if (isHome) null else cachedBottomColor,
                isMenuExpanded = false,
                isSearchEngineSwitcherEditExpanded = false,
                externalAppPrompt = pendingExternalAppPrompts[tabId],
                activeOverlay = targetOverlay,
                isBottomBarVisible = true,
                recentlyClosedTab = recentlyClosedTab ?: state.recentlyClosedTab,
                showUndoTabCloseSnackbar = if (recentlyClosedTab != null) true else state.showUndoTabCloseSnackbar,
            )
        }
        publishPendingExternalAppPrompt(tabId)
        Log.d(TAG, "Activated tab $tabId (url=$effectiveUrl)")
        if (liveUrl != null || liveTitle != null) {
            scope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(
                    id = tabId,
                    url = effectiveUrl,
                    title = effectiveTitle,
                    canGoBack = effectiveCanGoBack,
                    canGoForward = effectiveCanGoForward,
                )
            }
        }
    }

    fun closeTab(tabId: String) {
        Log.d(TAG, "Closing tab $tabId")
        tabThemeColors.remove(tabId)
        tabBottomThemeColors.remove(tabId)
        tabLoadingProgress.remove(tabId)
        dismissedExternalAppDomains.remove(tabId)
        pendingExternalAppPrompts.remove(tabId)
        timedOutExternalAppDomains.remove(tabId)
        webViewPool?.removeWebView(tabId)

        val currentTabs = getCurrentState().tabs
        val closingTab = currentTabs.find { it.id == tabId }
        val closedIdx = currentTabs.indexOfFirst { it.id == tabId }
        val wasActive = getCurrentState().activeTabId == tabId
        val remainingTabs = currentTabs.filter { it.id != tabId }

        closingTab?.folderId?.let { folderId ->
            checkAndPruneEmptyFolder(folderId, remainingTabs)
        }

        val effectiveWorkspaceId = closingTab?.workspaceId ?: getCurrentState().activeWorkspaceId
        if (closingTab != null) {
            recentlyClosedTabsByWorkspace[effectiveWorkspaceId] = closingTab
        }

        if (!wasActive) {
            updateUiState { state ->
                val updatedTabs = remainingTabs.map { t ->
                    val c = tabThemeColors[t.id] ?: t.themeColor
                    t.copy(themeColor = c)
                }
                state.copy(
                    tabs = updatedTabs,
                    recentlyClosedTab = closingTab,
                    showUndoTabCloseSnackbar = closingTab != null,
                )
            }
            scope.launch(ioDispatcher) {
                SnapshotEngine.deleteTabSnapshot(tabId)
                FaviconManager.deleteTabFavicons(tabId)
                tabOperationMutex.withLock {
                    val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                    if (tabToSave != null) {
                        recentlyClosedTabsByWorkspace[effectiveWorkspaceId] = tabToSave
                    }
                    tabRepository.closeTab(tabId)
                }
            }
            return
        }

        if (remainingTabs.isEmpty()) {
            updateUiState { state ->
                state.copy(
                    tabs = emptyList(),
                    activeTabId = null,
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    loadingProgress = 0f,
                    activeThemeColor = null,
                    activeOverlay = if (state.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                    recentlyClosedTab = closingTab,
                    showUndoTabCloseSnackbar = closingTab != null,
                    externalAppPrompt = null,
                )
            }
            scope.launch(ioDispatcher) {
                SnapshotEngine.deleteTabSnapshot(tabId)
                FaviconManager.deleteTabFavicons(tabId)
                tabOperationMutex.withLock {
                    val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                    if (tabToSave != null) {
                        recentlyClosedTabsByWorkspace[effectiveWorkspaceId] = tabToSave
                    }
                    tabRepository.closeTab(tabId)
                    val newTab = tabRepository.createTab(url = "", title = "Home", workspaceId = getCurrentState().activeWorkspaceId)
                    tabRepository.setActiveTab(newTab.id)
                    webViewPool?.onTabActivated(newTab.id, false)
                    tabLoadingProgress[newTab.id] = 100
                    updateUiState {
                        it.copy(
                            tabs = listOf(newTab.copy(isActive = true)),
                            activeTabId = newTab.id,
                            currentUrl = "",
                            pageTitle = "Home",
                            isLoading = false,
                            loadingProgress = 0f,
                            activeThemeColor = null,
                        activeBottomThemeColor = null,
                            activeOverlay = if (it.activeOverlay == OverlayType.TAB_GRID) OverlayType.TAB_GRID else OverlayType.HOME,
                            recentlyClosedTab = tabToSave,
                            showUndoTabCloseSnackbar = tabToSave != null,
                            externalAppPrompt = null,
                        )
                    }
                }
            }
        } else {
            val nextActive =
                if (closedIdx > 0) {
                    currentTabs[closedIdx - 1]
                } else {
                    remainingTabs.first()
                }

            activateTabInternal(
                selected = nextActive,
                tabsList = remainingTabs,
                keepOverlayIfTabGrid = true,
                recentlyClosedTab = closingTab,
            )

            scope.launch(ioDispatcher) {
                SnapshotEngine.deleteTabSnapshot(tabId)
                FaviconManager.deleteTabFavicons(tabId)
                tabOperationMutex.withLock {
                    val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                    if (tabToSave != null) {
                        recentlyClosedTabsByWorkspace[effectiveWorkspaceId] = tabToSave
                    }
                    tabRepository.closeTab(tabId)
                    tabRepository.setActiveTab(nextActive.id)
                }
            }
        }
    }

    fun undoCloseTab() {
        val activeWs = getCurrentState().activeWorkspaceId
        val tabToRestore = recentlyClosedTabsByWorkspace.remove(activeWs) ?: getCurrentState().recentlyClosedTab ?: return
        Log.d(TAG, "Undo close tab requested for ${tabToRestore.id}")
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created =
                    tabRepository.createTab(
                        url = tabToRestore.url,
                        title = tabToRestore.title,
                        isIncognito = tabToRestore.isIncognito,
                        workspaceId = tabToRestore.workspaceId,
                    )
                Log.d(TAG, "Restored closed tab as ${created.id} (url=${tabToRestore.url})")
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
                val isWebLoading = !isHome && restoredTab.url.isNotEmpty()
                if (isWebLoading) {
                    tabLoadingProgress[created.id] = 15
                } else {
                    tabLoadingProgress[created.id] = 100
                }
                val themeColor = if (isHome) null else (tabThemeColors[created.id] ?: restoredTab.themeColor)

                updateUiState { state ->
                    state.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = restoredTab.url,
                        pageTitle = restoredTab.title,
                        canGoBack = restoredTab.canGoBack,
                        canGoForward = restoredTab.canGoForward,
                        isLoading = isWebLoading,
                        loadingProgress = if (isWebLoading) 0.15f else 0f,
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
        Log.d(TAG, "Closing all tabs")
        tabThemeColors.clear()
        tabLoadingProgress.clear()
        dismissedExternalAppDomains.clear()
        pendingExternalAppPrompts.clear()
        timedOutExternalAppDomains.clear()
        webViewPool?.clearAll()
        scope.launch(ioDispatcher) {
            SnapshotEngine.deleteAllSnapshots()
            FaviconManager.deleteAllFavicons()
            tabOperationMutex.withLock {
                tabRepository.closeAllTabs()
                val newTab = tabRepository.createTab(url = "", title = "Home", workspaceId = getCurrentState().activeWorkspaceId)
                tabRepository.setActiveTab(newTab.id)
                webViewPool?.onTabActivated(newTab.id, false)
                tabLoadingProgress[newTab.id] = 100
                updateUiState {
                    it.copy(
                        tabs = listOf(newTab.copy(isActive = true)),
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        isLoading = false,
                        loadingProgress = 0f,
                        activeThemeColor = null,
                        activeBottomThemeColor = null,
                        externalAppPrompt = null,
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
            Log.d(TAG, "Switching to adjacent tab ${currentTabs[targetIndex].id} (delta=$delta)")
            selectTab(currentTabs[targetIndex].id)
        }
    }

    fun reorderTabs(orderedTabs: List<Tab>) {
        reorderTabsAndFolders(orderedTabs, null)
    }

    fun reorderTabsAndFolders(
        orderedTabs: List<Tab>,
        orderedFolders: List<TabFolder>? = null,
    ) {
        Log.d(TAG, "Reordering ${orderedTabs.size} tabs and ${orderedFolders?.size ?: 0} folders")
        val prevTabs = getCurrentState().tabs
        val changedFolderIdTabs = orderedTabs.filter { newTab ->
            val oldTab = prevTabs.find { it.id == newTab.id }
            oldTab != null && oldTab.folderId != newTab.folderId
        }

        updateUiState { state ->
            var newState = state.copy(tabs = orderedTabs)
            if (orderedFolders != null) {
                newState = newState.copy(folders = orderedFolders)
            }
            newState
        }

        scope.launch(ioDispatcher) {
            changedFolderIdTabs.forEach { tab ->
                tabRepository.updateFolderId(tab.id, tab.folderId)
            }
            tabRepository.reorderTabs(orderedTabs)
            if (orderedFolders != null) {
                tabFolderRepository?.updateFolderPositions(orderedFolders)
            }
            val remainingFolderIds = orderedTabs.mapNotNull { it.folderId }.toSet()
            val allFolders = orderedFolders ?: getCurrentState().folders
            for (folder in allFolders) {
                if (folder.id !in remainingFolderIds) {
                    checkAndPruneEmptyFolder(folder.id, orderedTabs)
                }
            }
        }
    }

    fun navigateToUrl(finalUrl: String, matchedShortcutEngine: SearchEngine? = null) {
        val activeId = getCurrentState().activeTabId ?: run {
            Log.w(TAG, "Navigation to $finalUrl ignored: no active tab")
            return
        }
        Log.d(TAG, "Navigating tab $activeId to $finalUrl (engine=${matchedShortcutEngine?.id ?: "default"})")
        webViewPool?.onTabActivated(activeId, false)
        if (finalUrl.isEmpty()) {
            tabLoadingProgress[activeId] = 100
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
                    loadingProgress = 0f,
                    isMenuExpanded = false,
                    isSearchEngineSwitcherEditExpanded = false,
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
        if (!sameDomain) {
            FaviconManager.deleteTabFavicons(activeId)
        }
        val cachedFavicon = if (!sameDomain || currentTab?.faviconUrl == null) {
            FaviconManager.getCachedFaviconForUrl(finalUrl)
        } else null
        val newFavicon = if (sameDomain && currentTab?.faviconUrl != null) {
            currentTab.faviconUrl
        } else {
            cachedFavicon
        }

        tabLoadingProgress[activeId] = 10
        updateUiState {
            val updatedTabs =
                it.tabs.map { tab ->
                    if (tab.id == activeId) tab.copy(url = finalUrl, title = finalUrl, faviconUrl = newFavicon) else tab
                }
            it.copy(
                tabs = updatedTabs,
                currentUrl = finalUrl,
                pageTitle = finalUrl,
                settings = if (matchedShortcutEngine != null) it.settings.copy(searchEngine = matchedShortcutEngine) else it.settings,
                isLoading = true,
                loadingProgress = 0.1f,
                activeThemeColor = tabThemeColors[activeId] ?: it.tabs.find { tab -> tab.id == activeId }?.themeColor,
                isVideoPlaying = false,
                isMenuExpanded = false,
                isSearchEngineSwitcherEditExpanded = false,
                activeOverlay = OverlayType.NONE,
            )
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
            if (newFavicon != currentTab?.faviconUrl) {
                tabRepository.updateFavicon(activeId, newFavicon)
            }
        }
        emitNavigation(NavigationCommand.LoadUrl(finalUrl))
    }

    fun handlePageStarted(tabId: String, url: String) {
        if (url.isEmpty() || url == "about:blank") return
        Log.d(TAG, "Page started in tab $tabId: $url")
        // A new navigation supersedes any offer queued for a previous page of this tab.
        pendingExternalAppPrompts.remove(tabId)
        tabLoadingProgress[tabId] = 15
        val currentTab = getCurrentState().tabs.find { it.id == tabId }
        val sameDomain = currentTab != null && isSameDomain(currentTab.url, url)
        if (!sameDomain) {
            FaviconManager.deleteTabFavicons(tabId)
        }
        val cachedFavicon = if (!sameDomain || currentTab?.faviconUrl == null) {
            FaviconManager.getCachedFaviconForUrl(url)
        } else null
        val newFavicon = if (sameDomain && currentTab?.faviconUrl != null) {
            currentTab.faviconUrl
        } else {
            cachedFavicon
        }

        val shouldResetTitle = currentTab != null && currentTab.url != url && !sameDomain
        val newTitle = if (shouldResetTitle) "Loading..." else null

        if (!sameDomain) {
            dismissedExternalAppDomains[tabId]?.clear()
            timedOutExternalAppDomains[tabId]?.clear()
        }

        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        title = newTitle ?: tab.title,
                        faviconUrl = newFavicon
                    )
                } else tab
            }
            if (state.activeTabId == tabId) {
                val isPromptForCurrentNavigation = state.externalAppPrompt?.let { prompt ->
                    isSameDomain(prompt.domain, url) ||
                        isSameDomain(prompt.intentUri, url) ||
                        com.jestk.browser.engine.ExternalAppResolver.areDomainAliases(
                            prompt.domain,
                            com.jestk.browser.engine.ExternalAppResolver.extractDomain(url)
                        )
                } ?: false

                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    pageTitle = if (shouldResetTitle) (if (url.isEmpty()) "Home" else "Loading...") else state.pageTitle,
                    isLoading = true,
                    loadingProgress = 0.15f,
                    isBottomBarVisible = true,
                    externalAppPrompt = if (sameDomain || isPromptForCurrentNavigation) state.externalAppPrompt else null,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
        if (shouldResetTitle) {
            scope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(
                    id = tabId,
                    url = url,
                    title = "Loading...",
                    canGoBack = currentTab?.canGoBack ?: false,
                    canGoForward = currentTab?.canGoForward ?: false
                )
            }
        }
        if (newFavicon != currentTab?.faviconUrl) {
            scope.launch(ioDispatcher) {
                tabRepository.updateFavicon(tabId, newFavicon)
            }
        }
    }

    fun handlePageFinished(tabId: String, url: String, title: String) {
        if (url.isEmpty() || url == "about:blank") return
        Log.d(TAG, "Page finished in tab $tabId: $url")
        tabLoadingProgress[tabId] = 100
        val displayTitle = title.ifEmpty { url }
        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(url = url, title = displayTitle) else tab
            }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                    loadingProgress = 1.0f,
                )
            } else {
                state.copy(tabs = updatedTabs)
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
                val effectiveWorkspaceId = tab?.workspaceId ?: getCurrentState().activeWorkspaceId
                historyRepository.recordVisit(url, displayTitle, workspaceId = effectiveWorkspaceId)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = displayTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl,
                    workspaceId = effectiveWorkspaceId,
                )
            }
        }
    }

    fun handleTitleReceived(tabId: String, title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty() || trimmedTitle.equals("about:blank", ignoreCase = true)) return
        val currentTab = getCurrentState().tabs.find { it.id == tabId }
        val effectiveUrl = currentTab?.url.orEmpty()
        if (effectiveUrl.isEmpty() || effectiveUrl == "about:blank") return

        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(title = trimmedTitle) else tab
            }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    pageTitle = trimmedTitle,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }

        scope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            val url = tab?.url?.takeIf { it.isNotEmpty() } ?: effectiveUrl
            tabRepository.updateTabDetails(
                id = tabId,
                url = url,
                title = trimmedTitle,
                canGoBack = tab?.canGoBack ?: (if (getCurrentState().activeTabId == tabId) getCurrentState().canGoBack else false),
                canGoForward = tab?.canGoForward ?: false,
            )
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                val effectiveWorkspaceId = tab?.workspaceId ?: getCurrentState().activeWorkspaceId
                historyRepository.recordVisit(url, trimmedTitle, workspaceId = effectiveWorkspaceId)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = trimmedTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl,
                    workspaceId = effectiveWorkspaceId,
                )
            }
        }
    }

    fun handleProgressChanged(tabId: String, progress: Int) {
        tabLoadingProgress[tabId] = progress
        if (getCurrentState().activeTabId == tabId) {
            updateUiState {
                it.copy(
                    loadingProgress = (progress / 100f).coerceIn(0f, 1f),
                    isLoading = progress < 100,
                )
            }
        }
    }

    fun handleStopLoading(tabId: String? = null) {
        val targetId = tabId ?: getCurrentState().activeTabId ?: return
        tabLoadingProgress[targetId] = 100
        if (getCurrentState().activeTabId == targetId) {
            updateUiState { it.copy(isLoading = false) }
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
                recentTabRepository?.updateSnapshot(tab.url, path, tab.workspaceId)
            }
        }
    }

    fun handleUpdateFavicon(tabId: String, faviconUrl: String?, pageUrl: String? = null) {
        val current = getCurrentState().tabs.find { it.id == tabId } ?: return

        // 1. If pageUrl is specified, verify that it matches current tab's domain
        if (!pageUrl.isNullOrBlank() && current.url.isNotBlank() && current.url != "about:blank") {
            if (!isSameDomain(current.url, pageUrl)) {
                Log.d(TAG, "Ignoring favicon for tab $tabId: pageUrl ($pageUrl) does not match tab url (${current.url})")
                return
            }
        }

        // 2. If faviconUrl points to a domain-specific cache file, verify that it matches current tab's domain
        if (!faviconUrl.isNullOrBlank() && current.url.isNotBlank() && current.url != "about:blank") {
            val fileName = java.io.File(faviconUrl).name
            if (fileName.startsWith("domain_") && fileName.endsWith(".png")) {
                val fileHost = fileName.removePrefix("domain_").removeSuffix(".png")
                val currentHost = FaviconManager.extractHost(current.url)?.let { FaviconManager.sanitizeHost(it) }
                if (currentHost != null && fileHost != currentHost) {
                    Log.d(TAG, "Ignoring favicon for tab $tabId: file domain ($fileHost) does not match current host ($currentHost)")
                    return
                }
            }
        }

        // Pages report their icon repeatedly. The favicon path is stable, so an unchanged value
        // carries no new information and must not trigger another write and state update.
        if (current.faviconUrl == faviconUrl) return
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
                val effectiveUrl = pageUrl ?: tab.url
                if (isSameDomain(tab.url, effectiveUrl)) {
                    recentTabRepository?.updateFavicon(tab.url, faviconUrl, tab.workspaceId)
                    FaviconManager.cacheFaviconPathForDomain(tab.url, faviconUrl)
                    if (favoriteRepository.isFavorite(tab.url, tab.workspaceId)) {
                        val persistentIconPath = if (faviconUrl.startsWith("/") || faviconUrl.startsWith("file:")) {
                            FaviconManager.persistFaviconForFavorite(faviconUrl)
                        } else null
                        val iconToUpdate = persistentIconPath ?: faviconUrl
                        favoriteRepository.updateFavicon(tab.url, iconToUpdate)
                    }
                }
            }
        }
    }

    fun handleThemeColor(tabId: String, color: Int?) {
        handleThemeColors(tabId, color, tabBottomThemeColors[tabId])
    }

    fun handleThemeColors(tabId: String, topColor: Int?, bottomColor: Int?) {
        if (topColor != null) {
            tabThemeColors[tabId] = topColor
        } else {
            tabThemeColors.remove(tabId)
        }
        if (bottomColor != null) {
            tabBottomThemeColors[tabId] = bottomColor
        } else if (topColor == null) {
            tabBottomThemeColors.remove(tabId)
        }
        scope.launch(ioDispatcher) {
            tabRepository.updateThemeColor(tabId, topColor)
        }
        val currentBottomColor = tabBottomThemeColors[tabId]
        updateUiState { state ->
            val updatedTabs =
                state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            themeColor = topColor,
                            bottomThemeColor = currentBottomColor ?: tab.bottomThemeColor,
                        )
                    } else tab
                }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    activeThemeColor = topColor,
                    activeBottomThemeColor = currentBottomColor ?: state.activeBottomThemeColor,
                )
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
            val targetWorkspace = activeTab?.workspaceId ?: current.activeWorkspaceId
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
                    workspaceId = targetWorkspace,
                )
            }
        }
    }

    fun addFavorite(title: String, url: String, workspaceId: String? = null) {
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
            val targetWorkspace = workspaceId ?: getCurrentState().activeWorkspaceId
            favoriteRepository.addFavorite(derivedTitle, normalizedUrl, icon, targetWorkspace)
        }
    }

    fun deleteFavorite(id: Long) {
        scope.launch(ioDispatcher) {
            favoriteRepository.deleteFavorite(id)
        }
    }

    suspend fun createFolder(tabId: String, title: String = "", color: Int): TabFolder {
        val folderId = java.util.UUID.randomUUID().toString()
        val existingFolders = tabFolderRepository?.getAllFoldersSync() ?: getCurrentState().folders
        val nextPosition = (existingFolders.maxOfOrNull { it.position } ?: -1) + 1
        val folder = TabFolder(
            id = folderId,
            title = title,
            color = color,
            isCollapsed = false,
            position = nextPosition,
            workspaceId = getCurrentState().activeWorkspaceId,
        )
        tabFolderRepository?.insertFolder(folder)
        tabRepository.updateFolderId(tabId, folderId)

        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(folderId = folderId) else tab
            }
            val updatedFolders = if (state.folders.none { it.id == folderId }) {
                state.folders + folder
            } else {
                state.folders.map { if (it.id == folderId) folder else it }
            }
            state.copy(tabs = updatedTabs, folders = updatedFolders)
        }
        return folder
    }

    suspend fun createFolderFromTabs(tabId1: String, tabId2: String, color: Int? = null): TabFolder {
        val folderColor = color ?: TabFolder.PALETTE.random()
        val folder = createFolder(tabId = tabId1, title = "", color = folderColor)
        addTabToFolder(tabId = tabId2, folderId = folder.id)
        return folder
    }

    suspend fun updateFolder(folderId: String, title: String, color: Int) {
        val current = tabFolderRepository?.getFolderById(folderId)
            ?: getCurrentState().getFolder(folderId)
            ?: return
        val updated = current.copy(title = title, color = color)
        tabFolderRepository?.updateFolder(updated)
        updateUiState { state ->
            state.copy(folders = state.folders.map { if (it.id == folderId) updated else it })
        }
    }

    suspend fun toggleFolderCollapse(folderId: String, fromDock: Boolean = false) {
        val currentFolder = tabFolderRepository?.getFolderById(folderId)
            ?: getCurrentState().getFolder(folderId)
            ?: return
        val newCollapsed = !currentFolder.isCollapsed
        val updatedFolder = currentFolder.copy(isCollapsed = newCollapsed)
        tabFolderRepository?.updateFolder(updatedFolder)
        updateUiState { state ->
            state.copy(folders = state.folders.map { if (it.id == folderId) updatedFolder else it })
        }

        if (fromDock && newCollapsed) {
            val state = getCurrentState()
            val activeTabId = state.activeTabId
            val activeTab = state.activeTab
            if (activeTab != null && activeTab.folderId == folderId) {
                val currentTabs = state.tabs
                val activeIndex = currentTabs.indexOfFirst { it.id == activeTabId }
                val eligibleTabs = currentTabs.filter { tab ->
                    tab.folderId != folderId && (tab.folderId == null || getCurrentState().getFolder(tab.folderId)?.isCollapsed == false)
                }

                if (eligibleTabs.isEmpty()) {
                    tabOperationMutex.withLock {
                        val created = tabRepository.createTab(url = "", title = "Home")
                        activateNewTabState(created, "", "Home", null, isWebLoading = false, isHome = true)
                    }
                } else {
                    val nearestTab = eligibleTabs.minByOrNull { tab ->
                        val tabIdx = currentTabs.indexOf(tab)
                        kotlin.math.abs(tabIdx - activeIndex)
                    }
                    if (nearestTab != null) {
                        selectTab(nearestTab.id)
                    }
                }
            }
        }
    }

    suspend fun addTabToFolder(tabId: String, folderId: String) {
        tabRepository.updateFolderId(tabId, folderId)
        updateUiState { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) it.copy(folderId = folderId) else it }
            )
        }
    }

    suspend fun removeTabFromFolder(tabId: String) {
        val currentTabs = getCurrentState().tabs
        val targetTab = currentTabs.find { it.id == tabId }
        val folderId = targetTab?.folderId

        tabRepository.updateFolderId(tabId, null)
        updateUiState { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) it.copy(folderId = null) else it }
            )
        }

        if (folderId != null) {
            val remainingInFolder = currentTabs.filter { it.folderId == folderId && it.id != tabId }
            checkAndPruneEmptyFolder(folderId, remainingInFolder)
        }
    }

    private fun checkAndPruneEmptyFolder(folderId: String, remainingTabs: List<Tab>) {
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

    suspend fun ungroupFolder(folderId: String) {
        tabRepository.clearFolderId(folderId)
        tabFolderRepository?.deleteFolder(folderId)
        updateUiState { state ->
            state.copy(
                tabs = state.tabs.map { if (it.folderId == folderId) it.copy(folderId = null) else it },
                folders = state.folders.filter { it.id != folderId }
            )
        }
    }

    suspend fun closeFolder(folderId: String) {
        val tabsInFolder = getCurrentState().tabs.filter { it.folderId == folderId }
        tabFolderRepository?.deleteFolder(folderId)
        updateUiState { state ->
            state.copy(folders = state.folders.filter { it.id != folderId })
        }
        val (active, nonActive) = tabsInFolder.partition { it.id == getCurrentState().activeTabId }
        nonActive.forEach { tab ->
            closeTab(tab.id)
        }
        active.forEach { tab ->
            closeTab(tab.id)
        }
    }

    fun createTabInFolder(folderId: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = "", title = "Home", workspaceId = activeWorkspaceId)
                tabRepository.updateFolderId(created.id, folderId)
                val tabWithFolder = created.copy(folderId = folderId)

                // Ensure folder is expanded
                val currentFolder = tabFolderRepository?.getFolderById(folderId) ?: getCurrentState().getFolder(folderId)
                if (currentFolder != null && currentFolder.isCollapsed) {
                    val expanded = currentFolder.copy(isCollapsed = false)
                    tabFolderRepository?.updateFolder(expanded)
                    updateUiState { state ->
                        state.copy(folders = state.folders.map { if (it.id == folderId) expanded else it })
                    }
                }

                // Insert tab after the last tab of this folder if exists
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id && it.workspaceId == activeWorkspaceId }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val lastFolderIndex = baseTabs.indexOfLast { it.folderId == folderId }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val activatedTab = tabWithFolder.copy(isActive = true)
                val updatedTabs = if (lastFolderIndex >= 0 && lastFolderIndex < currentTabs.size) {
                    currentTabs.toMutableList().apply { add(lastFolderIndex + 1, activatedTab) }
                } else {
                    currentTabs + activatedTab
                }.distinctBy { it.id }

                tabRepository.setActiveTab(created.id)
                dismissedExternalAppDomains.remove(created.id)
                pendingExternalAppPrompts.remove(created.id)
                timedOutExternalAppDomains.remove(created.id)
                webViewPool?.onTabActivated(created.id, false)
                tabLoadingProgress[created.id] = 100

                tabRepository.reorderTabs(updatedTabs)

                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        isLoading = false,
                        loadingProgress = 0f,
                        activeThemeColor = null,
                        activeBottomThemeColor = null,
                        isMenuExpanded = false,
                        isSearchEngineSwitcherEditExpanded = false,
                        externalAppPrompt = null,
                        activeOverlay = OverlayType.HOME,
                        linkContextMenuUrl = null,
                        selectedImageUrl = null,
                        isBottomBarVisible = true,
                    )
                }
            }
        }
    }

    fun switchWorkspace(targetWorkspaceId: String) {
        if (getCurrentState().activeWorkspaceId == targetWorkspaceId) return
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                switchWorkspaceInternal(targetWorkspaceId)
            }
        }
    }

    private suspend fun switchWorkspaceInternal(targetWorkspaceId: String, preserveOverview: Boolean = false) {
        val allTabs = if (cachedAllTabs.isNotEmpty()) cachedAllTabs else tabRepository.getInitialTabs()
        val targetTabs = allTabs.filter { it.workspaceId == targetWorkspaceId }
        val allFolders = if (cachedAllFolders.isNotEmpty()) cachedAllFolders else (tabFolderRepository?.getAllFoldersSync() ?: emptyList())
        val targetFolders = allFolders.filter { it.workspaceId == targetWorkspaceId }

        activeWorkspaceIdFlow.value = targetWorkspaceId
        val targetRecentlyClosed = recentlyClosedTabsByWorkspace[targetWorkspaceId]

        val overlay = if (preserveOverview) OverlayType.WORKSPACES else null

        if (targetTabs.isEmpty()) {
            val newTab = tabRepository.createTab(url = "", title = "Home", workspaceId = targetWorkspaceId)
            tabRepository.setActiveTab(newTab.id)
            webViewPool?.onTabActivated(newTab.id, false)
            tabLoadingProgress[newTab.id] = 100
            val activeTab = newTab.copy(isActive = true)
            updateUiState { state ->
                state.copy(
                    activeWorkspaceId = targetWorkspaceId,
                    tabs = listOf(activeTab),
                    folders = targetFolders,
                    activeTabId = activeTab.id,
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    loadingProgress = 0f,
                    canGoBack = false,
                    canGoForward = false,
                    activeThemeColor = null,
                    activeBottomThemeColor = null,
                    activeOverlay = overlay ?: OverlayType.HOME,
                    activeScreen = ScreenType.NONE,
                    recentlyClosedTab = targetRecentlyClosed,
                    showUndoTabCloseSnackbar = targetRecentlyClosed != null,
                )
            }
        } else {
            val targetActive = targetTabs.find { it.isActive }
                ?: targetTabs.maxByOrNull { it.lastActiveTimestamp }
                ?: targetTabs.first()

            tabRepository.setActiveTab(targetActive.id)
            updateUiState { state ->
                state.copy(
                    activeWorkspaceId = targetWorkspaceId,
                    folders = targetFolders,
                    activeScreen = ScreenType.NONE,
                    recentlyClosedTab = targetRecentlyClosed,
                    showUndoTabCloseSnackbar = targetRecentlyClosed != null,
                )
            }
            activateTabInternal(
                selected = targetActive,
                tabsList = targetTabs,
                keepOverlayIfTabGrid = false,
            )
            if (overlay != null) {
                updateUiState { it.copy(activeOverlay = overlay) }
            }
        }
    }

    fun createWorkspace(name: String, color: Int) {
        scope.launch(ioDispatcher) {
            val currentWorkspaces = workspaceRepository?.getAllWorkspacesSync() ?: getCurrentState().workspaces
            val id = "ws_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
            val position = (currentWorkspaces.maxOfOrNull { it.position } ?: 0) + 1
            val workspace = Workspace(
                id = id,
                name = name.trim().ifEmpty { "Workspace ${position + 1}" },
                color = color,
                position = position,
                profileName = id,
                createdAt = System.currentTimeMillis(),
            )
            workspaceRepository?.insertWorkspace(workspace)
        }
    }

    fun updateWorkspace(workspace: Workspace) {
        scope.launch(ioDispatcher) {
            workspaceRepository?.updateWorkspace(workspace)
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val currentWorkspaces = workspaceRepository?.getAllWorkspacesSync() ?: getCurrentState().workspaces
                if (currentWorkspaces.size <= 1) return@withLock // Cannot delete the last workspace

                val wasActive = (getCurrentState().activeWorkspaceId == workspaceId)
                val nextWorkspace = currentWorkspaces.firstOrNull { it.id != workspaceId }

                if (wasActive && nextWorkspace != null) {
                    val wasInOverview = (getCurrentState().activeOverlay == OverlayType.WORKSPACES)
                    switchWorkspaceInternal(nextWorkspace.id, preserveOverview = wasInOverview)
                }

                // Close and clean up all tabs in the deleted workspace
                val allTabs = if (cachedAllTabs.isNotEmpty()) cachedAllTabs else tabRepository.getInitialTabs()
                val tabsToDelete = allTabs.filter { it.workspaceId == workspaceId }
                tabsToDelete.forEach { tab ->
                    tabThemeColors.remove(tab.id)
                    tabBottomThemeColors.remove(tab.id)
                    tabLoadingProgress.remove(tab.id)
                    dismissedExternalAppDomains.remove(tab.id)
                    pendingExternalAppPrompts.remove(tab.id)
                    timedOutExternalAppDomains.remove(tab.id)
                    webViewPool?.removeWebView(tab.id)
                    tabRepository.deleteTab(tab.id)
                }

                // Clean up folders in the deleted workspace
                val allFolders = if (cachedAllFolders.isNotEmpty()) cachedAllFolders else (tabFolderRepository?.getAllFoldersSync() ?: emptyList())
                allFolders.filter { it.workspaceId == workspaceId }.forEach { folder ->
                    tabFolderRepository?.deleteFolder(folder.id)
                }

                // Delete profile and workspace itself
                val wsToDelete = currentWorkspaces.find { it.id == workspaceId }
                val profileToDelete = wsToDelete?.profileName ?: workspaceId
                webViewPool?.deleteProfile(profileToDelete)
                workspaceRepository?.deleteWorkspace(workspaceId)
            }
        }
    }

    fun moveTabToWorkspace(tabId: String, targetWorkspaceId: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val tab = tabRepository.getTabById(tabId) ?: return@withLock
                if (tab.workspaceId == targetWorkspaceId) return@withLock

                val sourceWorkspaceId = tab.workspaceId
                val isCurrentActive = (getCurrentState().activeTabId == tabId)
                val currentUiTabs = getCurrentState().tabs

                // Move tab in DB: update workspaceId and clear folderId
                webViewPool?.removeWebView(tabId)
                tabRepository.updateWorkspaceId(id = tabId, workspaceId = targetWorkspaceId, folderId = null)

                // Prune folder in source workspace if needed
                tab.folderId?.let { oldFolderId ->
                    val remainingSource = currentUiTabs.filter { it.id != tabId }
                    checkAndPruneEmptyFolder(oldFolderId, remainingSource)
                }

                if (sourceWorkspaceId == getCurrentState().activeWorkspaceId) {
                    val remainingTabs = currentUiTabs.filter { it.id != tabId }
                    if (isCurrentActive) {
                        if (remainingTabs.isEmpty()) {
                            val newTab = tabRepository.createTab(url = "", title = "Home", workspaceId = sourceWorkspaceId)
                            tabRepository.setActiveTab(newTab.id)
                            webViewPool?.onTabActivated(newTab.id, false)
                            tabLoadingProgress[newTab.id] = 100
                            updateUiState { state ->
                                state.copy(
                                    tabs = listOf(newTab.copy(isActive = true)),
                                    activeTabId = newTab.id,
                                    currentUrl = "",
                                    pageTitle = "Home",
                                    isLoading = false,
                                    loadingProgress = 0f,
                                    activeThemeColor = null,
                                    activeBottomThemeColor = null,
                                    activeOverlay = OverlayType.HOME,
                                )
                            }
                        } else {
                            val closedIdx = currentUiTabs.indexOfFirst { it.id == tabId }
                            val nextActive = if (closedIdx > 0) {
                                currentUiTabs[closedIdx - 1]
                            } else {
                                remainingTabs.first()
                            }
                            tabRepository.setActiveTab(nextActive.id)
                            activateTabInternal(
                                selected = nextActive,
                                tabsList = remainingTabs,
                                keepOverlayIfTabGrid = true,
                            )
                        }
                    } else {
                        updateUiState { state ->
                            state.copy(tabs = state.tabs.filterNot { it.id == tabId })
                        }
                    }
                }
            }
        }
    }

    fun clearRecentlyClosedTab(workspaceId: String = getCurrentState().activeWorkspaceId) {
        recentlyClosedTabsByWorkspace.remove(workspaceId)
        updateUiState { state ->
            if (state.activeWorkspaceId == workspaceId) {
                state.copy(recentlyClosedTab = null, showUndoTabCloseSnackbar = false)
            } else {
                state
            }
        }
    }

    fun moveFavoriteToWorkspace(id: Long, targetWorkspaceId: String) {
        scope.launch(ioDispatcher) {
            favoriteRepository.moveFavoriteToWorkspace(id, targetWorkspaceId)
        }
    }
}
