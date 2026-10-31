package com.jestk.browser.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.jestk.browser.R
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.components.SiteFavicon
import com.jestk.browser.ui.theme.DarkDockSurface
import com.jestk.browser.ui.theme.DarkError
import com.jestk.browser.ui.theme.DarkErrorContainer
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoError
import com.jestk.browser.ui.theme.ProstoErrorContainer
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.URI

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
    dockBg: Color = if (isDark) DarkDockSurface.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f),
    centerPillBg: Color = if (isDark) DarkDockSurface else Color.White,
    centerPillBorder: BorderStroke? = null,
    circleBtnBg: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val vibration = LocalVibrationFeedback.current

    val isHome = uiState.isHome
    val activeTab = uiState.activeTab

    val isHomeTab = isHome || activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank" || activeTab?.title == "Home"
    val displayTitle =
        if (isHomeTab) {
            "Home"
        } else {
            when {
                activeTab?.title?.isNotEmpty() == true && activeTab.title != "Home" && activeTab.title != "New Tab" -> activeTab.title
                uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                else -> "Prosto Browser"
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val totalChipWidth = maxWidth
        val activeTabWidth = totalChipWidth * 0.45f
        val lazyListState = rememberLazyListState()

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
            rememberTabStripDragState(
                initialTabs = displayTabs,
                onReorder = { reordered -> onEvent(BrowserUiEvent.ReorderTabs(reordered)) },
                onRemoveTab = { tabId -> onEvent(BrowserUiEvent.CloseTab(tabId)) },
                coroutineScope = coroutineScope,
            )

        LaunchedEffect(displayTabs) {
            dragState.updateTabs(displayTabs)
        }

        var contextMenuTabId by remember { mutableStateOf<String?>(null) }

        val activeIndex =
            remember(dragState.currentTabs, uiState.activeTabId, uiState.activeTabIndex) {
                val idx = dragState.currentTabs.indexOfFirst { it.id == uiState.activeTabId }
                if (idx >=
                    0
                ) {
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
        val tabSpacingPx = with(density) { 5.dp.toPx() }
        val hysteresisPx = with(density) { 4.dp.toPx() }

        val layoutMetrics =
            remember(activeTabWidthPx, inactiveTabWidthPx, tabSpacingPx, uiState.activeTabId, dragState.draggingTabId, hysteresisPx) {
                TabStripLayoutMetrics(
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = tabSpacingPx,
                    activeTabId = uiState.activeTabId,
                    draggingTabId = dragState.draggingTabId,
                    hysteresisPx = hysteresisPx,
                )
            }

        var lastCenteredActiveTabId by remember { mutableStateOf<String?>(null) }
        var lastActiveOverlay by remember { mutableStateOf<OverlayType?>(null) }

        LaunchedEffect(uiState.activeTabId, uiState.activeOverlay) {
            val activeTabId = uiState.activeTabId
            val currentOverlay = uiState.activeOverlay
            val overlayDismissed = lastActiveOverlay == OverlayType.TAB_GRID && currentOverlay != OverlayType.TAB_GRID
            lastActiveOverlay = currentOverlay

            if (currentOverlay != OverlayType.TAB_GRID && activeTabId != null) {
                if (activeTabId != lastCenteredActiveTabId || overlayDismissed) {
                    val targetIndex =
                        dragState.currentTabs.indexOfFirst { it.id == activeTabId }
                            .takeIf { it >= 0 }
                            ?: displayTabs.indexOfFirst { it.id == activeTabId }
                    if (targetIndex >= 0) {
                        lastCenteredActiveTabId = activeTabId
                        val viewportWidthPx =
                            lazyListState.layoutInfo.viewportSize.width
                                .takeIf { it > 0 }
                                ?.toFloat()
                                ?: totalChipWidthPx
                        val centeringOffsetPx = ((viewportWidthPx - activeTabWidthPx) / 2f).coerceAtLeast(0f)
                        val scrollOffset = -centeringOffsetPx.toInt()
                        lazyListState.animateScrollToItem(targetIndex, scrollOffset)
                    }
                }
            }
        }

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
                onLongPress = { tabId -> contextMenuTabId = tabId },
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

        // Continuous edge auto-scroll when a tab is dragged near dock boundaries
        LaunchedEffect(dragState.isDragging, dragState.draggingTabId) {
            val draggingTabId = dragState.draggingTabId
            if (!dragState.isDragging || draggingTabId == null) return@LaunchedEffect

            while (isActive && dragState.isDragging && dragState.draggingTabId == draggingTabId) {
                if (dragState.isOverRemoveZone) {
                    delay(16)
                    continue
                }
                val layoutInfo = lazyListState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingTabId }
                if (visibleItem == null) {
                    delay(16)
                    continue
                }

                val itemStartInViewport = visibleItem.offset + dragState.dragOffsetPx
                val itemEndInViewport = itemStartInViewport + visibleItem.size
                val viewportWidthPx =
                    layoutInfo.viewportSize.width.takeIf { it > 0 }?.toFloat() ?: totalChipWidthPx

                val currentDragIndex = dragState.currentTabs.indexOfFirst { it.id == draggingTabId }
                val canScrollBackward =
                    lazyListState.canScrollBackward &&
                        (currentDragIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                val canScrollForward =
                    lazyListState.canScrollForward &&
                        currentDragIndex in 0 until dragState.currentTabs.lastIndex

                val scrollDelta = computeAutoScrollDelta(
                    itemStartInViewport = itemStartInViewport,
                    itemEndInViewport = itemEndInViewport,
                    viewportWidthPx = viewportWidthPx,
                    rightPinnedOffsetPx = rightPinnedOffsetPx,
                    edgeTriggerThresholdPx = edgeTriggerThresholdPx,
                    maxScrollStepPx = maxScrollStepPx,
                    canScrollBackward = canScrollBackward,
                    canScrollForward = canScrollForward,
                )

                if (scrollDelta != 0f) {
                    val consumed = lazyListState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        val itemLayoutOffsetAfterScroll =
                            lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == draggingTabId }
                                ?.offset
                                ?.toFloat()
                                ?: visibleItem.offset - consumed
                        dragTabToViewportPosition(
                            lazyListState = lazyListState,
                            dragState = dragState,
                            tabId = draggingTabId,
                            metrics = layoutMetrics,
                            visualStartPx = itemStartInViewport,
                            itemLayoutOffsetPx = itemLayoutOffsetAfterScroll,
                            totalChipWidthPx = totalChipWidthPx,
                            rightPinnedOffsetPx = rightPinnedOffsetPx,
                            edgeSlackPx = edgeSlackPx,
                            onSwapVibration = { vibration.tick() },
                        )
                    }
                }
                delay(16)
            }
        }

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
                    .drawWithContent {
                        drawContent()
                        if (isFadeActive) {
                            if (canScrollBackward) {
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            1f to Color.Black,
                                            startX = 0f,
                                            endX = leftFadeWidthPx,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                    size = Size(leftFadeWidthPx, size.height),
                                )
                            }
                            if (canScrollForward) {
                                val endX = size.width - rightPinnedOffsetPx
                                val startX = (endX - rightFadeWidthPx).coerceAtLeast(0f)
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            0f to Color.Black,
                                            1f to Color.Transparent,
                                            startX = startX,
                                            endX = endX,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                    topLeft = Offset(startX, 0f),
                                    size = Size(endX - startX, size.height),
                                )
                                if (endX < size.width) {
                                    drawRect(
                                        color = Color.Transparent,
                                        blendMode = BlendMode.DstIn,
                                        topLeft = Offset(endX, 0f),
                                        size = Size(size.width - endX, size.height),
                                    )
                                }
                            }
                        }
                    }
                    .then(dragReorderModifier),
            userScrollEnabled = !dragState.isDragging && !dragState.isSettling,
            contentPadding = PaddingValues(start = 5.dp, end = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(
                items = dragState.currentTabs,
                key = { _, tab -> tab.id },
            ) { index, tab ->
                val isActive = index == activeIndex
                val tabIsHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home" || (isHome && isActive)
                val isElevated = dragState.draggingTabId == tab.id || dragState.settlingTabId == tab.id

                val itemPlacementModifier =
                    if (isElevated) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            placementSpec =
                                spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                        )
                    }

                if (isActive) {
                    val displayInfo = resolveTabDisplayInfo(tab = tab, isActive = true, isHome = tabIsHome, uiState = uiState)

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
                        loadingProgress = uiState.loadingProgress,
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
                                .then(itemPlacementModifier)
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    alpha = if (isElevated) 0f else 1f
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                        isCompact = false,
                        isRemovePending = false,
                        removeProgress = 0f,
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
                                .then(itemPlacementModifier)
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    alpha = if (isElevated) 0f else 1f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                    )
                }
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

        // Floating Dragged / Settling Tab Overlay (outside LazyRow to prevent clipping above dock)
        val elevatedTabId = dragState.draggingTabId ?: dragState.settlingTabId
        val elevatedTab = if (elevatedTabId != null) dragState.currentTabs.firstOrNull { it.id == elevatedTabId } else null
        if (elevatedTab != null) {
            val isDraggingThis = dragState.draggingTabId == elevatedTab.id
            val isSettlingThis = dragState.settlingTabId == elevatedTab.id
            val isElevatedActive = elevatedTab.id == uiState.activeTabId
            val tabIsHome = elevatedTab.url.isEmpty() || elevatedTab.url == "about:blank" || elevatedTab.title == "Home" || (isHome && isElevatedActive)
            val isOverRemoveZone = isDraggingThis && dragState.isOverRemoveZone

            val removeDistancePx = (-dragState.dragOffsetYPx).coerceAtLeast(0f)
            val removeProgress = (removeDistancePx / removeThresholdPx).coerceIn(0f, 1f)
            val overDistance = ((-dragState.dragOffsetYPx - removeThresholdPx) / removeThresholdPx).coerceIn(0f, 1f)

            val baseDragScale = if (isElevatedActive) 1.05f else 1.08f
            val dynamicDragScale =
                (baseDragScale - (removeProgress * 0.20f) - (overDistance * 0.05f)).coerceAtLeast(0.80f)
            val dynamicDragAlpha =
                (1.0f - (removeProgress * 0.15f) - (overDistance * 0.05f)).coerceIn(0.80f, 1.0f)

            val itemScale =
                when {
                    isDraggingThis -> dynamicDragScale
                    isSettlingThis -> dragState.settleScaleAnim.value
                    else -> 1.0f
                }
            val effectiveScale = itemScale + if (isDraggingThis) dragState.swapScaleBump.value else 0f
            val itemAlpha =
                when {
                    isDraggingThis -> dynamicDragAlpha
                    isSettlingThis -> 1.0f
                    else -> 1.0f
                }

            val overlayX =
                when {
                    isDraggingThis -> dragState.lastVisualXPx
                    isSettlingThis -> {
                        val slotItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == elevatedTab.id }
                        val slotOffset = slotItem?.offset?.toFloat() ?: dragState.lastVisualXPx
                        slotOffset + dragState.settleOffsetAnim.value
                    }
                    else -> 0f
                }
            val overlayY =
                when {
                    isDraggingThis -> dragState.dragOffsetYPx
                    isSettlingThis -> dragState.settleOffsetYAnim.value
                    else -> 0f
                }

            val tabRemoveProgress = if (isDraggingThis) removeProgress else 0f

            val renderedOverlayActiveWidth by animateDpAsState(
                targetValue = if (isDraggingThis) 38.dp else activeTabWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "OverlayActiveTabDragWidth",
            )

            if (isElevatedActive) {
                val displayInfo = resolveTabDisplayInfo(tab = elevatedTab, isActive = isElevatedActive, isHome = tabIsHome, uiState = uiState)

                val activePillBgActual =
                    if (isDraggingThis) {
                        if (isDark) DarkSurface else Color.White
                    } else {
                        centerPillBg
                    }
                val activePillBorderActual =
                    if (isDraggingThis) {
                        ProstoBorders.floatingBorder(isDark)
                    } else {
                        centerPillBorder
                    }

                ActiveTabPill(
                    title = displayInfo.title,
                    domain = displayInfo.domain,
                    url = displayInfo.url,
                    faviconUrl = elevatedTab.faviconUrl,
                    isHome = tabIsHome,
                    backgroundColor = activePillBgActual,
                    border = activePillBorderActual,
                    isDark = isDark,
                    isLoading = uiState.isLoading,
                    loadingProgress = uiState.loadingProgress,
                    onOpenSearch = {},
                    onClose = {},
                    onAddFavorite = { _, _ -> },
                    onShare = {},
                    onReload = {},
                    modifier =
                        Modifier
                            .width(renderedOverlayActiveWidth)
                            .zIndex(20f)
                            .graphicsLayer {
                                translationX = overlayX
                                translationY = overlayY
                                scaleX = effectiveScale
                                scaleY = effectiveScale
                                alpha = itemAlpha
                            },
                    menuExpanded = false,
                    onMenuDismiss = {},
                    isCompact = isDraggingThis,
                    isRemovePending = isOverRemoveZone,
                    removeProgress = tabRemoveProgress,
                )
            } else {
                val isTabLoaded = uiState.isTabLoaded(elevatedTab.id)
                val inactiveBtnBgActual =
                    if (isDraggingThis) {
                        if (isDark) DarkSurface else Color.White
                    } else {
                        circleBtnBg
                    }
                val inactiveBtnBorderActual =
                    if (isDraggingThis) {
                        ProstoBorders.floatingBorder(isDark)
                    } else {
                        null
                    }

                InactiveTabButton(
                    tab = elevatedTab,
                    isDark = isDark,
                    isLoaded = isTabLoaded,
                    grayUnloadedTabs = uiState.settings.isGrayUnloadedTabsEnabled,
                    backgroundColor = inactiveBtnBgActual,
                    border = inactiveBtnBorderActual,
                    isRemovePending = isOverRemoveZone,
                    removeProgress = tabRemoveProgress,
                    onClick = {},
                    onClose = {},
                    onAddFavorite = { _, _ -> },
                    modifier =
                        Modifier
                            .zIndex(20f)
                            .graphicsLayer {
                                translationX = overlayX
                                translationY = overlayY
                                scaleX = effectiveScale
                                scaleY = effectiveScale
                                alpha = itemAlpha
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                            },
                    menuExpanded = false,
                    onMenuDismiss = {},
                )
            }
        }

        // Floating Vanishing Tab Overlay during removal release animation
        val removingTab = dragState.removingTab
        if (removingTab != null) {
            val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            translationX = dragState.removingVisualX
                            translationY = dragState.removeOffsetYAnim.value
                            scaleX = dragState.removeScaleAnim.value
                            scaleY = dragState.removeScaleAnim.value
                            alpha = dragState.removeAlphaAnim.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                        },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isDark) DarkSurface else Color.White,
                    border = ProstoBorders.floatingBorder(isDark),
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Tab",
                            tint = normalTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Circular inactive tab button with favicon or site initial glyph.
 * Transparent background, ProstoMutedSlate icons and text, label-md typography.
 */
