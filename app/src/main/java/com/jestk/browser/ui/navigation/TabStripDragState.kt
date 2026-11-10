package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Encapsulates dimension metrics and hysteresis configuration for tab strip drag & drop reordering.
 */
internal data class TabStripLayoutMetrics(
    val activeTabWidthPx: Float,
    val inactiveTabWidthPx: Float,
    val spacingPx: Float,
    val activeTabId: String? = null,
    val hysteresisPx: Float = 10f,
    val draggingTabId: String? = null,
    val itemWidthProvider: ((String) -> Float?)? = null,
    val folderProvider: ((String) -> TabFolder?)? = null,
) {
    fun getItemWidth(key: String, fallbackWidth: Float): Float {
        return itemWidthProvider?.invoke(key) ?: fallbackWidth
    }
}

/**
 * Computes proportional auto-scroll step when a tab is dragged near dock boundaries.
 * Returns negative delta to scroll left (backward), positive delta to scroll right (forward),
 * or 0f when the item is within comfortable bounds or the list cannot scroll further.
 */
internal fun computeAutoScrollDelta(
    itemStartInViewport: Float,
    itemEndInViewport: Float,
    viewportWidthPx: Float,
    rightPinnedOffsetPx: Float,
    edgeTriggerThresholdPx: Float,
    maxScrollStepPx: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Float {
    val rightLimit = viewportWidthPx - rightPinnedOffsetPx
    return when {
        itemStartInViewport < edgeTriggerThresholdPx && canScrollBackward -> {
            val fraction = ((edgeTriggerThresholdPx - itemStartInViewport) / edgeTriggerThresholdPx).coerceIn(0.2f, 1f)
            -maxScrollStepPx * fraction
        }
        itemEndInViewport > (rightLimit - edgeTriggerThresholdPx) && canScrollForward -> {
            val fraction = ((itemEndInViewport - (rightLimit - edgeTriggerThresholdPx)) / edgeTriggerThresholdPx).coerceIn(0.2f, 1f)
            maxScrollStepPx * fraction
        }
        else -> 0f
    }
}

/**
 * State controller managing drag & drop tab reordering and tactile spring release physics
 * in [TabStripRow].
 *
 * Provides smooth dragging, immediate slot swapping with a scale bump,
 * and spring-driven release settlement back to translation 0f and scale 1.0f.
 */
internal const val SPRING_LOAD_HOVER_TIMEOUT_MS = 650L

internal class TabStripDragState(
    initialTabs: List<Tab>,
    val coroutineScope: CoroutineScope,
    val onReorder: (List<Tab>) -> Unit,
    val onRemoveTab: (String) -> Unit = {},
    val foldersProvider: () -> List<TabFolder> = { emptyList() },
    val onToggleFolderCollapse: (String) -> Unit = {},
    val onHoverVibration: () -> Unit = {},
    val onSpringLoadVibration: () -> Unit = {},
) {
    var currentTabs by mutableStateOf(initialTabs)
        private set

    var draggingTabId by mutableStateOf<String?>(null)
        private set

    var hoveredFolderId by mutableStateOf<String?>(null)
        internal set

    var springLoadedFolderId by mutableStateOf<String?>(null)
        internal set

    private var springLoadJob: Job? = null
    private var draggedTabOriginalFolderId: String? = null

    var settlingTabId by mutableStateOf<String?>(null)
        private set

    var removingTab by mutableStateOf<Tab?>(null)
        private set

    val removingTabId: String?
        get() = removingTab?.id

    var lastVisualXPx by mutableFloatStateOf(0f)
        private set

    var removingVisualX by mutableFloatStateOf(0f)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    var dragOffsetYPx by mutableFloatStateOf(0f)
        private set

    var isOverRemoveZone by mutableStateOf(false)
        private set

    var isDragging by mutableStateOf(false)
        private set

    val isSettling: Boolean
        get() = settlingTabId != null

    val isRemoving: Boolean
        get() = removingTab != null

    val settleOffsetAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleOffsetYAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleScaleAnim = Animatable(1.0f)
    val swapScaleBump = Animatable(0f)

    val removeScaleAnim = Animatable(1.0f)
    val removeAlphaAnim = Animatable(1.0f)
    val removeOffsetYAnim = Animatable(0f)

    private enum class SwapDirection { LEFT, RIGHT }
    private var lastSwapDirection: SwapDirection? = null
    private var lastSwapReverseTargetKey: String? = null
    private var swapBumpJob: Job? = null

    private var settleJob: Job? = null
    private var removeJob: Job? = null
    private var pendingTabs: List<Tab>? = null

    fun updateTabs(newTabs: List<Tab>) {
        if (!isDragging && !isSettling && !isRemoving) {
            currentTabs = newTabs
            pendingTabs = null
        } else {
            val filtered = removingTab?.id?.let { id -> newTabs.filter { it.id != id } } ?: newTabs
            pendingTabs = filtered
        }
    }

    private fun resetSwapState() {
        lastSwapDirection = null
        lastSwapReverseTargetKey = null
        swapBumpJob?.cancel()
        swapBumpJob = null
        if (swapScaleBump.value != 0f) {
            coroutineScope.launch {
                swapScaleBump.snapTo(0f)
            }
        }
    }

    fun cancelSettlingImmediately() {
        springLoadJob?.cancel()
        springLoadJob = null
        hoveredFolderId = null
        springLoadedFolderId = null
        if (isSettling) {
            settleJob?.cancel()
            settleJob = null
            settlingTabId = null
            resetSwapState()
            coroutineScope.launch {
                settleOffsetAnim.snapTo(0f)
                settleOffsetYAnim.snapTo(0f)
                settleScaleAnim.snapTo(1.0f)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
        if (isRemoving) {
            removeJob?.cancel()
            removeJob = null
            val removedId = removingTab?.id
            removingTab = null
            coroutineScope.launch {
                removeScaleAnim.snapTo(1.0f)
                removeAlphaAnim.snapTo(1.0f)
                removeOffsetYAnim.snapTo(0f)
            }
            if (removedId != null) {
                onRemoveTab(removedId)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun updateVisualX(visualX: Float) {
        lastVisualXPx = visualX
    }

    fun onDragStart(tabId: String, initialVisualX: Float = 0f) {
        if (isSettling || isRemoving) {
            cancelSettlingImmediately()
        }
        draggingTabId = tabId
        draggedTabOriginalFolderId = currentTabs.find { it.id == tabId }?.folderId
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        lastVisualXPx = initialVisualX
        isOverRemoveZone = false
        isDragging = true
        hoveredFolderId = null
        springLoadedFolderId = null
        springLoadJob?.cancel()
        springLoadJob = null
        resetSwapState()
    }

    fun updateClosedFolderHover(hoveredFolderId: String?) {
        if (this.hoveredFolderId == hoveredFolderId) return
        this.hoveredFolderId = hoveredFolderId
        springLoadJob?.cancel()
        springLoadJob = null

        if (hoveredFolderId != null) {
            onHoverVibration()
            springLoadJob = coroutineScope.launch {
                delay(SPRING_LOAD_HOVER_TIMEOUT_MS)
                if (isDragging && draggingTabId != null && this@TabStripDragState.hoveredFolderId == hoveredFolderId) {
                    springLoadedFolderId = hoveredFolderId
                    this@TabStripDragState.hoveredFolderId = null
                    onSpringLoadVibration()
                    onToggleFolderCollapse(hoveredFolderId)
                }
            }
        }
    }

    fun onSpringLoadedFolderExited() {
        val folderId = springLoadedFolderId ?: return
        springLoadedFolderId = null
        val draggedId = draggingTabId
        if (draggedId != null) {
            val idx = currentTabs.indexOfFirst { it.id == draggedId }
            if (idx >= 0 && currentTabs[idx].folderId == folderId) {
                val mutable = currentTabs.toMutableList()
                mutable[idx] = mutable[idx].copy(folderId = draggedTabOriginalFolderId)
                currentTabs = mutable
            }
        }
        onToggleFolderCollapse(folderId)
    }

    fun updateDragOffset(offsetPx: Float, offsetYPx: Float = dragOffsetYPx) {
        if (isDragging) {
            dragOffsetPx = offsetPx
            dragOffsetYPx = offsetYPx
        }
    }

    fun updateVerticalDrag(
        offsetY: Float,
        removeThresholdPx: Float,
        onEnterRemoveZone: () -> Unit = {},
        onExitRemoveZone: () -> Unit = {},
    ) {
        if (!isDragging) return
        dragOffsetYPx = if (offsetY > 0f) offsetY * 0.15f else offsetY
        val wasOverRemove = isOverRemoveZone
        val nowOverRemove = dragOffsetYPx <= -removeThresholdPx
        if (wasOverRemove != nowOverRemove) {
            isOverRemoveZone = nowOverRemove
            if (nowOverRemove) {
                onEnterRemoveZone()
            } else {
                onExitRemoveZone()
            }
        }
    }

    internal fun getTabWidth(
        tab: Tab,
        metrics: TabStripLayoutMetrics,
    ): Float {
        if (tab.id == metrics.draggingTabId) return metrics.inactiveTabWidthPx
        val isActive = tab.isActive || (metrics.activeTabId != null && tab.id == metrics.activeTabId)
        val defaultWidth = if (isActive) metrics.activeTabWidthPx else metrics.inactiveTabWidthPx
        return metrics.getItemWidth(tab.id, defaultWidth)
    }

    /**
     * One visible slot in the dock: a standalone tab, a folder indicator, or one member tab of
     * an expanded folder. [tabIds] lists every tab the slot stands for, so a collapsed folder is
     * a single slot that owns all of its member tabs.
     */
    internal data class DockSlot(
        val key: String,
        val folderId: String?,
        val isFolderItem: Boolean,
        val isCollapsedFolder: Boolean,
        val tabIds: List<String>,
        val startPx: Float,
        val widthPx: Float,
    ) {
        val endPx: Float get() = startPx + widthPx
    }

    /**
     * Builds the visible dock geometry in exactly the order [computeDockItems] renders it.
     *
     * Swap decisions must never target a tab that is hidden inside a collapsed folder, so every
     * decision is expressed against these slots instead of the flat tab list.
     */
    internal fun dockSlots(
        tabs: List<Tab>,
        metrics: TabStripLayoutMetrics,
    ): List<DockSlot> {
        val allFolders = foldersProvider()
        val slots = mutableListOf<DockSlot>()
        val emittedFolders = mutableSetOf<String>()
        var cursor = 0f

        for (tab in tabs) {
            val folderId = tab.folderId
            val folder = folderId?.let { id ->
                metrics.folderProvider?.invoke(id) ?: allFolders.firstOrNull { it.id == id }
            }

            if (folderId == null || folder == null) {
                val width = getTabWidth(tab, metrics)
                slots += DockSlot(
                    key = tab.id,
                    folderId = null,
                    isFolderItem = false,
                    isCollapsedFolder = false,
                    tabIds = listOf(tab.id),
                    startPx = cursor,
                    widthPx = width,
                )
                cursor += width + metrics.spacingPx
                continue
            }

            if (!emittedFolders.add(folderId)) continue

            val memberIds = tabs.filter { it.folderId == folderId }.map { it.id }
            val folderWidth = metrics.getItemWidth("folder_$folderId", metrics.inactiveTabWidthPx)
            slots += DockSlot(
                key = "folder_$folderId",
                folderId = folderId,
                isFolderItem = true,
                isCollapsedFolder = folder.isCollapsed,
                tabIds = memberIds,
                startPx = cursor,
                widthPx = folderWidth,
            )
            cursor += folderWidth + metrics.spacingPx

            if (folder.isCollapsed) continue

            for (memberId in memberIds) {
                val memberTab = tabs.first { it.id == memberId }
                val width = getTabWidth(memberTab, metrics)
                slots += DockSlot(
                    key = memberId,
                    folderId = folderId,
                    isFolderItem = false,
                    isCollapsedFolder = false,
                    tabIds = listOf(memberId),
                    startPx = cursor,
                    widthPx = width,
                )
                cursor += width + metrics.spacingPx
            }
        }

        return slots
    }

    internal fun getSlotStart(
        key: String,
        tabs: List<Tab>,
        metrics: TabStripLayoutMetrics,
    ): Float = dockSlots(tabs, metrics).firstOrNull { it.key == key }?.startPx ?: 0f

    internal fun getSlotIndex(
        key: String,
        tabs: List<Tab>,
        metrics: TabStripLayoutMetrics,
    ): Int = dockSlots(tabs, metrics).indexOfFirst { it.key == key }

    /** Visible region owned by [folderId]: the indenting indicator plus its rendered members. */
    private fun groupBounds(
        slots: List<DockSlot>,
        folderId: String,
    ): Pair<Float, Float>? {
        var start: Float? = null
        var end: Float? = null
        for (slot in slots) {
            if (slot.folderId != folderId) continue
            if (start == null) start = slot.startPx
            end = slot.endPx
        }
        val groupStart = start ?: return null
        return groupStart to (end ?: groupStart)
    }

    fun onDrag(
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

    private fun triggerSwapBump() {
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

    fun onDrag(
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

    fun onDrag(
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

    fun onDragEnd(
        isReleasedActive: Boolean,
        originalTabs: List<Tab>? = null,
        onConfirmVibration: () -> Unit = {},
        currentScale: Float? = null,
        currentAlpha: Float? = null,
    ) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        val currentHovered = hoveredFolderId
        if (currentHovered != null) {
            springLoadJob?.cancel()
            springLoadJob = null
            hoveredFolderId = null

            val draggedTab = currentTabs.firstOrNull { it.id == tabId }
            if (draggedTab != null) {
                val mutable = currentTabs.toMutableList()
                mutable.removeAll { it.id == tabId }
                val lastIdx = mutable.indexOfLast { it.folderId == currentHovered }
                val updatedTab = draggedTab.copy(folderId = currentHovered)
                if (lastIdx >= 0) {
                    mutable.add(lastIdx + 1, updatedTab)
                } else {
                    val allFolders = foldersProvider()
                    val folderIdx = allFolders.indexOfFirst { it.id == currentHovered }
                    val nextFolderWithTabs = allFolders.drop(folderIdx + 1).firstOrNull { f ->
                        mutable.any { it.folderId == f.id }
                    }
                    val insertIdx = if (nextFolderWithTabs != null) {
                        mutable.indexOfFirst { it.folderId == nextFolderWithTabs.id }
                    } else {
                        mutable.size
                    }
                    mutable.add(insertIdx, updatedTab)
                }
                currentTabs = mutable
            }
        }

        val springId = springLoadedFolderId
        if (springId != null) {
            springLoadedFolderId = null
            val droppedTab = currentTabs.firstOrNull { it.id == tabId }
            if (droppedTab != null && droppedTab.folderId != springId) {
                // Drop occurred inside the spring-opened folder before swapping with a member tab
                val mutable = currentTabs.toMutableList()
                val oldIdx = mutable.indexOfFirst { it.id == tabId }
                if (oldIdx >= 0) {
                    val removed = mutable.removeAt(oldIdx)
                    val updatedTab = removed.copy(folderId = springId)
                    val lastFolderTabIdx = mutable.indexOfLast { it.folderId == springId }
                    val insertIdx = if (lastFolderTabIdx >= 0) {
                        lastFolderTabIdx + 1
                    } else {
                        val allFolders = foldersProvider()
                        val folderIdx = allFolders.indexOfFirst { it.id == springId }
                        val nextFolderWithTabs = allFolders.drop(folderIdx + 1).firstOrNull { f ->
                            mutable.any { it.folderId == f.id }
                        }
                        if (nextFolderWithTabs != null) {
                            mutable.indexOfFirst { it.folderId == nextFolderWithTabs.id }
                        } else {
                            mutable.size
                        }
                    }
                    mutable.add(insertIdx.coerceIn(0, mutable.size), updatedTab)
                    currentTabs = mutable
                }
            }
        }

        if (isOverRemoveZone) {
            isDragging = false
            draggingTabId = null
            isOverRemoveZone = false
            resetSwapState()
            onConfirmVibration()

            val removedTab = currentTabs.firstOrNull { it.id == tabId }
            val remainingTabs = currentTabs.filter { it.id != tabId }

            if (originalTabs != null && currentTabs != originalTabs) {
                onReorder(remainingTabs)
            }

            removingTab = removedTab
            // Immediately drop tab from dock list so LazyRow closes the gap without leaving a ghost icon
            currentTabs = remainingTabs
            onRemoveTab(tabId)

            val startScale = currentScale ?: 0.60f
            val startAlpha = currentAlpha ?: 0.45f
            val startY = dragOffsetYPx
            removingVisualX = lastVisualXPx

            removeJob?.cancel()
            removeJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                removeScaleAnim.snapTo(startScale)
                removeAlphaAnim.snapTo(startAlpha)
                removeOffsetYAnim.snapTo(startY)

                val scaleJob = launch {
                    removeScaleAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                }
                val alphaJob = launch {
                    removeAlphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    )
                }
                val offsetJob = launch {
                    removeOffsetYAnim.animateTo(
                        targetValue = startY - 30f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                }

                scaleJob.join()
                alphaJob.join()
                offsetJob.join()

                removingTab = null
                removeScaleAnim.snapTo(1.0f)
                removeAlphaAnim.snapTo(1.0f)
                removeOffsetYAnim.snapTo(0f)
                dragOffsetPx = 0f
                dragOffsetYPx = 0f
                removeJob = null

                pendingTabs?.let {
                    currentTabs = it.filter { t -> t.id != tabId }
                    pendingTabs = null
                }
            }
            return
        }

        val finalOffset = dragOffsetPx
        val finalOffsetY = dragOffsetYPx
        val startScale = currentScale ?: (if (isReleasedActive) 1.05f else 1.08f)

        if (originalTabs != null && currentTabs != originalTabs) {
            onReorder(currentTabs)
        }

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        resetSwapState()

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleOffsetYAnim.snapTo(finalOffsetY)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val offsetYJob = launch {
                settleOffsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                )
            }
            offsetJob.join()
            offsetYJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleOffsetYAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun onDragCancel(isReleasedActive: Boolean) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        hoveredFolderId = null
        springLoadJob?.cancel()
        springLoadJob = null
        onSpringLoadedFolderExited()

        val finalOffset = dragOffsetPx
        val finalOffsetY = dragOffsetYPx
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        isOverRemoveZone = false
        resetSwapState()

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleOffsetYAnim.snapTo(finalOffsetY)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val offsetYJob = launch {
                settleOffsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                )
            }
            offsetJob.join()
            offsetYJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleOffsetYAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }
}

@Composable
internal fun rememberTabStripDragState(
    initialTabs: List<Tab>,
    onReorder: (List<Tab>) -> Unit,
    onRemoveTab: (String) -> Unit = {},
    foldersProvider: () -> List<TabFolder> = { emptyList() },
    onToggleFolderCollapse: (String) -> Unit = {},
    onHoverVibration: () -> Unit = {},
    onSpringLoadVibration: () -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): TabStripDragState {
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnRemoveTab by rememberUpdatedState(onRemoveTab)
    val currentFoldersProvider by rememberUpdatedState(foldersProvider)
    val currentOnToggleFolderCollapse by rememberUpdatedState(onToggleFolderCollapse)
    val currentOnHoverVibration by rememberUpdatedState(onHoverVibration)
    val currentOnSpringLoadVibration by rememberUpdatedState(onSpringLoadVibration)
    return remember {
        TabStripDragState(
            initialTabs = initialTabs,
            coroutineScope = coroutineScope,
            onReorder = { currentOnReorder(it) },
            onRemoveTab = { currentOnRemoveTab(it) },
            foldersProvider = { currentFoldersProvider() },
            onToggleFolderCollapse = { currentOnToggleFolderCollapse(it) },
            onHoverVibration = { currentOnHoverVibration() },
            onSpringLoadVibration = { currentOnSpringLoadVibration() },
        )
    }
}
