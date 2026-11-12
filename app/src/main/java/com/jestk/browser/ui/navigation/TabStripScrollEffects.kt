package com.jestk.browser.ui.navigation

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TabStripActiveTabCentering(
    uiState: BrowserUiState,
    lazyListState: LazyListState,
    currentDockItemsState: State<List<DockItem>>,
    dragState: TabStripDragState,
    displayTabs: List<Tab>,
    totalChipWidthPx: Float,
    activeTabWidthPx: Float,
) {
    val currentDockItems by currentDockItemsState

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
                    currentDockItems.indexOfFirst { it.key == activeTabId }
                        .takeIf { it >= 0 }
                        ?: dragState.currentTabs.indexOfFirst { it.id == activeTabId }
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
}

@Composable
internal fun TabStripEdgeAutoScroll(
    dragState: TabStripDragState,
    lazyListState: LazyListState,
    layoutMetrics: TabStripLayoutMetrics,
    totalChipWidthPx: Float,
    rightPinnedOffsetPx: Float,
    edgeTriggerThresholdPx: Float,
    maxScrollStepPx: Float,
    edgeSlackPx: Float,
) {
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

            val currentDragIndex = dragState.getSlotIndex(draggingTabId, dragState.currentTabs, layoutMetrics)
            val lastDockIndex = layoutInfo.totalItemsCount - 1
            val canScrollBackward =
                lazyListState.canScrollBackward &&
                    (currentDragIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
            val canScrollForward =
                lazyListState.canScrollForward &&
                    currentDragIndex in 0 until lastDockIndex

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
                    )
                }
            }
            delay(16)
        }
    }
}

internal fun Modifier.tabStripEdgeFade(
    isFadeActive: Boolean,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    leftFadeWidthPx: Float,
    rightFadeWidthPx: Float,
    rightPinnedOffsetPx: Float,
): Modifier =
    drawWithContent {
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
