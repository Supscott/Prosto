package com.jestk.browser.ui.viewmodel.tabs

import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.WebViewStateStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal class Workspaces(
    private val ctx: TabCoordinatorContext,
    private val activation: TabActivation,
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
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains
    private val tabBottomThemeColors = ctx.tabBottomThemeColors
    private val tabFolderRepository = ctx.tabFolderRepository
    private val workspaceRepository = ctx.workspaceRepository
    private val recentlyClosedTabsByWorkspace = ctx.recentlyClosedTabsByWorkspace
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
            val newTab = tabRepository.createTab(url = "", title = "", workspaceId = targetWorkspaceId)
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
                    pageTitle = "",
                    isLoading = false,
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
            setLoadingProgress(0f)
        } else {
            val targetActive = targetTabs.find { it.isActive }
                ?: targetTabs.maxByOrNull { it.lastActiveTimestamp }
                ?: targetTabs.first()

            tabRepository.setActiveTab(targetActive.id)
            val updatedTargetTabs = targetTabs.map { t ->
                val c = tabThemeColors[t.id] ?: t.themeColor
                val bc = tabBottomThemeColors[t.id] ?: t.bottomThemeColor
                if (t.id == targetActive.id) {
                    t.copy(isActive = true, themeColor = c, bottomThemeColor = bc)
                } else {
                    t.copy(isActive = false, themeColor = c, bottomThemeColor = bc)
                }
            }
            updateUiState { state ->
                state.copy(
                    activeWorkspaceId = targetWorkspaceId,
                    activeTabId = targetActive.id,
                    tabs = updatedTargetTabs,
                    folders = targetFolders,
                    activeScreen = ScreenType.NONE,
                    recentlyClosedTab = targetRecentlyClosed,
                    showUndoTabCloseSnackbar = targetRecentlyClosed != null,
                )
            }
            activation.activateTabInternal(
                selected = targetActive,
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
                name = name.trim(),
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
                    webViewPool?.removeWebView(tab.id, keepState = false)
                    WebViewStateStore.delete(tab.id)
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
                    ctx.checkAndPruneEmptyFolder(oldFolderId, remainingSource)
                }

                if (sourceWorkspaceId == getCurrentState().activeWorkspaceId) {
                    val remainingTabs = currentUiTabs.filter { it.id != tabId }
                    if (isCurrentActive) {
                        if (remainingTabs.isEmpty()) {
                            val newTab = tabRepository.createTab(url = "", title = "", workspaceId = sourceWorkspaceId)
                            tabRepository.setActiveTab(newTab.id)
                            webViewPool?.onTabActivated(newTab.id, false)
                            tabLoadingProgress[newTab.id] = 100
                            updateUiState { state ->
                                state.copy(
                                    tabs = listOf(newTab.copy(isActive = true)),
                                    activeTabId = newTab.id,
                                    currentUrl = "",
                                    pageTitle = "",
                                    isLoading = false,
                                    activeThemeColor = null,
                                    activeBottomThemeColor = null,
                                    activeOverlay = OverlayType.HOME,
                                )
                            }
                            setLoadingProgress(0f)
                        } else {
                            val closedIdx = currentUiTabs.indexOfFirst { it.id == tabId }
                            val nextActive = if (closedIdx > 0) {
                                currentUiTabs[closedIdx - 1]
                            } else {
                                remainingTabs.first()
                            }
                            tabRepository.setActiveTab(nextActive.id)
                            activation.activateTabInternal(
                                selected = nextActive,
                                removedTabId = tabId,
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
}
