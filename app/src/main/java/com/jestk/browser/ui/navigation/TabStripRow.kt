package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.theme.DarkDockSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Horizontal scrollable strip of tabs (`LazyRow`).
 * Adheres to Prosto Canvas design system:
 * - 50% width active tab capsule ([ActiveTabPill]) with solid surface and light elevation shadow.
 * - Inactive tabs: transparent background, [ProstoMutedSlate] icons and text, label-md typography.
 * - Long-press contextual menus ([TabContextMenu]) with solid card surface and hairline dividers.
 * - Persistent '+' new tab button pinned on the right edge ([AddTabDockButton]).
 * - Interactive tab drag reordering.
 * - Left/right edge smooth gradient fade scrims.
 */
@Composable
fun TabStripRow(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current,
    centerPillBg: Color = if (isDark) DarkDockSurface else Color.White,
    centerPillBorder: BorderStroke? = null,
    circleBtnBg: Color = Color.Transparent,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val density = LocalDensity.current
    val vibration = LocalVibrationFeedback.current

    val isHome = uiState.isHome
    val activeTab = uiState.activeTab

    val homeTitle = stringResource(R.string.tab_home_title)
    val fallbackTitle = stringResource(R.string.app_name)
    val isHomeTab = isHome || activeTab == null || activeTab.isHomeTab
    val displayTitle =
        when {
            isHomeTab -> homeTitle
            activeTab?.title?.isNotEmpty() == true -> activeTab.title
            uiState.pageTitle.isNotEmpty() -> uiState.pageTitle
            else -> fallbackTitle
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val totalChipWidth = maxWidth

        val resolvedActiveTab = activeTab ?: Tab(
            id = uiState.activeTabId ?: "default_tab",
            url = uiState.currentUrl,
            title = displayTitle,
            isActive = true,
        )
        val activeTabIsHome = isHome || resolvedActiveTab.isHomeTab
        val activeDisplayInfo = resolveTabDisplayInfo(tab = resolvedActiveTab, isActive = true, isHome = activeTabIsHome, uiState = uiState, homeTitle = homeTitle, fallbackTitle = fallbackTitle)

        val measuredTextWidthDp = rememberActiveTabTextWidth(activeDisplayInfo, density)

        val minActiveTabWidth = ActiveTabPillDefaults.MinWidth
        val maxActiveTabWidth = totalChipWidth * 0.45f
        val contentOverheadDp = ActiveTabPillDefaults.ContentOverhead
        val desiredActiveTabWidth = contentOverheadDp + measuredTextWidthDp
        val activeTabWidth = desiredActiveTabWidth
            .coerceAtLeast(minOf(minActiveTabWidth, maxActiveTabWidth))
            .coerceAtMost(maxActiveTabWidth)

        val displayTabs =
            remember(uiState.tabs, activeTab) {
                val tabs =
                    if (uiState.tabs.isNotEmpty()) {
                        uiState.tabs
                    } else {
                        listOf(
                            activeTab ?: Tab(
                                id = "default_tab",
                                url = uiState.currentUrl,
                                title = displayTitle,
                                isActive = true,
                            ),
                        )
                    }
                tabs.distinctBy { it.id }
            }

        val coroutineScope = rememberCoroutineScope()
        val dragState =
            androidx.compose.runtime.key(uiState.activeWorkspaceId) {
                rememberTabStripDragState(
                    initialTabs = displayTabs,
                    onReorder = { reordered ->
                        for (tab in reordered) {
                            val original = displayTabs.find { it.id == tab.id }
                            if (original != null && original.folderId != tab.folderId) {
                                if (tab.folderId != null) {
                                    onEvent(BrowserUiEvent.AddTabToFolder(tab.id, tab.folderId))
                                } else {
                                    onEvent(BrowserUiEvent.RemoveTabFromFolder(tab.id))
                                }
                            }
                        }
                        onEvent(BrowserUiEvent.ReorderTabs(reordered))
                    },
                    onRemoveTab = { tabId -> onEvent(BrowserUiEvent.CloseTab(tabId)) },
                    foldersProvider = { uiState.folders },
                    onToggleFolderCollapse = { folderId ->
                        onEvent(BrowserUiEvent.ToggleFolderCollapse(folderId, fromDock = true))
                    },
                    onHoverVibration = { vibration.tick() },
                    onSpringLoadVibration = { vibration.confirm() },
                    coroutineScope = coroutineScope,
                )
            }

        LaunchedEffect(displayTabs) {
            dragState.updateTabs(displayTabs)
        }

        val contextMenuTabIdState = remember(uiState.activeWorkspaceId) { mutableStateOf<String?>(null) }
        val contextMenuFolderIdState = remember(uiState.activeWorkspaceId) { mutableStateOf<String?>(null) }
        var contextMenuTabId by contextMenuTabIdState
        var contextMenuFolderId by contextMenuFolderIdState

        val dockItems = remember(dragState.currentTabs, uiState.folders) {
            computeDockItems(dragState.currentTabs, uiState.folders)
        }
        val currentDockItemsState = rememberUpdatedState(dockItems)

        var previousCollapsedFolderIds by remember {
            mutableStateOf(uiState.folders.filter { it.isCollapsed }.map { it.id }.toSet())
        }
        val folderToLeftAlignIdState = remember { mutableStateOf<String?>(null) }
        var folderToLeftAlignId by folderToLeftAlignIdState

        LaunchedEffect(dockItems) {
            val currentFolders = uiState.folders
            val newlyExpandedFolderId = folderToLeftAlignId ?: currentFolders.firstOrNull { folder ->
                !folder.isCollapsed && folder.id in previousCollapsedFolderIds
            }?.id

            previousCollapsedFolderIds = currentFolders.filter { it.isCollapsed }.map { it.id }.toSet()
            folderToLeftAlignId = null

            if (newlyExpandedFolderId != null) {
                val idx = dockItems.indexOfFirst { it is DockItem.FolderItem && it.folder.id == newlyExpandedFolderId }
                if (idx >= 0) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(idx, 0)
                    }
                }
            }
        }

        val activeIndex =
            remember(dragState.currentTabs, uiState.activeTabId, uiState.activeTabIndex) {
                val idx = dragState.currentTabs.indexOfFirst { it.id == uiState.activeTabId }
                if (idx >= 0) {
                    idx
                } else if (dragState.currentTabs.isNotEmpty()) {
                    uiState.activeTabIndex.coerceIn(0, dragState.currentTabs.lastIndex)
                } else {
                    0
                }
            }

        val activeTabWidthPx = with(density) { activeTabWidth.toPx() }
        val totalChipWidthPx = with(density) { totalChipWidth.toPx() }
        val inactiveTabWidthPx = with(density) { 38.dp.toPx() }
        val tabSpacingPx = with(density) { 3.dp.toPx() }
        val hysteresisPx = with(density) { 4.dp.toPx() }

        val layoutMetrics =
            remember(activeTabWidthPx, inactiveTabWidthPx, tabSpacingPx, uiState.activeTabId, dragState.draggingTabId, hysteresisPx, uiState.folders) {
                TabStripLayoutMetrics(
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = tabSpacingPx,
                    activeTabId = uiState.activeTabId,
                    draggingTabId = dragState.draggingTabId,
                    hysteresisPx = hysteresisPx,
                    itemWidthProvider = { key ->
                        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.size?.toFloat()
                    },
                    folderProvider = { folderId -> uiState.folders.find { it.id == folderId } },
                )
            }

        TabStripActiveTabCentering(
            uiState = uiState,
            lazyListState = lazyListState,
            currentDockItemsState = currentDockItemsState,
            dragState = dragState,
            displayTabs = displayTabs,
            totalChipWidthPx = totalChipWidthPx,
            activeTabWidthPx = activeTabWidthPx,
        )


        val edgeTriggerThresholdPx = with(density) { 24.dp.toPx() }
        val rightPinnedOffsetPx = with(density) { 46.dp.toPx() }
        val maxScrollStepPx = with(density) { 8.dp.toPx() }
        val edgeSlackPx = with(density) { 8.dp.toPx() }
        val removeThresholdPx = with(density) { 50.dp.toPx() }

        val dragReorderModifier =
            rememberTabStripDragReorderModifier(
                lazyListState = lazyListState,
                dragState = dragState,
                layoutMetrics = layoutMetrics,
                displayTabs = displayTabs,
                activeTabId = uiState.activeTabId,
                tabSpacingPx = tabSpacingPx,
                totalChipWidthPx = totalChipWidthPx,
                rightPinnedOffsetPx = rightPinnedOffsetPx,
                edgeSlackPx = edgeSlackPx,
                removeThresholdPx = removeThresholdPx,
                onLongPress = { key ->
                    if (key.startsWith("folder_")) {
                        contextMenuFolderId = key.removePrefix("folder_")
                    } else {
                        contextMenuTabId = key
                    }
                },
            )

        // Fail-safe drag cancellation on unmount or tab disposal during drag
        DisposableEffect(dragState.draggingTabId) {
            val currentDraggingTabId = dragState.draggingTabId
            onDispose {
                if (currentDraggingTabId != null && dragState.isDragging && dragState.draggingTabId == currentDraggingTabId) {
                    dragState.onDragCancel(isReleasedActive = (currentDraggingTabId == uiState.activeTabId))
                }
            }
        }

        TabStripEdgeAutoScroll(
            dragState = dragState,
            lazyListState = lazyListState,
            layoutMetrics = layoutMetrics,
            totalChipWidthPx = totalChipWidthPx,
            rightPinnedOffsetPx = rightPinnedOffsetPx,
            edgeTriggerThresholdPx = edgeTriggerThresholdPx,
            maxScrollStepPx = maxScrollStepPx,
            edgeSlackPx = edgeSlackPx,
        )

        val leftFadeWidthPx = with(density) { 32.dp.toPx() }
        val rightFadeWidthPx = with(density) { 32.dp.toPx() }

        val canScrollBackward = lazyListState.canScrollBackward
        val canScrollForward = lazyListState.canScrollForward
        val isFadeActive = canScrollBackward || canScrollForward

        val isDraggingActiveTab = dragState.isDragging && dragState.draggingTabId == uiState.activeTabId
        val renderedActiveWidth by animateDpAsState(
            targetValue = if (isDraggingActiveTab) 38.dp else activeTabWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "ActiveTabDragWidth",
        )

        LazyRow(
            state = lazyListState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(ProstoShapes.Dock)
                    .graphicsLayer {
                        compositingStrategy =
                            if (isFadeActive) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                    }
                    .tabStripEdgeFade(
                        isFadeActive = isFadeActive,
                        canScrollBackward = canScrollBackward,
                        canScrollForward = canScrollForward,
                        leftFadeWidthPx = leftFadeWidthPx,
                        rightFadeWidthPx = rightFadeWidthPx,
                        rightPinnedOffsetPx = rightPinnedOffsetPx,
                    )
                    .then(dragReorderModifier),
            userScrollEnabled = !dragState.isDragging && !dragState.isSettling,
            contentPadding = PaddingValues(start = 5.dp, end = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(
                items = dockItems,
                key = { _, item -> item.key },
            ) { _, item ->
                TabStripDockItem(
                    item = item,
                    uiState = uiState,
                    onEvent = onEvent,
                    dragState = dragState,
                    isDark = isDark,
                    centerPillBg = centerPillBg,
                    centerPillBorder = centerPillBorder,
                    circleBtnBg = circleBtnBg,
                    tabSpacingPx = tabSpacingPx,
                    renderedActiveWidth = renderedActiveWidth,
                    homeTitle = homeTitle,
                    fallbackTitle = fallbackTitle,
                    contextMenuTabIdState = contextMenuTabIdState,
                    contextMenuFolderIdState = contextMenuFolderIdState,
                    folderToLeftAlignIdState = folderToLeftAlignIdState,
                )
            }
        }

        // Pinned New Tab Button (absolute right-0 over tab strip)
        AddTabDockButton(
            isDark = isDark,
            backgroundColor = Color.Transparent,
            border = null,
            onClick = {
                onEvent(BrowserUiEvent.OpenNewTab(""))
            },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        )

        TabStripDragOverlays(
            uiState = uiState,
            dragState = dragState,
            lazyListState = lazyListState,
            isDark = isDark,
            activeTabWidth = activeTabWidth,
            centerPillBg = centerPillBg,
            centerPillBorder = centerPillBorder,
            circleBtnBg = circleBtnBg,
            removeThresholdPx = removeThresholdPx,
            homeTitle = homeTitle,
            fallbackTitle = fallbackTitle,
        )
    }
}
