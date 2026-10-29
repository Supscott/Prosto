package com.jestk.browser.ui.navigation

import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.jestk.browser.R
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

        LaunchedEffect(uiState.activeTabId, activeIndex, dragState.isDragging, dragState.isSettling) {
            if (!dragState.isDragging && !dragState.isSettling && activeIndex in dragState.currentTabs.indices) {
                val viewportWidthPx =
                    lazyListState.layoutInfo.viewportSize.width
                        .takeIf { it > 0 }
                        ?.toFloat()
                        ?: totalChipWidthPx
                val centeringOffsetPx = ((viewportWidthPx - activeTabWidthPx) / 2f).coerceAtLeast(0f)
                val scrollOffset = -centeringOffsetPx.toInt()
                lazyListState.animateScrollToItem(activeIndex, scrollOffset)
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

        LazyRow(
            state = lazyListState,
            modifier =
                Modifier
                    .fillMaxWidth()
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
                val isDraggingThis = dragState.draggingTabId == tab.id
                val isSettlingThis = dragState.settlingTabId == tab.id
                val isElevated = isDraggingThis || isSettlingThis
                val isOverRemoveZone = isDraggingThis && dragState.isOverRemoveZone

                val removeDistancePx = (-dragState.dragOffsetYPx).coerceAtLeast(0f)
                val removeProgress = (removeDistancePx / removeThresholdPx).coerceIn(0f, 1f)
                val overDistance = ((-dragState.dragOffsetYPx - removeThresholdPx) / removeThresholdPx).coerceIn(0f, 1f)

                val renderedActiveWidth by animateDpAsState(
                    targetValue = if (isDraggingThis) 38.dp else activeTabWidth,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "ActiveTabDragWidth",
                )

                val itemTranslationX =
                    when {
                        isDraggingThis -> dragState.dragOffsetPx
                        isSettlingThis -> dragState.settleOffsetAnim.value
                        else -> 0f
                    }
                val itemTranslationY =
                    when {
                        isDraggingThis -> dragState.dragOffsetYPx
                        isSettlingThis -> dragState.settleOffsetYAnim.value
                        else -> 0f
                    }

                val baseDragScale = if (isActive) 1.05f else 1.08f
                val dynamicDragScale =
                    (baseDragScale - (removeProgress * 0.45f) - (overDistance * 0.05f)).coerceAtLeast(0.50f)
                val dynamicDragAlpha =
                    (1.0f - (removeProgress * 0.55f) - (overDistance * 0.05f)).coerceIn(0.40f, 1.0f)

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
                        else -> 1.0f
                    }

                val itemPlacementModifier =
                    if (isDraggingThis) {
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

                val tabRemoveProgress = if (isDraggingThis) removeProgress else 0f

                if (isActive) {
                    val currentTitle =
                        if (tabIsHome) {
                            "Home"
                        } else {
                            when {
                                isActive && uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                                tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                                else -> "Prosto Browser"
                            }
                        }
                    val currentUrl = if (isActive && uiState.currentUrl.isNotEmpty()) uiState.currentUrl else tab.url
                    val currentDomain = if (tabIsHome) "" else extractDomain(currentUrl)

                    val activePillBgActual = centerPillBg
                    val activePillBorderActual = centerPillBorder

                    ActiveTabPill(
                        title = currentTitle,
                        domain = currentDomain,
                        url = currentUrl,
                        faviconUrl = tab.faviconUrl,
                        isHome = tabIsHome,
                        backgroundColor = activePillBgActual,
                        border = activePillBorderActual,
                        isDark = isDark,
                        isLoading = uiState.isLoading,
                        loadingProgress = uiState.loadingProgress,
                        onOpenSearch = {
                            val targetPrefill = if (currentUrl.isNotBlank() && currentUrl != "about:blank") currentUrl else null
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
                                .zIndex(if (isElevated) 10f else 0f)
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    translationX = itemTranslationX
                                    translationY = itemTranslationY
                                    scaleX = effectiveScale
                                    scaleY = effectiveScale
                                    alpha = itemAlpha
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                        isCompact = isDraggingThis,
                        isRemovePending = isOverRemoveZone,
                        removeProgress = tabRemoveProgress,
                    )
                } else {
                    val isTabLoaded = uiState.isTabLoaded(tab.id)
                    val inactiveBtnBgActual =
                        if (isDraggingThis) {
                            if (isDark) DarkSurface.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.90f)
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
                        tab = tab,
                        isDark = isDark,
                        isLoaded = isTabLoaded,
                        grayUnloadedTabs = uiState.settings.isGrayUnloadedTabsEnabled,
                        backgroundColor = inactiveBtnBgActual,
                        border = inactiveBtnBorderActual,
                        isRemovePending = isOverRemoveZone,
                        removeProgress = tabRemoveProgress,
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
                                .zIndex(if (isElevated) 10f else 0f)
                                .testTag("tab_${tab.id}")
                                .graphicsLayer {
                                    translationX = itemTranslationX
                                    translationY = itemTranslationY
                                    scaleX = effectiveScale
                                    scaleY = effectiveScale
                                    alpha = itemAlpha
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                },
                        menuExpanded = contextMenuTabId == tab.id,
                        onMenuDismiss = { contextMenuTabId = null },
                    )
                }
            }
        }

        // Left edge fade (visible when scrolled right)
        if (lazyListState.canScrollBackward) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(36.dp)
                        .clip(RoundedCornerShape(topStart = 25.dp, bottomStart = 25.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        dockBg,
                                        dockBg.copy(alpha = dockBg.alpha * 0.75f),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )
        }

        // Right edge horizontal gradient scrim beneath the pinned button
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(42.dp)
                    .clip(RoundedCornerShape(topEnd = 25.dp, bottomEnd = 25.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                dockBg.copy(alpha = dockBg.alpha * 0.75f),
                                dockBg,
                            ),
                        ),
                    ),
        )

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
                    color = if (isDark) DarkSurface.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.90f),
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
    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val menuBorder = ProstoBorders.floatingBorder(isDark)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = ProstoShapes.Card,
        containerColor = menuBg,
        shadowElevation = 12.dp,
        border = menuBorder,
        modifier = Modifier.width(172.dp),
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true),
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

private fun extractDomain(url: String): String {
    if (url.isEmpty() || url == "about:blank") return ""
    return try {
        URI(url).host?.removePrefix("www.") ?: url.removePrefix("https://").removePrefix("http://")
    } catch (_: Exception) {
        url
    }
}
