package com.jestk.browser.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.ExternalAppPrompt
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
    } catch (e: Throwable) {
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
) {
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
            Log.d(TAG, "Restored session with active tab ${activeTab.id} (${tabsWithActive.size} tabs)")
            val hasWebUrl = activeTab.url.isNotEmpty() && activeTab.url != "about:blank"
            val initialThemeColor = if (hasWebUrl) (tabThemeColors[activeTab.id] ?: activeTab.themeColor) else null
            if (hasWebUrl) {
                tabLoadingProgress[activeTab.id] = 15
            } else {
                tabLoadingProgress[activeTab.id] = 100
            }

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
                    isSearchEngineSwitcherEditExpanded = false,
                    activeOverlay = if (hasWebUrl) OverlayType.NONE else OverlayType.HOME,
                )
            }
        }
    }

    fun observeDatabaseFlows() {
        scope.launch(ioDispatcher) {
            tabRepository.getAllTabs().collectLatest { tabs ->
                updateUiState { state ->
                    if (state.tabs.any { it.id == state.activeTabId } && tabs.none { it.id == state.activeTabId }) {
                        return@updateUiState state
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
                            if (cached != null) {
                                tabThemeColors[tab.id] = cached
                            }
                            tab.copy(
                                url = tabUrl,
                                title = tabTitle,
                                isActive = isActive,
                                themeColor = cached ?: tab.themeColor,
                                faviconUrl = tabFavicon,
                            )
                        }
                    val currentTab = enrichedTabs.find { it.id == activeId }
                    val isHome = currentTab?.url.isNullOrEmpty() || currentTab?.url == "about:blank"
                    val activeColor = if (activeId != null) tabThemeColors[activeId] else null
                    val resolvedUrl = if (isSameAsActive) state.currentUrl else (currentTab?.url ?: state.currentUrl)
                    val resolvedTitle = if (isSameAsActive) state.pageTitle else (currentTab?.title ?: state.pageTitle)
                    state.copy(
                        tabs = enrichedTabs,
                        activeTabId = activeId,
                        currentUrl = resolvedUrl,
                        pageTitle = resolvedTitle,
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
                Log.d(TAG, "Created search tab ${created.id}")
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
                val cachedFavicon = if (url.isNotEmpty() && url != "about:blank") {
                    FaviconManager.getCachedFaviconForUrl(url)
                } else null
                val created = tabRepository.createTab(url = url, title = title)
                Log.d(TAG, "Created tab ${created.id} for $url")
                tabRepository.setActiveTab(created.id)
                // A new external navigation is a fresh user intent: the tab may be reused when
                // the same URL is already open, so per-tab suppression must not leak into it.
                dismissedExternalAppDomains.remove(created.id)
                pendingExternalAppPrompts.remove(created.id)
                timedOutExternalAppDomains.remove(created.id)
                if (cachedFavicon != null) {
                    tabRepository.updateFavicon(created.id, cachedFavicon)
                }
                webViewPool?.onTabActivated(created.id, false)
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
                    } else {
                        getCurrentState().tabs.filter { it.id != created.id }
                    }
                val currentTabs = baseTabs.map { it.copy(isActive = false) }
                val updatedTabs = (currentTabs + created.copy(isActive = true, faviconUrl = cachedFavicon)).distinctBy { it.id }
                val isHome = url.isEmpty() || url == "about:blank"
                val isWebLoading = !isHome && url.isNotEmpty()
                if (isWebLoading) {
                    tabLoadingProgress[created.id] = 15
                } else {
                    tabLoadingProgress[created.id] = 100
                }
                updateUiState {
                    it.copy(
                        tabs = updatedTabs,
                        activeTabId = created.id,
                        currentUrl = url,
                        pageTitle = title,
                        isLoading = isWebLoading,
                        loadingProgress = if (isWebLoading) 0.15f else 0f,
                        activeThemeColor = null,
                        isMenuExpanded = false,
                        isSearchEngineSwitcherEditExpanded = false,
                        externalAppPrompt = null,
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
                val cachedFavicon = if (url.isNotEmpty() && url != "about:blank") {
                    FaviconManager.getCachedFaviconForUrl(url)
                } else null
                val created = tabRepository.createTab(url = url, title = title)
                Log.d(TAG, "Created background tab ${created.id} for $url")
                if (cachedFavicon != null) {
                    tabRepository.updateFavicon(created.id, cachedFavicon)
                }
                val baseTabs =
                    if (getCurrentState().tabs.isEmpty()) {
                        tabRepository.getInitialTabs().filter { it.id != created.id }
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

    fun openRecentTab(item: RecentTabItem, onNavigateUrl: (String) -> Unit) {
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
                openNewTab(item.url)
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
            baseTabs = getCurrentState().tabs,
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
        baseTabs: List<Tab>,
        keepOverlayIfTabGrid: Boolean = false,
        recentlyClosedTab: Tab? = null,
        showUndoSnackbar: Boolean = false,
    ) {
        val tabId = selected.id
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
        val progress = tabLoadingProgress[tabId]
        val isTabLoading = progress != null && progress < 100
        val tabProgress = when {
            isTabLoading -> (progress!! / 100f).coerceIn(0f, 1f)
            isHome -> 0f
            else -> 1.0f
        }

        updateUiState { state ->
            val updatedTabs =
                baseTabs.map { t ->
                    val c = tabThemeColors[t.id] ?: t.themeColor
                    if (t.id == tabId) {
                        t.copy(
                            isActive = true,
                            url = effectiveUrl,
                            title = effectiveTitle,
                            canGoBack = effectiveCanGoBack,
                            canGoForward = effectiveCanGoForward,
                            themeColor = c
                        )
                    } else {
                        t.copy(isActive = false, themeColor = c)
                    }
                }
            val targetOverlay = when {
                keepOverlayIfTabGrid && state.activeOverlay == OverlayType.TAB_GRID -> OverlayType.TAB_GRID
                isHome -> OverlayType.HOME
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
                isMenuExpanded = false,
                isSearchEngineSwitcherEditExpanded = false,
                externalAppPrompt = pendingExternalAppPrompts[tabId],
                activeOverlay = targetOverlay,
                isBottomBarVisible = true,
                recentlyClosedTab = if (showUndoSnackbar) recentlyClosedTab else state.recentlyClosedTab,
                showUndoTabCloseSnackbar = if (showUndoSnackbar) (recentlyClosedTab != null) else state.showUndoTabCloseSnackbar,
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
                if (effectiveUrl.isNotEmpty() && !effectiveUrl.startsWith("about:") && effectiveTitle.isNotEmpty()) {
                    historyRepository.recordVisit(effectiveUrl, effectiveTitle)
                }
            }
        }
    }

    fun closeTab(tabId: String) {
        Log.d(TAG, "Closing tab $tabId")
        tabThemeColors.remove(tabId)
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

        if (remainingTabs.isEmpty()) {
            scope.launch(ioDispatcher) {
                SnapshotEngine.deleteTabSnapshot(tabId)
                FaviconManager.deleteTabFavicons(tabId)
                tabOperationMutex.withLock {
                    val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                    tabRepository.closeTab(tabId)
                    val newTab = tabRepository.createTab(url = "", title = "Home")
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
                if (!wasActive && remainingTabs.any { it.id == getCurrentState().activeTabId }) {
                    remainingTabs.first { it.id == getCurrentState().activeTabId }
                } else if (closedIdx > 0) {
                    currentTabs[closedIdx - 1]
                } else {
                    remainingTabs.first()
                }

            activateTabInternal(
                selected = nextActive,
                baseTabs = remainingTabs,
                keepOverlayIfTabGrid = true,
                recentlyClosedTab = closingTab,
                showUndoSnackbar = true,
            )

            scope.launch(ioDispatcher) {
                SnapshotEngine.deleteTabSnapshot(tabId)
                FaviconManager.deleteTabFavicons(tabId)
                tabOperationMutex.withLock {
                    val tabToSave = closingTab ?: tabRepository.getTabById(tabId)
                    tabRepository.closeTab(tabId)
                    tabRepository.setActiveTab(nextActive.id)
                }
            }
        }
    }

    fun undoCloseTab() {
        val tabToRestore = getCurrentState().recentlyClosedTab ?: return
        Log.d(TAG, "Undo close tab requested for ${tabToRestore.id}")
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val created =
                    tabRepository.createTab(
                        url = tabToRestore.url,
                        title = tabToRestore.title,
                        isIncognito = tabToRestore.isIncognito,
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
                val newTab = tabRepository.createTab(url = "", title = "Home")
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
        Log.d(TAG, "Reordering ${orderedTabs.size} tabs")
        updateUiState { it.copy(tabs = orderedTabs) }
        scope.launch(ioDispatcher) {
            tabRepository.reorderTabs(orderedTabs)
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
                historyRepository.recordVisit(url, trimmedTitle)
                recentTabRepository?.recordRecentTab(
                    url = url,
                    title = trimmedTitle,
                    snapshotPath = tab?.snapshotPath,
                    faviconUrl = tab?.faviconUrl,
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
                recentTabRepository?.updateSnapshot(tab.url, path)
            }
        }
    }

    fun handleUpdateFavicon(tabId: String, faviconUrl: String?) {
        // Pages report their icon repeatedly. The favicon path is stable, so an unchanged value
        // carries no new information and must not trigger another write and state update.
        val current = getCurrentState().tabs.find { it.id == tabId }
        if (current != null && current.faviconUrl == faviconUrl) return
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
                FaviconManager.cacheFaviconPathForDomain(tab.url, faviconUrl)
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
