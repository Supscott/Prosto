package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.viewmodel.isSameDomain
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "TabCoordinator"

internal class PageEvents(
    ctx: TabCoordinatorContext,
) {
    private val tabRepository = ctx.tabRepository
    private val recentTabRepository = ctx.recentTabRepository
    private val historyRepository = ctx.historyRepository
    private val webViewPool = ctx.webViewPool
    private val tabThemeColors = ctx.tabThemeColors
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val setLoadingProgress = ctx.setLoadingProgress
    private val emitNavigation = ctx.emitNavigation
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains

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
                        if (tab.id == activeId) tab.copy(url = "", title = "", faviconUrl = null) else tab
                    }
                it.copy(
                    tabs = updatedTabs,
                    currentUrl = "",
                    pageTitle = "",
                    isLoading = false,
                    isMenuExpanded = false,
                    isSearchEngineSwitcherEditExpanded = false,
                    activeOverlay = OverlayType.HOME,
                    homeHoldTabId = null,
                )
            }
            setLoadingProgress(0f)
            scope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(activeId, "", "", canGoBack = false, canGoForward = false)
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
        // Like Chrome's NTP: Home stays on screen until the page paints its first frame.
        val startsFromHome = currentTab == null || currentTab.isHomeTab
        if (startsFromHome) webViewPool?.markAwaitingFirstPaint(activeId)

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
                awaitingFirstPaintTabIds = if (startsFromHome) it.awaitingFirstPaintTabIds + activeId else it.awaitingFirstPaintTabIds,
                homeHoldTabId = if (startsFromHome) activeId else null,
                activeThemeColor = tabThemeColors[activeId] ?: it.tabs.find { tab -> tab.id == activeId }?.themeColor,
                isVideoPlaying = false,
                isMenuExpanded = false,
                isSearchEngineSwitcherEditExpanded = false,
                activeOverlay = OverlayType.NONE,
            )
        }
        setLoadingProgress(0.1f)
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
        val newTitle = if (shouldResetTitle) "" else null

        if (!sameDomain) {
            dismissedExternalAppDomains[tabId]?.clear()
            timedOutExternalAppDomains[tabId]?.clear()
        }

        var isActiveTab = false
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
            isActiveTab = state.activeTabId == tabId
            if (isActiveTab) {
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
                    pageTitle = if (shouldResetTitle) "" else state.pageTitle,
                    isLoading = true,
                    isBottomBarVisible = true,
                    externalAppPrompt = if (sameDomain || isPromptForCurrentNavigation) state.externalAppPrompt else null,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
        if (isActiveTab) setLoadingProgress(0.15f)
        if (shouldResetTitle) {
            scope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(
                    id = tabId,
                    url = url,
                    title = "",
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
        var isActiveTab = false
        updateUiState { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(url = url, title = displayTitle) else tab
            }
            isActiveTab = state.activeTabId == tabId
            if (isActiveTab) {
                state.copy(
                    tabs = updatedTabs,
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
        if (isActiveTab) setLoadingProgress(1.0f)
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
            updateUiState { it.copy(isLoading = progress < 100) }
            setLoadingProgress((progress / 100f).coerceIn(0f, 1f))
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
}
