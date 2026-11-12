package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.jestk.browser.ui.navigation.TabStripDragState.SwapDirection
import kotlinx.coroutines.launch

internal fun TabStripDragState.onDrag(
    tabId: String,
    deltaX: Float,
    metrics: TabStripLayoutMetrics,
) {
    if (!isDragging || draggingTabId != tabId) return
    dragOffsetPx += deltaX
    if (isOverRemoveZone) {
        return
    }

    val slots = dockSlots(currentTabs, metrics)
    val draggedSlotIndex = slots.indexOfFirst { it.key == tabId }
    if (draggedSlotIndex < 0) return
    val draggedSlot = slots[draggedSlotIndex]
    val visualStart = draggedSlot.startPx + dragOffsetPx
    val visualEnd = visualStart + draggedSlot.widthPx
    val visualCenter = visualStart + 0.5f * draggedSlot.widthPx

    if (draggedSlot.isFolderItem) {
        // A dragged folder moves as one group and never splits another folder's group.
        val draggedFolderId = draggedSlot.folderId ?: return
        val folderTabs = currentTabs.filter { it.folderId == draggedFolderId }
        val ownGroupLastSlotIndex = slots.indexOfLast { it.folderId == draggedFolderId }

        val rightSlot = slots.getOrNull(ownGroupLastSlotIndex + 1)
        if (rightSlot != null) {
            val isReverseSwap = lastSwapDirection == SwapDirection.LEFT && rightSlot.key == lastSwapReverseTargetKey
            val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f
            val rightTriggerX =
                rightSlot.folderId?.let { groupBounds(slots, it)?.second }
                    ?: (rightSlot.startPx + 0.5f * rightSlot.widthPx)
            if (visualCenter > rightTriggerX + hysteresisMargin) {
                val mutable = currentTabs.toMutableList()
                mutable.removeAll { it.folderId == draggedFolderId }
                val anchorIndex = mutable.indexOfFirst { it.id == rightSlot.tabIds.last() }
                val insertIndex = if (anchorIndex >= 0) anchorIndex + 1 else mutable.size
                mutable.addAll(insertIndex.coerceIn(0, mutable.size), folderTabs)
                currentTabs = mutable
                dragOffsetPx = visualStart - getSlotStart(tabId, mutable, metrics)
                lastSwapDirection = SwapDirection.RIGHT
                lastSwapReverseTargetKey = rightSlot.key
                triggerSwapBump()
                return
            }
        }

        val leftSlot = slots.getOrNull(draggedSlotIndex - 1)
        if (leftSlot != null) {
            val isReverseSwap = lastSwapDirection == SwapDirection.RIGHT && leftSlot.key == lastSwapReverseTargetKey
            val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f
            val leftTriggerX =
                leftSlot.folderId?.let { groupBounds(slots, it)?.first }
                    ?: (leftSlot.startPx + 0.5f * leftSlot.widthPx)
            if (visualCenter < leftTriggerX - hysteresisMargin) {
                val mutable = currentTabs.toMutableList()
                mutable.removeAll { it.folderId == draggedFolderId }
                val anchorIndex = mutable.indexOfFirst { it.id == leftSlot.tabIds.first() }
                val insertIndex = if (anchorIndex >= 0) anchorIndex else 0
                mutable.addAll(insertIndex.coerceIn(0, mutable.size), folderTabs)
                currentTabs = mutable
                dragOffsetPx = visualStart - getSlotStart(tabId, mutable, metrics)
                lastSwapDirection = SwapDirection.LEFT
                lastSwapReverseTargetKey = leftSlot.key
                triggerSwapBump()
                return
            }
        }
        return
    }

    // A collapsed folder is one visible slot: hover and spring-load it while the dragged tab
    // covers it, but never reorder into it - its member tabs are not rendered.
    val hoveredClosedFolder = slots.firstOrNull { slot ->
        slot.isFolderItem &&
            slot.isCollapsedFolder &&
            visualCenter >= slot.startPx &&
            visualCenter <= slot.endPx
    }

    if (hoveredClosedFolder != null) {
        updateClosedFolderHover(hoveredClosedFolder.folderId)
        return
    } else if (hoveredFolderId != null) {
        updateClosedFolderHover(null)
    }

    // Leave a spring-loaded folder once the dragged tab clears its visible region.
    val springId = springLoadedFolderId
    if (springId != null) {
        val springBounds = groupBounds(slots, springId)
        if (springBounds != null &&
            (
                visualCenter < springBounds.first - metrics.hysteresisPx ||
                    visualCenter > springBounds.second + metrics.hysteresisPx
            )
        ) {
            onSpringLoadedFolderExited()
        }
    }

    // Swap right. The indicator of an expanded folder is a container, not a target: the tab
    // swaps with its first visible member. A collapsed folder's indicator is crossed as one
    // indivisible group, which is what keeps the tab draggable across it.
    var rightSlot = slots.getOrNull(draggedSlotIndex + 1)
    if (rightSlot != null && rightSlot.isFolderItem && !rightSlot.isCollapsedFolder) {
        rightSlot = slots.getOrNull(draggedSlotIndex + 2) ?: rightSlot
    }
    if (rightSlot != null) {
        val tabFlatIndex = currentTabs.indexOfFirst { it.id == tabId }
        val isReverseSwap = lastSwapDirection == SwapDirection.LEFT && rightSlot.key == lastSwapReverseTargetKey
        val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f
        val canSwapRight = when {
            rightSlot.isFolderItem -> {
                val groupEnd = rightSlot.folderId?.let { groupBounds(slots, it)?.second } ?: rightSlot.endPx
                visualCenter > groupEnd + hysteresisMargin
            }
            draggedSlot.widthPx > rightSlot.widthPx ->
                visualEnd > rightSlot.startPx + 0.5f * rightSlot.widthPx + hysteresisMargin
            else -> visualCenter > rightSlot.startPx + 0.5f * rightSlot.widthPx + hysteresisMargin
        }

        if (tabFlatIndex >= 0 && canSwapRight) {
            val mutable = currentTabs.toMutableList()
            val oldTab = mutable.removeAt(tabFlatIndex)
            val updatedTab = oldTab.copy(folderId = if (rightSlot.isFolderItem) null else rightSlot.folderId)
            val firstTargetIndex = currentTabs.indexOfFirst { it.id == rightSlot.tabIds.first() }
            val lastTargetIndex = currentTabs.indexOfFirst { it.id == rightSlot.tabIds.last() }
            val insertIndex = when {
                firstTargetIndex < 0 || lastTargetIndex < 0 -> mutable.size
                tabFlatIndex < firstTargetIndex -> lastTargetIndex
                else -> lastTargetIndex + 1
            }
            mutable.add(insertIndex.coerceIn(0, mutable.size), updatedTab)
            currentTabs = mutable
            dragOffsetPx = visualStart - getSlotStart(tabId, mutable, metrics)
            lastSwapDirection = SwapDirection.RIGHT
            lastSwapReverseTargetKey = rightSlot.key
            triggerSwapBump()
            return
        }
    }

    // Swap left. Crossing a folder indicator always means leaving that folder, never joining it.
    val leftSlot = slots.getOrNull(draggedSlotIndex - 1)
    if (leftSlot != null) {
        val tabFlatIndex = currentTabs.indexOfFirst { it.id == tabId }
        val isReverseSwap = lastSwapDirection == SwapDirection.RIGHT && leftSlot.key == lastSwapReverseTargetKey
        val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f
        val canSwapLeft = when {
            leftSlot.isFolderItem -> {
                val groupStart = leftSlot.folderId?.let { groupBounds(slots, it)?.first } ?: leftSlot.startPx
                visualCenter < groupStart - hysteresisMargin
            }
            draggedSlot.widthPx > leftSlot.widthPx ->
                visualStart < leftSlot.endPx - 0.5f * leftSlot.widthPx - hysteresisMargin
            else -> visualCenter < leftSlot.startPx + 0.5f * leftSlot.widthPx - hysteresisMargin
        }

        if (tabFlatIndex >= 0 && canSwapLeft) {
            val mutable = currentTabs.toMutableList()
            val oldTab = mutable.removeAt(tabFlatIndex)
            val updatedTab = oldTab.copy(folderId = if (leftSlot.isFolderItem) null else leftSlot.folderId)
            val firstTargetIndex = currentTabs.indexOfFirst { it.id == leftSlot.tabIds.first() }
            val insertIndex = when {
                firstTargetIndex < 0 -> 0
                tabFlatIndex < firstTargetIndex -> firstTargetIndex - 1
                else -> firstTargetIndex
            }
            mutable.add(insertIndex.coerceIn(0, mutable.size), updatedTab)
            currentTabs = mutable
            dragOffsetPx = visualStart - getSlotStart(tabId, mutable, metrics)
            lastSwapDirection = SwapDirection.LEFT
            lastSwapReverseTargetKey = leftSlot.key
            triggerSwapBump()
            return
        }
    }
}

