package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.adblock.BlockPage
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
    private val emitNavigation = ctx.emitNavigation
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains

    /** Per tab, the URL whose visit is already in history; see [claimVisit]. */
    private val recordedVisitUrls = mutableMapOf<String, String>()

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
                    loadingProgress = 0f,
                    isMenuExpanded = false,
                    isSearchEngineSwitcherEditExpanded = false,
                    activeOverlay = OverlayType.HOME,
                    homeHoldTabId = null,
                )
            }
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
                loadingProgress = 0.1f,
                awaitingFirstPaintTabIds = if (startsFromHome) it.awaitingFirstPaintTabIds + activeId else it.awaitingFirstPaintTabIds,
                homeHoldTabId = if (startsFromHome) activeId else null,
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
        val newTitle = if (shouldResetTitle) "" else null

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
                    pageTitle = if (shouldResetTitle) "" else state.pageTitle,
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
        // A `$document` block page is shown but is not a visit: no history, no recent tab.
        val isBlockPage = BlockPage.isBlockPageTitle(title)
        val displayTitle = BlockPage.displayTitle(title).ifEmpty { url }
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
        val isNewVisit = !isBlockPage && claimVisit(tabId, url)
        scope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            tabRepository.updateTabDetails(
                id = tabId,
                url = url,
                title = displayTitle,
                canGoBack = tab?.canGoBack ?: getCurrentState().canGoBack,
                canGoForward = tab?.canGoForward ?: false,
            )
            if (!isBlockPage) persistVisit(tab, url, displayTitle, isNewVisit)
        }
    }

    fun handleTitleReceived(tabId: String, title: String) {
        val isBlockPage = BlockPage.isBlockPageTitle(title)
        val trimmedTitle = BlockPage.displayTitle(title).trim()
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

        // A title for a page that has no visit yet (a same-document navigation never reports
        // onPageFinished) records it; later titles for the same page only rename that visit.
        val isNewVisit = !isBlockPage && claimVisit(tabId, effectiveUrl)
        scope.launch(ioDispatcher) {
            val tab = tabRepository.getTabById(tabId)
            tabRepository.updateTabDetails(
                id = tabId,
                url = effectiveUrl,
                title = trimmedTitle,
                canGoBack = tab?.canGoBack ?: (if (getCurrentState().activeTabId == tabId) getCurrentState().canGoBack else false),
                canGoForward = tab?.canGoForward ?: false,
            )
            if (!isBlockPage) persistVisit(tab, effectiveUrl, trimmedTitle, isNewVisit)
        }
    }

    /**
     * True when [url] is a new visit for [tabId]: one page load reports several finish and title
     * events (onReceivedTitle, doUpdateVisitedHistory, the back/forward cache finish), and only
     * the first of them may count a visit. Main thread only.
     */
    private fun claimVisit(tabId: String, url: String): Boolean {
        if (url.isEmpty() || url.startsWith("about:")) return false
        return recordedVisitUrls.put(tabId, url) != url
    }

    private suspend fun persistVisit(tab: Tab?, url: String, title: String, isNewVisit: Boolean) {
        // An incognito tab leaves no history visit and no recent-tab entry.
        if (tab?.isIncognito == true || url.isEmpty() || url.startsWith("about:")) return
        val workspaceId = tab?.workspaceId ?: getCurrentState().activeWorkspaceId
        if (isNewVisit) {
            historyRepository.recordVisit(url, title, workspaceId = workspaceId)
        } else {
            historyRepository.updateVisitTitle(url, title, workspaceId = workspaceId)
        }
        recentTabRepository?.recordRecentTab(
            url = url,
            title = title,
            snapshotPath = tab?.snapshotPath,
            faviconUrl = tab?.faviconUrl,
            workspaceId = workspaceId,
        )
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
}
