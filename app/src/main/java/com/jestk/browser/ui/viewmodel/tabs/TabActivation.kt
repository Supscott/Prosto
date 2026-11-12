package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val TAG = "TabCoordinator"

internal class TabActivation(
    private val ctx: TabCoordinatorContext,
    private val externalAppPrompts: ExternalAppPrompts,
) {
    private val tabRepository = ctx.tabRepository
    private val webViewPool = ctx.webViewPool
    private val tabOperationMutex = ctx.tabOperationMutex
    private val tabThemeColors = ctx.tabThemeColors
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val setLoadingProgress = ctx.setLoadingProgress
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val tabBottomThemeColors = ctx.tabBottomThemeColors
    private val tabFolderRepository = ctx.tabFolderRepository

    fun selectTab(tabId: String) {
        val selected = getCurrentState().tabs.find { it.id == tabId }
        if (selected == null) {
            Log.w(TAG, "Select tab $tabId requested but the tab is not in the current state")
            return
        }
        activateTabInternal(
            selected = selected,
            keepOverlayIfTabGrid = false,
        )
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                tabRepository.setActiveTab(tabId)
            }
        }
    }

    /** @param removedTabId a tab that has just been closed or moved away and must not reappear. */
    fun activateTabInternal(
        selected: Tab,
        removedTabId: String? = null,
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
        if (liveWebView == null) ctx.holdUntilFirstPaintIfRecreated(tabId, effectiveUrl)
        val effectiveTitle = liveTitle ?: (if (liveUrl != null && selected.title.isEmpty()) liveUrl else selected.title)
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
                state.tabs.filter { it.id != removedTabId }.map { t ->
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
        setLoadingProgress(tabProgress)
        externalAppPrompts.publishPendingExternalAppPrompt(tabId)
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
}
