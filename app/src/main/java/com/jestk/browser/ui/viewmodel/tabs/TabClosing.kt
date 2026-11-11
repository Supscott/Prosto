package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SnapshotEngine
import com.jestk.browser.engine.WebViewStateStore
import com.jestk.browser.ui.viewmodel.NavigationCommand
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val TAG = "TabCoordinator"

internal class TabClosing(
    private val ctx: TabCoordinatorContext,
    private val activation: TabActivation,
) {
    private val tabRepository = ctx.tabRepository
    private val webViewPool = ctx.webViewPool
    private val tabOperationMutex = ctx.tabOperationMutex
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
    private val tabBottomThemeColors = ctx.tabBottomThemeColors
    private val recentlyClosedTabsByWorkspace = ctx.recentlyClosedTabsByWorkspace

    fun closeTab(tabId: String) {
        Log.d(TAG, "Closing tab $tabId")
        tabThemeColors.remove(tabId)
        tabBottomThemeColors.remove(tabId)
        tabLoadingProgress.remove(tabId)
        dismissedExternalAppDomains.remove(tabId)
        pendingExternalAppPrompts.remove(tabId)
        timedOutExternalAppDomains.remove(tabId)
        webViewPool?.removeWebView(tabId)
        WebViewStateStore.onTabClosed(tabId)
        if (tabId in getCurrentState().externalAppTabIds) {
            updateUiState { it.copy(externalAppTabIds = it.externalAppTabIds - tabId) }
        }

        val currentTabs = getCurrentState().tabs
        val closingTab = currentTabs.find { it.id == tabId }
        val closedIdx = currentTabs.indexOfFirst { it.id == tabId }
        val wasActive = getCurrentState().activeTabId == tabId
        val remainingTabs = currentTabs.filter { it.id != tabId }

        closingTab?.folderId?.let { folderId ->
            ctx.checkAndPruneEmptyFolder(folderId, remainingTabs)
        }

        val effectiveWorkspaceId = closingTab?.workspaceId ?: getCurrentState().activeWorkspaceId
        if (closingTab != null) {
            recentlyClosedTabsByWorkspace[effectiveWorkspaceId] = closingTab
        }

        if (!wasActive) {
            updateUiState { state ->
                val updatedTabs = state.tabs.filter { it.id != tabId }.map { t ->
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
                    pageTitle = "",
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
                    val newTab = tabRepository.createTab(url = "", title = "", workspaceId = getCurrentState().activeWorkspaceId)
                    tabRepository.setActiveTab(newTab.id)
                    webViewPool?.onTabActivated(newTab.id, false)
                    tabLoadingProgress[newTab.id] = 100
                    updateUiState {
                        it.copy(
                            tabs = listOf(newTab.copy(isActive = true)),
                            activeTabId = newTab.id,
                            currentUrl = "",
                            pageTitle = "",
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

            activation.activateTabInternal(
                selected = nextActive,
                removedTabId = tabId,
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
                WebViewStateStore.move(tabToRestore.id, created.id)
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


                val isHome = restoredTab.url.isEmpty() || restoredTab.url == "about:blank"
                val isWebLoading = !isHome && restoredTab.url.isNotEmpty()
                ctx.holdUntilFirstPaintIfRecreated(created.id, restoredTab.url)
                if (isWebLoading) {
                    tabLoadingProgress[created.id] = 15
                } else {
                    tabLoadingProgress[created.id] = 100
                }
                val themeColor = if (isHome) null else (tabThemeColors[created.id] ?: restoredTab.themeColor)

                updateUiState { state ->
                    state.copy(
                        tabs = state.tabsWithNewTab(restoredTab, fallbackTabs = emptyList()),
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
        WebViewStateStore.deleteAll()
        scope.launch(ioDispatcher) {
            SnapshotEngine.deleteAllSnapshots()
            FaviconManager.deleteAllFavicons()
            tabOperationMutex.withLock {
                tabRepository.closeAllTabs()
                val newTab = tabRepository.createTab(url = "", title = "", workspaceId = getCurrentState().activeWorkspaceId)
                tabRepository.setActiveTab(newTab.id)
                webViewPool?.onTabActivated(newTab.id, false)
                tabLoadingProgress[newTab.id] = 100
                updateUiState {
                    it.copy(
                        tabs = listOf(newTab.copy(isActive = true)),
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "",
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
}