private fun TabStripDragState.triggerSwapBump() {
    swapBumpJob?.cancel()
    swapBumpJob = coroutineScope.launch {
        swapScaleBump.snapTo(0.06f)
        swapScaleBump.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
        )
        swapBumpJob = null
    }
}

internal fun TabStripDragState.onDrag(
    tabId: String,
    deltaX: Float,
    activeTabWidthPx: Float,
    inactiveTabWidthPx: Float,
    spacingPx: Float,
    activeTabId: String? = null,
    hysteresisPx: Float = 10f,
) {
    onDrag(
        tabId = tabId,
        deltaX = deltaX,
        metrics = TabStripLayoutMetrics(
            activeTabWidthPx = activeTabWidthPx,
            inactiveTabWidthPx = inactiveTabWidthPx,
            spacingPx = spacingPx,
            activeTabId = activeTabId,
            hysteresisPx = hysteresisPx
        ),
    )
}

internal fun TabStripDragState.onDrag(
    tabId: String,
    deltaX: Float,
    swapThreshold: Float,
) {
    onDrag(
        tabId = tabId,
        deltaX = deltaX,
        activeTabWidthPx = swapThreshold,
        inactiveTabWidthPx = swapThreshold,
        spacingPx = 0f,
        activeTabId = null,
        hysteresisPx = 0f,
    )
}
