package com.jestk.browser.ui.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * State holder for the tabs-page drag gesture.
 *
 * Interaction model (Chrome mobile parity):
 * - The picked-up card leaves the grid; only a floating ghost follows the finger while its
 *   original cell stays vacant and shifts with the placeholder that marks the drop slot.
 * - Releasing the ghost in a card's near zone is a grouping action (folder creation, adding a
 *   tab to a folder, merging two folders). The target card stays still in that zone.
 * - Pushing the ghost past that card's far edge turns the gesture into a move: the placeholder
 *   jumps to the target slot and the grid reflows.
 * - Every outcome is committed once, on release. Nothing reaches the ViewModel mid-gesture.
 */
internal class TabOverviewReorderState(
    val coroutineScope: CoroutineScope,
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    var isDragging by mutableStateOf(false)
        private set

    /** Top-left of the floating ghost in grid viewport coordinates. */
    var ghostTopLeft by mutableStateOf(Offset.Zero)
        private set
    var ghostSize by mutableStateOf(IntSize.Zero)
        private set

    /** Card highlighted as the grouping target, null when the gesture currently means "move". */
    var hoverGroupingKey by mutableStateOf<String?>(null)
        private set

    var workingTabs by mutableStateOf<List<Tab>>(emptyList())
        private set
    var workingFolders by mutableStateOf<List<TabFolder>>(emptyList())
        private set

    private var initialTabs: List<Tab> = emptyList()
    private var initialFolders: List<TabFolder> = emptyList()
    private var grabOffset = Offset.Zero
    private var gridState: LazyGridState? = null
    private var vibration: VibrationFeedback? = null

    private var autoScrollJob: Job? = null
    private var autoScrollVelocity = 0f
    private var edgeZonePx = 0f
    private var maxScrollPxPerSecond = 0f

    private var lastPointer = Offset.Zero
    private var lastReorderTargetKey: String? = null

    fun onDragStart(
        key: String,
        initialPointer: Offset,
        currentPointer: Offset,
        sourceBounds: Rect,
        tabs: List<Tab>,
        folders: List<TabFolder>,
        gridState: LazyGridState,
        vibration: VibrationFeedback,
        edgeZonePx: Float,
        maxScrollPxPerSecond: Float,
    ) {
        draggedKey = key
        isDragging = true
        grabOffset = initialPointer - sourceBounds.topLeft
        ghostSize = IntSize(sourceBounds.width.toInt(), sourceBounds.height.toInt())
        ghostTopLeft = currentPointer - grabOffset
        lastPointer = currentPointer
        initialTabs = tabs
        initialFolders = folders
        workingTabs = tabs
        workingFolders = folders
        hoverGroupingKey = null
        lastReorderTargetKey = null
        this.gridState = gridState
        this.vibration = vibration
        this.edgeZonePx = edgeZonePx
        this.maxScrollPxPerSecond = maxScrollPxPerSecond
    }

    fun onDragMove(
        pointer: Offset,
        visibleItems: List<LazyGridItemInfo>,
    ) {
        if (!isDragging) return
        lastPointer = pointer
        ghostTopLeft = pointer - grabOffset
        updateTarget(pointer, visibleItems)
        updateAutoScroll()
    }

    fun onDragEnd(
        onEvent: (BrowserUiEvent) -> Unit,
        vibration: VibrationFeedback,
    ) {
        val dragged = draggedKey
        val groupingTarget = hoverGroupingKey
        try {
            if (dragged == null) return
            if (groupingTarget != null) {
                vibration.tick()
                commitGrouping(dragged, groupingTarget, onEvent)
                return
            }

            if (dragged.startsWith("tab_")) {
                val tabId = dragged.removePrefix("tab_")
                val initialTab = initialTabs.find { it.id == tabId }
                val finalTab = workingTabs.find { it.id == tabId }
                if (initialTab?.folderId != null && finalTab?.folderId == null) {
                    onEvent(BrowserUiEvent.RemoveTabFromFolder(tabId))
                } else if (initialTab?.folderId == null && finalTab?.folderId != null) {
                    onEvent(BrowserUiEvent.AddTabToFolder(tabId = tabId, folderId = finalTab.folderId!!))
                }
            }

            if (workingTabs != initialTabs || workingFolders != initialFolders) {
                vibration.tick()
                onEvent(BrowserUiEvent.ReorderTabs(orderedTabs = workingTabs, orderedFolders = workingFolders))
            }
        } finally {
            reset()
        }
    }

    fun onDragCancel() {
        reset()
    }

    private fun updateTarget(
        pointer: Offset,
        visibleItems: List<LazyGridItemInfo>,
    ) {
        val dragged = draggedKey ?: return
        val feedback = vibration ?: return
        val ghost = Rect(ghostTopLeft, Size(ghostSize.width.toFloat(), ghostSize.height.toFloat()))
        val ghostCenter = ghost.center

        val gapRect =
            visibleItems.firstOrNull { it.key.toString() == dragged }?.let { itemRect(it) }

        val target =
            visibleItems.firstOrNull { item ->
                val key = item.key.toString()
                key != dragged &&
                    !key.startsWith("folder_spacer_") &&
                    (itemRect(item).contains(ghostCenter) || itemRect(item).contains(pointer))
            } ?: return clearGrouping()

        val targetKey = target.key.toString()
        val targetRect = itemRect(target)
        val isGroupable = isEligibleForGrouping(dragged, targetKey, workingTabs)

        if (isGroupable) {
            val halfW = targetRect.width / 2f
            val halfH = targetRect.height / 2f
            val cx = targetRect.left + halfW
            val cy = targetRect.top + halfH
            val isCenterZone =
                abs(pointer.x - cx) <= halfW * 0.7f &&
                    abs(pointer.y - cy) <= halfH * 0.7f

            if (isCenterZone) {
                if (hoverGroupingKey != targetKey) {
                    hoverGroupingKey = targetKey
                    feedback.tick()
                }
                return
            }

            val referenceCenter = gapRect?.center ?: ghostCenter
            val dx = pointer.x - referenceCenter.x
            val dy = pointer.y - referenceCenter.y
            val hasMovedPast =
                if (abs(dx) >= abs(dy)) {
                    if (dx > 0) pointer.x > cx + halfW * 0.4f else pointer.x < cx - halfW * 0.4f
                } else {
                    if (dy > 0) pointer.y > cy + halfH * 0.4f else pointer.y < cy - halfH * 0.4f
                }

            if (!hasMovedPast) {
                return
            }
        }

        if (targetKey != lastReorderTargetKey) {
            lastReorderTargetKey = targetKey
            clearGrouping()
            val (reorderedTabs, reorderedFolders) =
                reorderOverviewGrid(
                    tabs = workingTabs,
                    folders = workingFolders,
                    draggedKey = dragged,
                    targetKey = targetKey,
                )
            workingTabs = reorderedTabs
            workingFolders = reorderedFolders
        }
    }

    private fun clearGrouping() {
        hoverGroupingKey = null
    }

    private fun updateAutoScroll() {
        val state = gridState ?: return
        val info = state.layoutInfo
        val ghostCenterY = ghostTopLeft.y + ghostSize.height / 2f
        val viewportTop = info.viewportStartOffset.toFloat()
        val viewportBottom = info.viewportEndOffset.toFloat()
        val zone = edgeZonePx.coerceAtLeast(1f)

        autoScrollVelocity =
            when {
                ghostCenterY < viewportTop + zone ->
                    -maxScrollPxPerSecond * ((viewportTop + zone - ghostCenterY) / zone).coerceIn(0f, 1f)
                ghostCenterY > viewportBottom - zone ->
                    maxScrollPxPerSecond * ((ghostCenterY - (viewportBottom - zone)) / zone).coerceIn(0f, 1f)
                else -> 0f
            }

        if (autoScrollVelocity == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }
        if (autoScrollJob?.isActive == true) return

        autoScrollJob =
            coroutineScope.launch {
                while (isActive && isDragging && autoScrollVelocity != 0f) {
                    delay(AUTO_SCROLL_FRAME_MS)
                    val grid = gridState ?: break
                    grid.scrollBy(autoScrollVelocity * AUTO_SCROLL_FRAME_MS / 1000f)
                    updateTarget(lastPointer, grid.layoutInfo.visibleItemsInfo)
                }
            }
    }

    private fun commitGrouping(
        draggedKey: String,
        targetKey: String,
        onEvent: (BrowserUiEvent) -> Unit,
    ) {
        val draggedTabId = if (draggedKey.startsWith("tab_")) draggedKey.removePrefix("tab_") else null
        val draggedFolderId = folderIdOf(draggedKey)
        val targetTabId = if (targetKey.startsWith("tab_")) targetKey.removePrefix("tab_") else null
        val targetFolderId = folderIdOf(targetKey)

        if (draggedTabId != null) {
            val folderId =
                targetFolderId
                    ?: targetTabId?.let { id ->
                        (workingTabs.find { it.id == id } ?: initialTabs.find { it.id == id })?.folderId
                    }
            if (folderId != null) {
                onEvent(BrowserUiEvent.AddTabToFolder(tabId = draggedTabId, folderId = folderId))
            } else if (targetTabId != null) {
                onEvent(BrowserUiEvent.CreateFolderFromTabs(tabId1 = draggedTabId, tabId2 = targetTabId))
            }
            return
        }

        if (draggedFolderId != null) {
            if (targetFolderId != null) {
                val (mergedTabs, mergedFolders) =
                    mergeFolderIntoFolder(
                        tabs = workingTabs,
                        folders = workingFolders,
                        sourceFolderId = draggedFolderId,
                        targetFolderId = targetFolderId,
                    )
                onEvent(BrowserUiEvent.ReorderTabs(orderedTabs = mergedTabs, orderedFolders = mergedFolders))
            } else if (targetTabId != null) {
                onEvent(BrowserUiEvent.AddTabToFolder(tabId = targetTabId, folderId = draggedFolderId))
            }
        }
    }

    /**
     * Grouping is only offered where the target can actually absorb the dragged item, so the
     * near zone of an ineligible target means "hold on, keep moving" rather than an action.
     */
    private fun isEligibleForGrouping(
        draggedKey: String,
        targetKey: String,
        tabs: List<Tab>,
    ): Boolean {
        if (targetKey == draggedKey) return false

        val draggedFolderId = folderIdOf(draggedKey)
        if (draggedFolderId != null) {
            val targetFolderId = folderIdOf(targetKey)
            return targetFolderId != null && targetFolderId != draggedFolderId
        }

        if (!draggedKey.startsWith("tab_")) return false
        val draggedTab = tabs.find { it.id == draggedKey.removePrefix("tab_") } ?: return false

        val targetFolderId = folderIdOf(targetKey)
        if (targetFolderId != null) return draggedTab.folderId != targetFolderId

        val targetTab = tabs.find { "tab_${it.id}" == targetKey } ?: return false
        return if (targetTab.folderId != null) {
            draggedTab.folderId != targetTab.folderId
        } else {
            draggedTab.folderId == null
        }
    }

    private fun itemRect(item: LazyGridItemInfo): Rect =
        Rect(
            offset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
            size = Size(item.size.width.toFloat(), item.size.height.toFloat()),
        )

    private fun reset() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollVelocity = 0f
        draggedKey = null
        isDragging = false
        ghostTopLeft = Offset.Zero
        ghostSize = IntSize.Zero
        hoverGroupingKey = null
        workingTabs = emptyList()
        workingFolders = emptyList()
        initialTabs = emptyList()
        initialFolders = emptyList()
        lastReorderTargetKey = null
        grabOffset = Offset.Zero
        gridState = null
        vibration = null
    }

    private companion object {
        const val AUTO_SCROLL_FRAME_MS = 16L
    }
}

