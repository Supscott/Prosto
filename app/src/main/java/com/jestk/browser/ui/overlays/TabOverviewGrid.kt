package com.jestk.browser.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.R
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import kotlin.math.roundToInt

internal sealed interface OverviewGridItem {
    val key: String

    data class TabCard(val tab: Tab) : OverviewGridItem {
        override val key: String get() = "tab_${tab.id}"
    }

    data class FolderCard(val folder: TabFolder, val tabs: List<Tab>) : OverviewGridItem {
        override val key: String get() = "folder_${folder.id}"
    }

    data class FolderHeader(val folder: TabFolder, val tabs: List<Tab>) : OverviewGridItem {
        override val key: String get() = "folder_header_${folder.id}"
    }

    data class FolderTabCard(
        val folder: TabFolder,
        val tab: Tab,
        val memberIndex: Int,
        val totalMembers: Int,
    ) : OverviewGridItem {
        override val key: String get() = "tab_${tab.id}"
    }

    data class FolderSpacer(val folderId: String, val index: Int) : OverviewGridItem {
        override val key: String get() = "folder_spacer_${folderId}_$index"
    }
}

internal fun computeOverviewItems(
    tabs: List<Tab>,
    folders: List<TabFolder>,
    searchQuery: String,
    expandedFolderIds: Set<String> = emptySet(),
): List<OverviewGridItem> {
    val folderTabsMap = tabs.filter { it.folderId != null }.groupBy { it.folderId!! }
    fun matchesQuery(text: String) = text.contains(searchQuery, ignoreCase = true)
    fun tabMatches(tab: Tab) = matchesQuery(tab.title) || matchesQuery(tab.url)
    fun folderMatches(folder: TabFolder, memberTabs: List<Tab>): Boolean {
        return matchesQuery(folder.title) || memberTabs.any { tabMatches(it) }
    }

    val items = mutableListOf<OverviewGridItem>()
    val processedFolderIds = mutableSetOf<String>()

    fun addFolderItems(folder: TabFolder, memberTabs: List<Tab>) {
        val isExpanded = folder.id in expandedFolderIds || searchQuery.isNotBlank()
        if (isExpanded) {
            items.add(OverviewGridItem.FolderHeader(folder, memberTabs))
            memberTabs.forEachIndexed { index, tab ->
                items.add(OverviewGridItem.FolderTabCard(folder, tab, index, memberTabs.size))
            }
            val remainder = memberTabs.size % 3
            val padding = if (remainder == 0) 0 else 3 - remainder
            for (i in 0 until padding) {
                items.add(OverviewGridItem.FolderSpacer(folder.id, i))
            }
        } else {
            items.add(OverviewGridItem.FolderCard(folder, memberTabs))
        }
    }

    for (tab in tabs) {
        val folderId = tab.folderId
        if (folderId == null) {
            if (searchQuery.isBlank() || tabMatches(tab)) {
                items.add(OverviewGridItem.TabCard(tab))
            }
        } else {
            if (folderId !in processedFolderIds) {
                processedFolderIds.add(folderId)
                val folder = folders.find { it.id == folderId }
                val memberTabs = folderTabsMap[folderId] ?: emptyList()
                if (folder != null) {
                    if (searchQuery.isBlank()) {
                        addFolderItems(folder, memberTabs)
                    } else if (folderMatches(folder, memberTabs)) {
                        val visibleTabs = if (matchesQuery(folder.title)) {
                            memberTabs
                        } else {
                            memberTabs.filter { tabMatches(it) }
                        }
                        addFolderItems(folder, visibleTabs)
                    }
                }
            }
        }
    }

    for (folder in folders) {
        if (folder.id !in processedFolderIds) {
            val memberTabs = folderTabsMap[folder.id] ?: emptyList()
            if (searchQuery.isBlank() || folderMatches(folder, memberTabs)) {
                val visibleTabs = if (searchQuery.isBlank() || matchesQuery(folder.title)) {
                    memberTabs
                } else {
                    memberTabs.filter { tabMatches(it) }
                }
                addFolderItems(folder, visibleTabs)
            }
        }
    }

    return items
}

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

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .prostoSkyBackground(isDark = isDark)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Navigation Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.action_back),
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.tabs_overview_title),
                    style = LocalProstoTypography.current.headlineSm,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )

                // Clean Badge
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 2. Sub-Header: Tab Count & Controls
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val totalMatchingTabs =
                    if (tabSearchQuery.isNotBlank()) {
                        overviewItems.sumOf { item ->
                            when (item) {
                                is OverviewGridItem.TabCard -> 1
                                is OverviewGridItem.FolderCard -> item.tabs.size
                                is OverviewGridItem.FolderHeader -> 0
                                is OverviewGridItem.FolderTabCard -> 1
                                is OverviewGridItem.FolderSpacer -> 0
                            }
                        }
                    } else {
                        uiState.tabs.size
                    }
                val totalTabs = uiState.tabs.size
                val tabCountText =
                    if (tabSearchQuery.isNotBlank()) {
                        pluralStringResource(R.plurals.tabs_overview_count_filtered, totalTabs, totalMatchingTabs, totalTabs)
                    } else {
                        pluralStringResource(R.plurals.workspace_tabs_count, totalTabs, totalTabs)
                    }
                Text(
                    text = tabCountText,
                    style = LocalProstoTypography.current.headlineMd,
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Search in tabs button
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) {
                                tabSearchQuery = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.tabs_overview_search),
                            tint = if (isSearchVisible) ProstoBrandPrimary else ProstoMutedSlate,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // Solid "+" Add Tab Button
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ProstoBrandPrimary)
                                .clickable {
                                    onEvent(BrowserUiEvent.OpenNewTab(""))
                                    onEvent(BrowserUiEvent.DismissOverlay)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_new_tab),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Expandable in-place tab search filter bar
            AnimatedVisibility(visible = isSearchVisible) {
                Surface(
                    shape = ProstoShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.cardBorder(isDark),
                    shadowElevation = 2.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .height(44.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ProstoMutedSlate,
                            modifier = Modifier.size(18.dp),
                        )
                        BasicTextField(
                            value = tabSearchQuery,
                            onValueChange = { tabSearchQuery = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusRequester(searchFocusRequester),
                            singleLine = true,
                            textStyle =
                                LocalProstoTypography.current.bodyMd.copy(
                                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                                ),
                            cursorBrush = SolidColor(ProstoBrandPrimary),
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSearch = { keyboardController?.hide() },
                                ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (tabSearchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.tabs_overview_filter_hint),
                                            style = LocalProstoTypography.current.bodyMd,
                                            color = ProstoMutedSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (tabSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    tabSearchQuery = ""
                                    searchFocusRequester.requestFocus()
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_action),
                                    tint = ProstoMutedSlate,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 3. 3-Column Vertical Grid with 9:16 preview cards and drag-and-drop grouping
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
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
                                        homePreview = if (item.tab.isHomeTab) {
                                            { HomePreview(uiState = uiState, workspaceId = item.tab.workspaceId) }
                                        } else {
                                            null
                                        },
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
                                        homePreview = if (item.tab.isHomeTab) {
                                            { HomePreview(uiState = uiState, workspaceId = item.tab.workspaceId) }
                                        } else {
                                            null
                                        },
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

        // 4. Floating Bottom Action Bar (Done pill + Close All)
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close All Button
            TextButton(
                onClick = { onEvent(BrowserUiEvent.CloseAllTabs) },
            ) {
                Text(
                    text = stringResource(R.string.tabs_overview_close_all),
                    color = MaterialTheme.colorScheme.error,
                    style = LocalProstoTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // Floating Centered "Done" Pill Button
            Surface(
                shape = ProstoShapes.Pill,
                color = if (isDark) DarkSurface else Color.White,
                border = ProstoBorders.floatingBorder(isDark),
                shadowElevation = 4.dp,
                modifier =
                    Modifier
                        .height(46.dp)
                        .clickable { onEvent(BrowserUiEvent.DismissOverlay) },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        color = ProstoBrandPrimary,
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }
        }

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

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridLightPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
        TabOverviewGrid(
            uiState =
                com.jestk.browser.ui.viewmodel.BrowserUiState(
                    tabs =
                        listOf(
                            com.jestk.browser.domain.model.Tab(
                                id = "1",
                                url = "https://kotlinlang.org",
                                title = "Kotlin Programming",
                                isActive = true,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "2",
                                url = "https://developer.android.com",
                                title = "Android Developers",
                                isActive = false,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "3",
                                url = "https://github.com",
                                title = "GitHub",
                                isActive = false,
                                folderId = "f1",
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "4",
                                url = "https://duckduckgo.com",
                                title = "DuckDuckGo",
                                isActive = false,
                                folderId = "f1",
                            ),
                        ),
                    folders =
                        listOf(
                            com.jestk.browser.domain.model.TabFolder(
                                id = "f1",
                                title = "Dev",
                                color = 0xFF8B5CF6.toInt(),
                            ),
                        ),
                    activeTabId = "1",
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridDarkPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.DARK) {
        TabOverviewGrid(
            uiState =
                com.jestk.browser.ui.viewmodel.BrowserUiState(
                    tabs =
                        listOf(
                            com.jestk.browser.domain.model.Tab(
                                id = "1",
                                url = "https://kotlinlang.org",
                                title = "Kotlin Programming",
                                isActive = true,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "2",
                                url = "https://developer.android.com",
                                title = "Android Developers",
                                isActive = false,
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "3",
                                url = "https://github.com",
                                title = "GitHub",
                                isActive = false,
                                folderId = "f1",
                            ),
                            com.jestk.browser.domain.model.Tab(
                                id = "4",
                                url = "https://duckduckgo.com",
                                title = "DuckDuckGo",
                                isActive = false,
                                folderId = "f1",
                            ),
                        ),
                    folders =
                        listOf(
                            com.jestk.browser.domain.model.TabFolder(
                                id = "f1",
                                title = "Dev",
                                color = 0xFF8B5CF6.toInt(),
                            ),
                        ),
                    activeTabId = "1",
                ),
            onEvent = {},
        )
    }
}
