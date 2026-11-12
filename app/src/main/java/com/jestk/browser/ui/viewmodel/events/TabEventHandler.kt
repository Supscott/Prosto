package com.jestk.browser.ui.viewmodel.events

import android.util.Log
import com.jestk.browser.domain.model.InsecureNavigationTarget
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.TabCoordinator
import com.jestk.browser.ui.viewmodel.TabUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BrowserViewModel"

internal class TabEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val searchCoordinator: SearchCoordinator,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val dispatch: (BrowserUiEvent) -> Unit,
    private val loadUrl: (String) -> Unit,
    private val shouldWarnForInsecureUrl: (String) -> Boolean,
) {
    fun handle(event: TabUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenNewTab -> {
                Log.d(TAG, "Navigation command: open new tab for ${event.url}")
                if (shouldWarnForInsecureUrl(event.url)) {
                    dispatch(
                        BrowserUiEvent.ShowInsecureHttpWarning(
                            url = event.url,
                            target = InsecureNavigationTarget.NEW_TAB,
                        )
                    )
                } else {
                    tabCoordinator.opening.openNewTab(event.url, openedByExternalApp = event.openedByExternalApp)
                }
            }

            is BrowserUiEvent.OpenPopupTab -> {
                Log.d(TAG, "Navigation command: open popup tab for ${event.tabId}")
                tabCoordinator.opening.openPopupTab(event.tabId)
            }

            is BrowserUiEvent.OpenRecentTab -> {
                Log.d(TAG, "Navigation command: open recent tab ${event.item.url}")
                tabCoordinator.opening.openRecentTab(
                    item = event.item,
                    onNavigateUrl = { loadUrl(it) },
                    onOpenNewTab = { dispatch(BrowserUiEvent.OpenNewTab(it)) },
                )
            }

            is BrowserUiEvent.SelectTab -> {
                Log.d(TAG, "Navigation command: select tab ${event.tabId}")
                tabCoordinator.activation.selectTab(event.tabId)
            }

            is BrowserUiEvent.CloseTab -> {
                Log.d(TAG, "Navigation command: close tab ${event.tabId}")
                tabCoordinator.closing.closeTab(event.tabId)
            }

            is BrowserUiEvent.CloseAllTabs -> {
                Log.d(TAG, "Navigation command: close all tabs")
                tabCoordinator.closing.closeAllTabs()
            }

            is BrowserUiEvent.ReorderTabs -> {
                tabCoordinator.folders.reorderTabsAndFolders(event.orderedTabs, event.orderedFolders)
            }

            is BrowserUiEvent.SwipeNextTab -> {
                tabCoordinator.activation.switchAdjacentTab(1)
            }

            is BrowserUiEvent.SwipePrevTab -> {
                tabCoordinator.activation.switchAdjacentTab(-1)
            }

            is BrowserUiEvent.UndoCloseTab -> {
                Log.d(TAG, "Navigation command: undo close tab")
                tabCoordinator.closing.undoCloseTab()
            }

            is BrowserUiEvent.OpenWorkspacesOverview -> {
                Log.d(TAG, "Workspace command: open workspaces overview")
                updateUiState { it.copy(activeOverlay = OverlayType.WORKSPACES) }
            }

            is BrowserUiEvent.CloseWorkspacesOverview -> {
                Log.d(TAG, "Workspace command: close workspaces overview")
                if (getCurrentState().activeOverlay == OverlayType.WORKSPACES) {
                    updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
                }
            }

            is BrowserUiEvent.SwitchWorkspace -> {
                Log.d(TAG, "Workspace command: switch workspace to ${event.workspaceId}")
                tabCoordinator.workspaces.switchWorkspace(event.workspaceId)
                searchCoordinator.updateHistorySuggestions(getCurrentState().searchQuery)
                if (getCurrentState().activeOverlay == OverlayType.WORKSPACES) {
                    updateUiState { it.copy(activeOverlay = OverlayType.NONE) }
                }
            }

            is BrowserUiEvent.CreateWorkspace -> {
                Log.d(TAG, "Workspace command: create workspace ${event.name}")
                tabCoordinator.workspaces.createWorkspace(event.name, event.color)
            }

            is BrowserUiEvent.UpdateWorkspace -> {
                Log.d(TAG, "Workspace command: update workspace ${event.workspace.id}")
                tabCoordinator.workspaces.updateWorkspace(event.workspace)
            }

            is BrowserUiEvent.DeleteWorkspace -> {
                Log.d(TAG, "Workspace command: delete workspace ${event.workspaceId}")
                tabCoordinator.workspaces.deleteWorkspace(event.workspaceId)
            }

            is BrowserUiEvent.MoveTabToWorkspace -> {
                Log.d(TAG, "Workspace command: move tab ${event.tabId} to ${event.targetWorkspaceId}")
                tabCoordinator.workspaces.moveTabToWorkspace(event.tabId, event.targetWorkspaceId)
            }

            is BrowserUiEvent.DismissUndoTabCloseSnackbar -> {
                tabCoordinator.closing.clearRecentlyClosedTab()
            }

            is BrowserUiEvent.OpenTabInBackground -> {
                Log.d(TAG, "Navigation command: open tab in background for ${event.url}")
                if (shouldWarnForInsecureUrl(event.url)) {
                    dispatch(
                        BrowserUiEvent.ShowInsecureHttpWarning(
                            url = event.url,
                            target = InsecureNavigationTarget.BACKGROUND_TAB,
                        )
                    )
                } else {
                    tabCoordinator.opening.openTabInBackground(event.url)
                }
            }

            is BrowserUiEvent.CreateFolderFromTabs -> {
                scope.launch {
                    tabCoordinator.folders.createFolderFromTabs(
                        tabId1 = event.tabId1,
                        tabId2 = event.tabId2,
                        color = event.color,
                    )
                }
            }

            is BrowserUiEvent.OpenCreateFolderSheet -> {
                updateUiState {
                    it.copy(
                        isFolderSheetVisible = true,
                        folderSheetTargetTabId = event.tabId,
                        folderSheetEditingFolderId = null,
                    )
                }
            }

            is BrowserUiEvent.OpenEditFolderSheet -> {
                updateUiState {
                    it.copy(
                        isFolderSheetVisible = true,
                        folderSheetTargetTabId = null,
                        folderSheetEditingFolderId = event.folderId,
                    )
                }
            }

            BrowserUiEvent.DismissFolderSheet -> {
                updateUiState {
                    it.copy(
                        isFolderSheetVisible = false,
                        folderSheetTargetTabId = null,
                        folderSheetEditingFolderId = null,
                    )
                }
            }

            is BrowserUiEvent.SaveFolder -> {
                val targetTabId = getCurrentState().folderSheetTargetTabId ?: getCurrentState().activeTabId
                val editingFolderId = getCurrentState().folderSheetEditingFolderId
                updateUiState {
                    it.copy(
                        isFolderSheetVisible = false,
                        folderSheetTargetTabId = null,
                        folderSheetEditingFolderId = null,
                    )
                }
                scope.launch {
                    if (editingFolderId != null) {
                        tabCoordinator.folders.updateFolder(
                            folderId = editingFolderId,
                            title = event.title,
                            color = event.color,
                        )
                    } else if (targetTabId != null) {
                        tabCoordinator.folders.createFolder(
                            tabId = targetTabId,
                            title = event.title,
                            color = event.color,
                        )
                    }
                }
            }

            is BrowserUiEvent.ToggleFolderCollapse -> {
                scope.launch {
                    tabCoordinator.folders.toggleFolderCollapse(event.folderId, fromDock = event.fromDock)
                }
            }

            is BrowserUiEvent.CloseFolder -> {
                scope.launch {
                    tabCoordinator.folders.closeFolder(event.folderId)
                }
            }

            is BrowserUiEvent.UngroupFolder -> {
                scope.launch {
                    tabCoordinator.folders.ungroupFolder(event.folderId)
                }
            }

            is BrowserUiEvent.AddTabToFolder -> {
                scope.launch {
                    tabCoordinator.folders.addTabToFolder(event.tabId, event.folderId)
                }
            }

            is BrowserUiEvent.RemoveTabFromFolder -> {
                scope.launch {
                    tabCoordinator.folders.removeTabFromFolder(event.tabId)
                }
            }

            is BrowserUiEvent.CreateTabInFolder -> {
                tabCoordinator.folders.createTabInFolder(event.folderId)
            }
        }
    }
}