private fun folderIdOf(key: String): String? =
    when {
        key.startsWith("folder_header_") -> key.removePrefix("folder_header_")
        key.startsWith("folder_") && !key.startsWith("folder_spacer_") -> key.removePrefix("folder_")
        else -> null
    }

/**
 * Modifier hosting the long-press and drag-reorder loop on the stable grid container (Lesson #3).
 */
@Composable
internal fun rememberTabOverviewReorderModifier(
    gridState: LazyGridState,
    tabs: List<Tab>,
    folders: List<TabFolder>,
    onEvent: (BrowserUiEvent) -> Unit,
    reorderState: TabOverviewReorderState,
): Modifier {
    val currentTabs by rememberUpdatedState(tabs)
    val currentFolders by rememberUpdatedState(folders)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val vibration = LocalVibrationFeedback.current
    val currentVibration by rememberUpdatedState(vibration)
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 8.dp.toPx() }
    val edgeZonePx = with(density) { 56.dp.toPx() }
    val maxScrollPxPerSecond = with(density) { 700.dp.toPx() }

    return Modifier.pointerInput(touchSlopPx, edgeZonePx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val initialDownPos = down.position
            val downItem =
                gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    val x = down.position.x.toInt()
                    val y = down.position.y.toInt()
                    x >= item.offset.x && x <= item.offset.x + item.size.width &&
                        y >= item.offset.y && y <= item.offset.y + item.size.height
                } ?: return@awaitEachGesture

            val downKey = downItem.key.toString()
            if (downKey.startsWith("folder_spacer_")) {
                return@awaitEachGesture
            }

            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            vibration.confirm()

            val pointerId = longPress.id
            var totalDragX = 0f
            var totalDragY = 0f
            var isDragging = false
            var currentPos = longPress.position

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed) {
                        if (change != null && change.changedToUp()) {
                            change.consume()
                        }
                        break
                    }

                    val delta = change.positionChange()
                    totalDragX += delta.x
                    totalDragY += delta.y
                    change.consume()
                    currentPos = change.position

                    if (!isDragging && (abs(totalDragX) > touchSlopPx || abs(totalDragY) > touchSlopPx)) {
                        isDragging = true
                        reorderState.onDragStart(
                            key = downKey,
                            initialPointer = initialDownPos,
                            currentPointer = currentPos,
                            sourceBounds =
                                Rect(
                                    offset = Offset(downItem.offset.x.toFloat(), downItem.offset.y.toFloat()),
                                    size = Size(downItem.size.width.toFloat(), downItem.size.height.toFloat()),
                                ),
                            tabs = currentTabs,
                            folders = currentFolders,
                            gridState = gridState,
                            vibration = currentVibration,
                            edgeZonePx = edgeZonePx,
                            maxScrollPxPerSecond = maxScrollPxPerSecond,
                        )
                    }

                    if (isDragging) {
                        reorderState.onDragMove(
                            pointer = currentPos,
                            visibleItems = gridState.layoutInfo.visibleItemsInfo,
                        )
                    }
                }

                if (!isDragging) {
                    // Stationary long press: open the folder edit sheet for folders.
                    val folderId = folderIdOf(downKey)
                    if (folderId != null) {
                        currentOnEvent(BrowserUiEvent.OpenEditFolderSheet(folderId))
                    }
                } else {
                    reorderState.onDragEnd(
                        onEvent = currentOnEvent,
                        vibration = currentVibration,
                    )
                }
            } catch (e: Exception) {
                if (isDragging) {
                    reorderState.onDragCancel()
                }
            }
        }
    }
}
