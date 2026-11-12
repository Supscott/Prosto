package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive

@Composable
internal fun LazyItemScope.TabStripDockItem(
    item: DockItem,
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    dragState: TabStripDragState,
    isDark: Boolean,
    centerPillBg: Color,
    centerPillBorder: BorderStroke?,
    circleBtnBg: Color,
    tabSpacingPx: Float,
    renderedActiveWidth: Dp,
    homeTitle: String,
    fallbackTitle: String,
    contextMenuTabIdState: MutableState<String?>,
    contextMenuFolderIdState: MutableState<String?>,
    folderToLeftAlignIdState: MutableState<String?>,
) {
    val context = LocalContext.current
    val isHome = uiState.isHome
    var contextMenuTabId by contextMenuTabIdState
    var contextMenuFolderId by contextMenuFolderIdState
    var folderToLeftAlignId by folderToLeftAlignIdState

    when (item) {
        is DockItem.FolderItem -> {
            // No fadeOutSpec on dock items: on a workspace switch every old key leaves at once and the
            // default fade-out left stuck ghost icons drawn over the new workspace's tabs.
            val folder = item.folder
            val folderColor = Color(folder.color)
            val isFolderElevated = dragState.draggingTabId == item.key || dragState.settlingTabId == item.key

            val folderPlacementModifier =
                if (isFolderElevated) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeOutSpec = null,
                        placementSpec =
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                    )
                }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(folderPlacementModifier)
                    .graphicsLayer {
                        alpha = if (isFolderElevated) 0f else 1f
                    }
                    .drawBehind {
                        if (!folder.isCollapsed && item.hasVisibleTabs) {
                            val lineY = size.height - 3.dp.toPx()
                            val startX = 4.dp.toPx()
                            val endX = size.width + tabSpacingPx
                            drawLine(
                                color = folderColor,
                                start = Offset(startX, lineY),
                                end = Offset(endX, lineY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                FolderDockIndicator(
                    folder = folder,
                    onClick = {
                        if (folder.isCollapsed) {
                            folderToLeftAlignId = folder.id
                        }
                        onEvent(BrowserUiEvent.ToggleFolderCollapse(folder.id, fromDock = true))
                    },
                    isBlinking = dragState.hoveredFolderId == folder.id,
                )

                FolderContextMenu(
                    folder = folder,
                    expanded = contextMenuFolderId == folder.id,
                    onDismissRequest = { contextMenuFolderId = null },
                    isDark = isDark,
                    onNewTabInFolder = {
                        onEvent(BrowserUiEvent.CreateTabInFolder(folder.id))
                    },
                    onEditFolder = {
                        onEvent(BrowserUiEvent.OpenEditFolderSheet(folder.id))
                    },
                    onUngroupFolder = {
                        onEvent(BrowserUiEvent.UngroupFolder(folder.id))
                    },
                    onCloseFolder = {
                        onEvent(BrowserUiEvent.CloseFolder(folder.id))
                    },
                )
            }
        }
        is DockItem.TabItem -> {
            val tab = item.tab
            val isActive = tab.id == uiState.activeTabId
            val tabIsHome = tab.isHomeTab || (isHome && isActive)
            val isElevated = dragState.draggingTabId == tab.id || dragState.settlingTabId == tab.id
            val folder = item.folder

            val itemPlacementModifier =
                if (isElevated) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeOutSpec = null,
                        placementSpec =
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                    )
                }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(itemPlacementModifier)
                    .drawBehind {
                        if (folder != null) {
                            val lineY = size.height - 3.dp.toPx()
                            val startX = 0f
                            val endX = if (item.isLastInFolder) size.width - 4.dp.toPx() else size.width + tabSpacingPx
                            drawLine(
                                color = Color(folder.color),
                                start = Offset(startX, lineY),
                                end = Offset(endX, lineY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) {
                    val displayInfo = resolveTabDisplayInfo(tab = tab, isActive = true, isHome = tabIsHome, uiState = uiState, homeTitle = homeTitle, fallbackTitle = fallbackTitle)

                    ActiveTabPill(
                        title = displayInfo.title,
                        domain = displayInfo.domain,
                        url = displayInfo.url,
                        faviconUrl = tab.faviconUrl,
                        isHome = tabIsHome,
                        backgroundColor = centerPillBg,
                        border = centerPillBorder,
                        isDark = isDark,
                        isLoading = uiState.isLoading,
                        loadingProgress = LocalLoadingProgress.current.value,
                        onOpenSearch = {
                            val targetPrefill = if (displayInfo.url.isNotBlank() && displayInfo.url != "about:blank") displayInfo.url else null
                            onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = targetPrefill, openInNewTab = false))
                        },
                        onClose = {
                            onEvent(BrowserUiEvent.CloseTab(tab.id))
                        },
                        onAddFavorite = { favTitle, favUrl ->
                            onEvent(BrowserUiEvent.AddFavorite(favTitle, favUrl))
                        },
                        onShare = { urlToShare ->
                            shareWebpageUrl(context, urlToShare)
                        },
                        onReload = {
                            onEvent(BrowserUiEvent.Reload)
                        },
                        modifier =
                            Modifier
                                .width(renderedActiveWidth)
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    alpha = if (isElevated) 0f else 1f
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                        isCompact = false,
                        isRemovePending = false,
                        removeProgress = 0f,
                        folderId = tab.folderId,
                        availableFolders = uiState.folders,
                        onCreateFolder = { onEvent(BrowserUiEvent.OpenCreateFolderSheet(tab.id)) },
                        onAddToFolder = { folderId -> onEvent(BrowserUiEvent.AddTabToFolder(tab.id, folderId)) },
                        onRemoveFromFolder = { onEvent(BrowserUiEvent.RemoveTabFromFolder(tab.id)) },
                        availableWorkspaces = uiState.workspaces.filter { it.id != tab.workspaceId },
                        onMoveToWorkspace = { wsId -> onEvent(BrowserUiEvent.MoveTabToWorkspace(tab.id, wsId)) },
                    )
                } else {
                    val isTabLoaded = uiState.isTabLoaded(tab.id)

                    InactiveTabButton(
                        tab = tab,
                        isDark = isDark,
                        isLoaded = isTabLoaded,
                        grayUnloadedTabs = uiState.settings.isGrayUnloadedTabsEnabled,
                        backgroundColor = circleBtnBg,
                        border = null,
                        isRemovePending = false,
                        removeProgress = 0f,
                        onClick = {
                            onEvent(BrowserUiEvent.SelectTab(tab.id))
                        },
                        onClose = {
                            onEvent(BrowserUiEvent.CloseTab(tab.id))
                        },
                        onAddFavorite = { favTitle, favUrl ->
                            onEvent(BrowserUiEvent.AddFavorite(favTitle, favUrl))
                        },
                        modifier =
                            Modifier
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    alpha = if (isElevated) 0f else 1f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                        folderId = tab.folderId,
                        availableFolders = uiState.folders,
                        onCreateFolder = { onEvent(BrowserUiEvent.OpenCreateFolderSheet(tab.id)) },
                        onAddToFolder = { folderId -> onEvent(BrowserUiEvent.AddTabToFolder(tab.id, folderId)) },
                        onRemoveFromFolder = { onEvent(BrowserUiEvent.RemoveTabFromFolder(tab.id)) },
                        availableWorkspaces = uiState.workspaces.filter { it.id != tab.workspaceId },
                        onMoveToWorkspace = { wsId -> onEvent(BrowserUiEvent.MoveTabToWorkspace(tab.id, wsId)) },
                    )
                }
            }
        }
    }
}
