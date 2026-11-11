package com.jestk.browser.ui.viewmodel.tabs

import android.util.Log
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val TAG = "TabCoordinator"

internal class TabFolders(
    private val ctx: TabCoordinatorContext,
    private val activation: TabActivation,
    private val opening: TabOpening,
    private val closing: TabClosing,
) {
    private val tabRepository = ctx.tabRepository
    private val webViewPool = ctx.webViewPool
    private val tabOperationMutex = ctx.tabOperationMutex
    private val getCurrentState = ctx.getCurrentState
    private val updateUiState = ctx.updateUiState
    private val scope = ctx.scope
    private val ioDispatcher = ctx.ioDispatcher
    private val tabLoadingProgress = ctx.tabLoadingProgress
    private val dismissedExternalAppDomains = ctx.dismissedExternalAppDomains
    private val pendingExternalAppPrompts = ctx.pendingExternalAppPrompts
    private val timedOutExternalAppDomains = ctx.timedOutExternalAppDomains
    private val tabFolderRepository = ctx.tabFolderRepository

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
                    ctx.checkAndPruneEmptyFolder(folder.id, orderedTabs)
                }
            }
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
                        val created = tabRepository.createTab(url = "", title = "", workspaceId = state.activeWorkspaceId)
                        opening.activateNewTabState(created, "", "", null, isWebLoading = false, isHome = true)
                    }
                } else {
                    val nearestTab = eligibleTabs.minByOrNull { tab ->
                        val tabIdx = currentTabs.indexOf(tab)
                        kotlin.math.abs(tabIdx - activeIndex)
                    }
                    if (nearestTab != null) {
                        activation.selectTab(nearestTab.id)
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
            ctx.checkAndPruneEmptyFolder(folderId, remainingInFolder)
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
            closing.closeTab(tab.id)
        }
        active.forEach { tab ->
            closing.closeTab(tab.id)
        }
    }

    fun createTabInFolder(folderId: String) {
        scope.launch(ioDispatcher) {
            tabOperationMutex.withLock {
                val activeWorkspaceId = getCurrentState().activeWorkspaceId
                val created = tabRepository.createTab(url = "", title = "", workspaceId = activeWorkspaceId)
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
                        pageTitle = "",
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
}