@Composable
fun InactiveTabButton(
    tab: Tab,
    isDark: Boolean,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    isLoaded: Boolean = true,
    grayUnloadedTabs: Boolean = true,
    isRemovePending: Boolean = false,
    removeProgress: Float = 0f,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val typography = LocalProstoTypography.current
    val isGray = grayUnloadedTabs && !isLoaded
    val contentAlpha = if (isGray) 0.45f else 1.0f
    val normalTint = if (isDark) {
        if (isGray) DarkOnSurfaceVariant.copy(alpha = 0.6f) else DarkOnSurfaceVariant
    } else {
        if (isGray) ProstoMutedSlate.copy(alpha = 0.6f) else ProstoMutedSlate
    }
    val grayscaleFilter = remember(isGray) {
        if (isGray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }
    val crossAlpha = if (isRemovePending) 1f else ((removeProgress - 0.15f) / 0.70f).coerceIn(0f, 1f)

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
            contentAlignment = Alignment.Center,
        ) {
            val isHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home"
            if (crossAlpha > 0f) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove Tab",
                    tint = normalTint,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = crossAlpha },
                )
            }
            if (crossAlpha < 1f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = 1f - crossAlpha },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isHome) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = normalTint,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        SiteFavicon(
                            faviconUrl = tab.faviconUrl,
                            imageSize = 18.dp,
                            colorFilter = grayscaleFilter,
                            placeholder = {
                                val initial =
                                    tab.title
                                        .trim()
                                        .firstOrNull()
                                        ?.uppercaseChar()
                                if (initial != null && initial.isLetterOrDigit() && tab.title != "New Tab") {
                                    Text(
                                        text = initial.toString(),
                                        style =
                                            typography.labelMd.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                            ),
                                        color = normalTint,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = tab.title,
                                        tint = normalTint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            TabContextMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
                isDark = isDark,
                onCloseTab = onClose,
                onCopyUrl = {
                    copyUrlToClipboard(context, tab.url)
                },
                onAddFavorite = {
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        onAddFavorite(tab.title, tab.url)
                        Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareLink = {
                    shareWebpageUrl(context, tab.url)
                },
            )
        }
    }
}

