package com.jestk.browser.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.util.LocalVibrationFeedback
import kotlin.math.abs

/**
 * Long-press drag-reorder gesture hosted by the stable `LazyRow` node instead of by the
 * individual tab items.
 *
 * Reordering mutates the item order, and Compose disposes and recreates the dragged item's
 * `LayoutNode` when its slot content type changes (active pill vs. inactive circle). Disposing
 * that node synthesizes a pointer cancel, which used to abort the drag mid-reorder and spring
 * the tab back. The `LazyRow` node itself is never replaced, so the pointer stream survives any
 * number of reorders.
 *
 * The gesture only claims the pointer after the long-press timeout, so taps, link clicks and
 * normal scrolling continue to work.
 */
@Composable
internal fun rememberTabStripDragReorderModifier(
    lazyListState: LazyListState,
    dragState: TabStripDragState,
    layoutMetrics: TabStripLayoutMetrics,
    displayTabs: List<Tab>,
    activeTabId: String?,
    tabSpacingPx: Float,
    totalChipWidthPx: Float,
    rightPinnedOffsetPx: Float,
    edgeSlackPx: Float,
    removeThresholdPx: Float = with(LocalDensity.current) { 36.dp.toPx() },
    onLongPress: (String) -> Unit,
): Modifier {
    val currentMetrics by rememberUpdatedState(layoutMetrics)
    val currentTabs by rememberUpdatedState(displayTabs)
    val currentActiveTabId by rememberUpdatedState(activeTabId)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentVibration by rememberUpdatedState(LocalVibrationFeedback.current)
    val density = LocalDensity.current
    val touchSlop = with(density) { 8.dp.toPx() }

    return Modifier.pointerInput(touchSlop, tabSpacingPx, totalChipWidthPx, rightPinnedOffsetPx, edgeSlackPx, removeThresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tabId = lazyListState.tabIdAt(down.position.x, tabSpacingPx) ?: return@awaitEachGesture
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            currentVibration.confirm()
            val pointerId = longPress.id
            var totalDragX = 0f
            var totalDragY = 0f
            var isDragging = false
            var cancelled = false
            var completed = false
            var grabOffsetPx = 0f
            val startTouchY = down.position.y

            fun isReleasedActive() = tabId == currentActiveTabId

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed) {
                        if (change != null && change.changedToUp()) {
                            change.consume()
                        } else {
                            cancelled = true
                        }
                        break
                    }

                    val pointerDelta = change.positionChange()
                    totalDragX += pointerDelta.x
                    totalDragY += pointerDelta.y
                    change.consume()

                    if (!isDragging && (abs(totalDragX) > touchSlop || abs(totalDragY) > touchSlop)) {
                        isDragging = true
                        val initialOffset =
                            lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == tabId }
                                ?.offset
                                ?.toFloat()
                                ?: 0f
                        dragState.onDragStart(tabId, initialVisualX = initialOffset)
                        val visibleItem =
                            lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tabId }
                        if (visibleItem != null) {
                            grabOffsetPx = down.position.x - visibleItem.offset
                        }
                    }
                    if (isDragging) {
                        dragTabToViewportPosition(
                            lazyListState = lazyListState,
                            dragState = dragState,
                            tabId = tabId,
                            metrics = currentMetrics,
                            visualStartPx = change.position.x - grabOffsetPx,
                            itemLayoutOffsetPx =
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == tabId }
                                    ?.offset
                                    ?.toFloat()
                                    ?: 0f,
                            totalChipWidthPx = totalChipWidthPx,
                            rightPinnedOffsetPx = rightPinnedOffsetPx,
                            edgeSlackPx = edgeSlackPx,
                            dragOffsetYPx = change.position.y - startTouchY,
                            removeThresholdPx = removeThresholdPx,
                            onSwapVibration = { currentVibration.tick() },
                            onEnterRemoveZone = { currentVibration.tick() },
                            onExitRemoveZone = { currentVibration.tick() },
                        )
                    }
                }

                if (isDragging) {
                    completed = true
                    if (cancelled) {
                        dragState.onDragCancel(isReleasedActive = isReleasedActive())
                    } else {
                        dragState.onDragEnd(
                            isReleasedActive = isReleasedActive(),
                            originalTabs = currentTabs,
                        )
                    }
                } else if (!cancelled) {
                    currentOnLongPress(tabId)
                }
            } finally {
                if (isDragging && !completed) {
                    dragState.onDragCancel(isReleasedActive = isReleasedActive())
                }
            }
        }
    }
}

internal fun dragTabToViewportPosition(
    lazyListState: LazyListState,
    dragState: TabStripDragState,
    tabId: String,
    metrics: TabStripLayoutMetrics,
    visualStartPx: Float,
    itemLayoutOffsetPx: Float,
    totalChipWidthPx: Float,
    rightPinnedOffsetPx: Float,
    edgeSlackPx: Float,
    dragOffsetYPx: Float = 0f,
    removeThresholdPx: Float = 0f,
    onSwapVibration: () -> Unit,
    onEnterRemoveZone: () -> Unit = {},
    onExitRemoveZone: () -> Unit = {},
) {
    if (removeThresholdPx > 0f) {
        dragState.updateVerticalDrag(
            offsetY = dragOffsetYPx,
            removeThresholdPx = removeThresholdPx,
            onEnterRemoveZone = onEnterRemoveZone,
            onExitRemoveZone = onExitRemoveZone,
        )
    }

    val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tabId } ?: return
    val oldIndex = dragState.currentTabs.indexOfFirst { it.id == tabId }
    if (oldIndex < 0) return

    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
    val oldSlotStart = dragState.getSlotStart(oldIndex, dragState.currentTabs, metrics)
    val boundedVisualStart =
        visualStartPx.coerceIn(
            -edgeSlackPx,
            (totalChipWidthPx - rightPinnedOffsetPx + edgeSlackPx) - visibleItem.size,
        )
    dragState.updateVisualX(boundedVisualStart)
    val targetOffset = boundedVisualStart - itemLayoutOffsetPx
    val deltaX = targetOffset - dragState.dragOffsetPx
    if (deltaX != 0f) {
        dragState.onDrag(
            tabId = tabId,
            deltaX = deltaX,
            metrics = metrics,
            onSwapVibration = onSwapVibration,
        )
    }

    val newIndex = dragState.currentTabs.indexOfFirst { it.id == tabId }
    if (newIndex < 0) return
    val newSlotStart = dragState.getSlotStart(newIndex, dragState.currentTabs, metrics)
    val draggedTabBecameFirst = newIndex < oldIndex && newIndex <= firstVisibleIndex
    if (draggedTabBecameFirst && !dragState.isOverRemoveZone) {
        lazyListState.requestScrollToItem(newIndex)
    }
    val newItemOffset =
        if (draggedTabBecameFirst && !dragState.isOverRemoveZone) {
            0f
        } else {
            itemLayoutOffsetPx + newSlotStart - oldSlotStart
        }
    dragState.updateDragOffset(boundedVisualStart - newItemOffset)
}

private fun LazyListState.tabIdAt(
    x: Float,
    spacingPx: Float,
): String? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { item ->
            x >= item.offset - spacingPx / 2f && x <= item.offset + item.size + spacingPx / 2f
        }?.key as? String
