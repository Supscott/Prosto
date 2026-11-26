package com.jestk.browser.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import kotlin.math.roundToInt

/**
 * Tab Overview Grid - Prosto Canvas design system.
 * Features:
 * - Top navigation bar with back button, "Tabs Overview", and accent orb icon
 * - Sub-header with tab count, search in tabs, and gradient "+" add button
 * - 3-column grid with 9:16 preview cards and collapsed folder cards
 * - Active tab highlighted with multi-color gradient border (#7BA7D7 -> #F6B99D -> #D391A5)
 * - Direct-manipulation drag-and-drop tab grouping onto tabs and collapsed folders
 * - Floating bottom frosted "Done" pill button
 */
@Composable
fun TabOverviewGrid(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    homePreviews: SharedHomePreviews = remember { SharedHomePreviews() },
) {
    val isDark = LocalIsDarkTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSearchVisible by remember { mutableStateOf(false) }
    var tabSearchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    val currentUiState by rememberUpdatedState(uiState)

    BackHandler(enabled = isSearchVisible) {
        isSearchVisible = false
        tabSearchQuery = ""
        keyboardController?.hide()
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var expandedFolderIds by remember { mutableStateOf(setOf<String>()) }
    val gridState = rememberLazyGridState()

    var contextMenuTabId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val reorderState = remember { TabOverviewReorderState(coroutineScope) }
    val reorderModifier = rememberTabOverviewReorderModifier(
        gridState = gridState,
        tabs = uiState.tabs,
        folders = uiState.folders,
        onEvent = onEvent,
        reorderState = reorderState,
        onTabLongPress = { tabId -> contextMenuTabId = tabId },
    )

    val activeTabs = if (reorderState.isDragging && reorderState.workingTabs.isNotEmpty()) {
        reorderState.workingTabs
    } else {
        uiState.tabs
    }

    val activeFolders = if (reorderState.isDragging && reorderState.workingFolders.isNotEmpty()) {
        reorderState.workingFolders
    } else {
        uiState.folders
    }

    val overviewItems =
        remember(activeTabs, activeFolders, tabSearchQuery, expandedFolderIds) {
            computeOverviewItems(
                tabs = activeTabs,
                folders = activeFolders,
                searchQuery = tabSearchQuery,
                expandedFolderIds = expandedFolderIds,
            )
        }

    val vibration = LocalVibrationFeedback.current
    val density = LocalDensity.current
    var gridWidthPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .prostoSkyBackground(isDark = isDark)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabOverviewTopBar(isDark, onEvent)

            TabOverviewSubHeader(
                tabSearchQuery = tabSearchQuery,
                overviewItems = overviewItems,
                totalTabs = uiState.tabs.size,
                isSearchVisible = isSearchVisible,
                onToggleSearch = {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        tabSearchQuery = ""
                        keyboardController?.hide()
                    }
                },
                isDark = isDark,
                onEvent = onEvent,
            )

            TabOverviewSearchBar(
                visible = isSearchVisible,
                query = tabSearchQuery,
                onQueryChange = { tabSearchQuery = it },
                focusRequester = searchFocusRequester,
                onSearchAction = { keyboardController?.hide() },
                isDark = isDark,
            )

            // 3. 3-Column Vertical Grid with 9:16 preview cards and drag-and-drop grouping
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { gridWidthPx = it.width },
            ) {
                val homeWorkspaceIds = remember(activeTabs) { activeTabs.filter { it.isHomeTab }.map { it.workspaceId }.toSet() }
                if (gridWidthPx > 0 && homeWorkspaceIds.isNotEmpty()) {
                    SharedHomePreviewRecorders(uiState, homeWorkspaceIds, homePreviewSize(gridWidthPx, density), homePreviews)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = with(density) { bottomBarHeightPx.toDp() } + TAB_OVERVIEW_BOTTOM_BAR_GAP,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("overview_grid")
                            .drawBehind {
                                val activeFolderIds = if (tabSearchQuery.isNotBlank()) {
                                    overviewItems.filterIsInstance<OverviewGridItem.FolderHeader>().map { it.folder.id }.toSet()
                                } else {
                                    expandedFolderIds
                                }
                                for (folderId in activeFolderIds) {
                                    val folder = uiState.folders.find { it.id == folderId } ?: continue
                                    val memberKeys = overviewItems.filter {
                                        (it is OverviewGridItem.FolderTabCard && it.folder.id == folderId) ||
                                            (it is OverviewGridItem.FolderSpacer && it.folderId == folderId)
                                    }.map { it.key }.toSet()
                                    val bounds = calculateFolderUnderlayBounds(
                                        visibleItems = gridState.layoutInfo.visibleItemsInfo,
                                        folderTabKeys = memberKeys,
                                        gridWidth = size.width,
                                        horizontalMarginPx = 10.dp.toPx(),
                                        verticalPaddingPx = 6.dp.toPx(),
                                    ) ?: continue

                                    val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                    val folderColor = Color(folder.color)
                                    drawRoundRect(
                                        color = folderColor.copy(alpha = 0.15f),
                                        topLeft = Offset(bounds.minX, bounds.minY),
                                        size = Size(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY),
                                        cornerRadius = cornerRadius,
                                    )
                                    drawRoundRect(
                                        color = folderColor.copy(alpha = 0.35f),
                                        topLeft = Offset(bounds.minX, bounds.minY),
                                        size = Size(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY),
                                        cornerRadius = cornerRadius,
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }
                            }
                            .then(reorderModifier),
                ) {
                    items(
                        items = overviewItems,
                        key = { it.key },
                        span = { item ->
                            when (item) {
                                is OverviewGridItem.FolderHeader -> GridItemSpan(3)
                                else -> GridItemSpan(1)
                            }
                        },
                    ) { item ->
                        val isDragged = item.key == reorderState.draggedKey
                        val hoverScale by animateFloatAsState(
                            targetValue = if (reorderState.hoverGroupingKey == item.key) 0.96f else 1f,
                            animationSpec = tween(120),
                            label = "overviewHoverScale",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        scaleX = hoverScale
                                        scaleY = hoverScale
                                    }
                                    .alpha(if (isDragged) 0f else 1f),
                        ) {
                            when (item) {
                                is OverviewGridItem.TabCard -> {
                                    GridTabCard(
                                        tab = item.tab,
                                        isActive = item.tab.id == uiState.activeTabId,
                                        isDark = isDark,
                                        isHovered = reorderState.hoverGroupingKey == item.key,
                                        onSelect = {
                                            onEvent(BrowserUiEvent.SelectTab(item.tab.id))
                                            onEvent(BrowserUiEvent.DismissOverlay)
                                        },
                                        onClose = {
                                            onEvent(BrowserUiEvent.CloseTab(item.tab.id))
                                        },
                                        homePreview = { SharedHomePreviewImage(homePreviews, item.tab.workspaceId) },
                                    )
                                }
                                is OverviewGridItem.FolderCard -> {
                                    FolderOverviewGridCard(
                                        folder = item.folder,
                                        tabs = item.tabs,
                                        isDark = isDark,
                                        isHovered = reorderState.hoverGroupingKey == item.key,
                                        onClick = {
                                            expandedFolderIds =
                                                if (item.folder.id in expandedFolderIds) {
                                                    expandedFolderIds - item.folder.id
                                                } else {
                                                    expandedFolderIds + item.folder.id
                                                }
                                        },
                                    )
                                }
                                is OverviewGridItem.FolderHeader -> {
                                    FolderOverviewGridHeader(
                                        folder = item.folder,
                                        tabCount = item.tabs.size,
                                        onClick = {
                                            expandedFolderIds = expandedFolderIds - item.folder.id
                                        },
                                        onOpenEditSheet = {
                                            onEvent(BrowserUiEvent.OpenEditFolderSheet(item.folder.id))
                                        },
                                    )
                                }
                                is OverviewGridItem.FolderTabCard -> {
                                    GridTabCard(
                                        tab = item.tab,
                                        isActive = item.tab.id == uiState.activeTabId,
                                        isDark = isDark,
                                        isHovered = reorderState.hoverGroupingKey == item.key,
                                        onSelect = {
                                            onEvent(BrowserUiEvent.SelectTab(item.tab.id))
                                            onEvent(BrowserUiEvent.DismissOverlay)
                                        },
                                        onClose = {
                                            onEvent(BrowserUiEvent.CloseTab(item.tab.id))
                                        },
                                        homePreview = { SharedHomePreviewImage(homePreviews, item.tab.workspaceId) },
                                    )
                                }
                                is OverviewGridItem.FolderSpacer -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag(item.key),
                                    )
                                }
                            }
                            GroupingHintScrim(
                                visible = reorderState.hoverGroupingKey == item.key,
                                joinsFolder =
                                    item is OverviewGridItem.FolderCard || item is OverviewGridItem.FolderTabCard,
                                isDark = isDark,
                            )
                        }
                    }
                }

                // Floating Sticky Header Overlay
                val stickyHeaderState by remember(activeFolders, activeTabs, expandedFolderIds) {
                    derivedStateOf {
                        val folderTabsMap = activeTabs.filter { it.folderId != null }.groupBy { it.folderId!! }
                        val headerHeightPx = with(density) { 44.dp.toPx() }
                        val expandedFoldersInOrder =
                            overviewItems
                                .filterIsInstance<OverviewGridItem.FolderHeader>()
                                .map { it.folder }
                        computeStickyHeaderState(
                            expandedFolders = expandedFoldersInOrder,
                            folderMemberTabsMap = folderTabsMap,
                            visibleItems = gridState.layoutInfo.visibleItemsInfo,
                            headerHeightPx = headerHeightPx,
                        )
                    }
                }

                if (stickyHeaderState.visible && stickyHeaderState.folder != null) {
                    val folder = stickyHeaderState.folder!!
                    FolderOverviewGridHeader(
                        folder = folder,
                        tabCount = stickyHeaderState.tabCount,
                        isSticky = true,
                        onClick = {
                            expandedFolderIds = expandedFolderIds - folder.id
                        },
                        onOpenEditSheet = {
                            onEvent(BrowserUiEvent.OpenEditFolderSheet(folder.id))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .offset { IntOffset(0, stickyHeaderState.translationY.roundToInt()) },
                    )
                }

                // Floating ghost of the picked-up card; the real item is hidden while dragging.
                TabOverviewDragGhost(
                    reorderState = reorderState,
                    tabs = activeTabs,
                    folders = activeFolders,
                    isDark = isDark,
                )
            }
        }

        // The bar and the grid share this Box, which already applies the navigation-bar inset, so
        // the measured bar height is all the grid needs to clear it at scroll end.
        TabOverviewBottomBar(
            isDark,
            onEvent,
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarHeightPx = it.height },
        )

        val contextTab = uiState.tabs.find { it.id == contextMenuTabId }
        if (contextTab != null) {
            val targetWorkspaces = remember(uiState.workspaces, contextTab.workspaceId) {
                uiState.workspaces.filter { it.id != contextTab.workspaceId }
            }
            TabGridContextMenuBottomSheet(
                tab = contextTab,
                isDark = isDark,
                availableWorkspaces = targetWorkspaces,
                onMoveToWorkspace = { targetWsId ->
                    onEvent(BrowserUiEvent.MoveTabToWorkspace(contextTab.id, targetWsId))
                    contextMenuTabId = null
                },
                onCloseTab = {
                    onEvent(BrowserUiEvent.CloseTab(contextTab.id))
                    contextMenuTabId = null
                },
                onDismiss = {
                    contextMenuTabId = null
                },
            )
        }
    }
}