/**
 * Pinned persistent circular '+' new tab button on the right edge of the dock.
 */
@Composable
fun AddTabDockButton(
    isDark: Boolean,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = normalTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Custom PopupPositionProvider that anchors the tab context menu horizontally to the
 * pressed tab and vertically aligns its bottom edge to the standard dock elevation height
 * (matching ActionPopupMenu's 64.dp elevation above the navigation bar / dock bottom).
 */
internal class TabContextMenuPositionProvider(
    private val density: Density,
    private val margin: androidx.compose.ui.unit.Dp = 12.dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val marginPx = with(density) { margin.roundToPx() }
        val isPill = anchorBounds.height >= with(density) { 40.dp.roundToPx() }
        // For pill (42dp): 14dp gap above pill top places bottom at 64dp above navigation bar
        // For circle (38dp): 16dp gap above circle top aligns to the exact same 64dp elevation
        val bottomGapPx = with(density) { (if (isPill) 14.dp else 16.dp).roundToPx() }

        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))

        val targetBottomY = anchorBounds.top - bottomGapPx
        val y = (targetBottomY - popupContentSize.height).coerceAtLeast(marginPx)

        return IntOffset(x, y)
    }
}

/**
 * Clipping shape that allows shadow to spread outwards on top, left, and right,
 * but clips at the bottom boundary (size.height + dockGapPx) so the modal's downward
 * elevation shadow does not overlay onto the dock surface, keeping the shadow
 * visually underneath the dock capsule.
 */
internal class DockShadowUnderClipShape(
    val dockGapPx: Float,
    val overflowPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect =
            Rect(
                left = -overflowPx,
                top = -overflowPx,
                right = size.width + overflowPx,
                bottom = size.height + dockGapPx,
            )
        return Outline.Rectangle(rect)
    }
}

/**
 * Contextual dropdown menu on tabs (active or inactive).
 * Adheres to Prosto Canvas:
 * - Floating card with 24px radius (ProstoShapes.Card), pure solid surface.
 * - Rim border and Level 2 elevation shadow.
 * - 48px touch targets for menu rows.
 * - Hairline dividers 1px solid #F1F5F9 (Dark: 1px solid #334155).
 * - Clean Manrope title typography.
 * - NO BLUR.
 */
@Composable
fun TabContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    onAddFavorite: () -> Unit,
    onShareLink: (() -> Unit)? = null,
    isReloadVisible: Boolean = false,
    onReload: (() -> Unit)? = null,
) {
    if (!expanded) return

    BackHandler(enabled = true, onBack = onDismissRequest)

    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val menuBorder = ProstoBorders.floatingBorder(isDark)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
    val density = LocalDensity.current
    val popupPositionProvider = remember(density) {
        TabContextMenuPositionProvider(density)
    }

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = popupPositionProvider,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        val dockGapPx = with(density) { 10.dp.toPx() }
        val overflowPx = with(density) { 32.dp.toPx() }

        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        shape = DockShadowUnderClipShape(dockGapPx, overflowPx)
                        clip = true
                    }
                    .drawWithContent {
                        clipRect(
                            left = -overflowPx,
                            top = -overflowPx,
                            right = size.width + overflowPx,
                            bottom = size.height + dockGapPx,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    },
        ) {
            Surface(
                shape = ProstoShapes.Card,
                color = menuBg,
                shadowElevation = 12.dp,
                border = menuBorder,
                modifier = Modifier.width(172.dp),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Reload (First item when isReloadVisible is true)
                    if (isReloadVisible && onReload != null) {
                        PopupMenuItem(
                            icon = Icons.Default.Refresh,
                            label = stringResource(R.string.reload_tab),
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onReload()
                            },
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)
                    }

                    // Close Tab
                    PopupMenuItem(
                        icon = Icons.Default.Close,
                        label = "Close tab",
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onCloseTab()
                        },
                    )

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Copy URL
                    PopupMenuItem(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy URL",
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onCopyUrl()
                        },
                    )

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Add to Favorites
                    PopupMenuItem(
                        icon = Icons.Default.Bookmark,
                        label = "Add to Favorites",
                        isActive = false,
                        isDark = isDark,
                        onClick = {
                            onDismissRequest()
                            onAddFavorite()
                        },
                    )

                    // Share Link
                    if (onShareLink != null) {
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PopupMenuItem(
                            icon = Icons.Default.Share,
                            label = "Share Link",
                            isActive = false,
                            isDark = isDark,
                            onClick = {
                                onDismissRequest()
                                onShareLink()
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class TabDisplayInfo(
    val title: String,
    val url: String,
    val domain: String,
)

private fun resolveTabDisplayInfo(
    tab: Tab,
    isActive: Boolean,
    isHome: Boolean,
    uiState: BrowserUiState,
): TabDisplayInfo {
    val currentTitle =
        if (isHome) {
            "Home"
        } else {
            when {
                isActive && uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                else -> "Prosto Browser"
            }
        }
    val currentUrl = if (isActive && uiState.currentUrl.isNotEmpty()) uiState.currentUrl else tab.url
    val currentDomain = if (isHome) "" else extractDomain(currentUrl)
    return TabDisplayInfo(title = currentTitle, url = currentUrl, domain = currentDomain)
}

private fun extractDomain(url: String): String {
    if (url.isEmpty() || url == "about:blank") return ""
    return try {
        URI(url).host?.removePrefix("www.") ?: url.removePrefix("https://").removePrefix("http://")
    } catch (_: Exception) {
        url
    }
}
