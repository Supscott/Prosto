package com.jestk.browser.ui.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Top-level projection unit for grid reordering.
 */
internal sealed interface OverviewUnit {
    data class StandaloneTab(val tab: Tab) : OverviewUnit
    data class FolderUnit(val folder: TabFolder, val memberTabs: List<Tab>) : OverviewUnit
}

/**
 * Decomposes linear [tabs] and [folders] into contiguous top-level [OverviewUnit] blocks.
 */
internal fun tabsAndFoldersToUnits(
    tabs: List<Tab>,
    folders: List<TabFolder>,
): List<OverviewUnit> {
    val folderMap = folders.associateBy { it.id }
    val seenFolders = mutableSetOf<String>()
    val units = mutableListOf<OverviewUnit>()

    for (tab in tabs) {
        val fId = tab.folderId
        if (fId == null) {
            units.add(OverviewUnit.StandaloneTab(tab))
        } else if (fId !in seenFolders) {
            seenFolders.add(fId)
            val folder = folderMap[fId] ?: TabFolder(id = fId, title = "", color = 0)
            val members = tabs.filter { it.folderId == fId }
            units.add(OverviewUnit.FolderUnit(folder, members))
        }
    }

    for (folder in folders) {
        if (folder.id !in seenFolders) {
            units.add(OverviewUnit.FolderUnit(folder, emptyList()))
        }
    }
    return units
}

/**
 * Flattens top-level [OverviewUnit] blocks back into synchronized lists of [Tab] and [TabFolder].
 */
internal fun unitsToTabsAndFolders(
    units: List<OverviewUnit>,
): Pair<List<Tab>, List<TabFolder>> {
    val tabs = mutableListOf<Tab>()
    val folders = mutableListOf<TabFolder>()
    var pos = 0

    for (unit in units) {
        when (unit) {
            is OverviewUnit.StandaloneTab -> {
                tabs.add(unit.tab.copy(folderId = null))
            }
            is OverviewUnit.FolderUnit -> {
                if (unit.memberTabs.isNotEmpty()) {
                    folders.add(unit.folder.copy(position = pos++))
                    tabs.addAll(unit.memberTabs.map { it.copy(folderId = unit.folder.id) })
                }
            }
        }
    }
    return Pair(tabs, folders)
}

/**
 * Pure calculation that moves [draggedKey] to [targetKey]'s slot in [tabs] and [folders].
 *
 * Supports:
 * - Standalone tab to standalone tab reorder.
 * - Folder card/header reorder among tabs and folders.
 * - Internal reorder of tabs within an open folder.
 * - Ungrouping a tab by dragging it out of an open folder to an external slot.
 * - Adding a tab to an open folder by dragging it into the open folder section.
 */
internal fun reorderOverviewGrid(
    tabs: List<Tab>,
    folders: List<TabFolder>,
    draggedKey: String,
    targetKey: String,
): Pair<List<Tab>, List<TabFolder>> {
    if (draggedKey == targetKey) return Pair(tabs, folders)

    val units = tabsAndFoldersToUnits(tabs, folders).toMutableList()

    // 1. Dragging a folder (collapsed card or expanded header)
    if (draggedKey.startsWith("folder_header_") || draggedKey.startsWith("folder_")) {
        val draggedFolderId = if (draggedKey.startsWith("folder_header_")) {
            draggedKey.removePrefix("folder_header_")
        } else {
            draggedKey.removePrefix("folder_")
        }

        val draggedUnitIndex = units.indexOfFirst { it is OverviewUnit.FolderUnit && it.folder.id == draggedFolderId }
        val targetUnitIndex = findTargetUnitIndex(units, targetKey)

        if (draggedUnitIndex == -1 || targetUnitIndex == -1) return Pair(tabs, folders)
        val draggedUnit = units.removeAt(draggedUnitIndex)
        units.add(targetUnitIndex.coerceIn(0, units.size), draggedUnit)
        return unitsToTabsAndFolders(units)
    }

    // 2. Dragging a tab
    if (draggedKey.startsWith("tab_")) {
        val draggedTabId = draggedKey.removePrefix("tab_")
        val draggedTab = tabs.find { it.id == draggedTabId } ?: return Pair(tabs, folders)
        val sourceFolderId = draggedTab.folderId

        // Case A: Dragged tab originates from a folder
        if (sourceFolderId != null) {
            val sourceUnitIndex = units.indexOfFirst { it is OverviewUnit.FolderUnit && it.folder.id == sourceFolderId }
            if (sourceUnitIndex == -1) return Pair(tabs, folders)
            val sourceFolderUnit = units[sourceUnitIndex] as OverviewUnit.FolderUnit

            // Subcase A1: Target is within the same folder
            val isTargetInSameFolder = targetKey.startsWith("folder_spacer_${sourceFolderId}_") ||
                sourceFolderUnit.memberTabs.any { "tab_${it.id}" == targetKey }

            if (isTargetInSameFolder) {
                val currentMembers = sourceFolderUnit.memberTabs.toMutableList()
                val fromIndex = currentMembers.indexOfFirst { it.id == draggedTabId }
                val toIndex = if (targetKey.startsWith("folder_spacer_")) {
                    currentMembers.size - 1
                } else {
                    val targetTabId = targetKey.removePrefix("tab_")
                    currentMembers.indexOfFirst { it.id == targetTabId }
                }

                if (fromIndex != -1 && toIndex != -1) {
                    val movedTab = currentMembers.removeAt(fromIndex)
                    currentMembers.add(toIndex.coerceIn(0, currentMembers.size), movedTab)
                    units[sourceUnitIndex] = sourceFolderUnit.copy(memberTabs = currentMembers)
                    return unitsToTabsAndFolders(units)
                }
                return Pair(tabs, folders)
            }

            // Subcase A2: Target is outside the source folder -> tab is ungrouped!
            // Subcase A2: Target is outside the source folder -> tab is ungrouped or moves to another folder
            val isTargetInAnotherFolder = units.any { unit ->
                unit is OverviewUnit.FolderUnit && unit != sourceFolderUnit && (
                    unit.memberTabs.any { "tab_${it.id}" == targetKey } ||
                        targetKey.startsWith("folder_spacer_${unit.folder.id}_")
                )
            }

            if (isTargetInAnotherFolder) {
                val updatedSourceMembers = sourceFolderUnit.memberTabs.filter { it.id != draggedTabId }
                if (updatedSourceMembers.isEmpty()) {
                    units.removeAt(sourceUnitIndex)
                } else {
                    units[sourceUnitIndex] = sourceFolderUnit.copy(memberTabs = updatedSourceMembers)
                }
                insertTabIntoFolder(units, draggedTab, targetKey)
                return unitsToTabsAndFolders(units)
            }

            // Otherwise, tab becomes a StandaloneTab at the target unit index
            val targetUnitIdx = findTargetUnitIndex(units, targetKey)

            val updatedSourceMembers = sourceFolderUnit.memberTabs.filter { it.id != draggedTabId }
            if (updatedSourceMembers.isEmpty()) {
                units.removeAt(sourceUnitIndex)
            } else {
                units[sourceUnitIndex] = sourceFolderUnit.copy(memberTabs = updatedSourceMembers)
            }

            val standaloneTab = OverviewUnit.StandaloneTab(draggedTab.copy(folderId = null))
            if (targetUnitIdx != -1) {
                units.add(targetUnitIdx.coerceIn(0, units.size), standaloneTab)
            } else {
                units.add(standaloneTab)
            }
            return unitsToTabsAndFolders(units)
        }

        // Case B: Dragged tab is a StandaloneTab
        val sourceUnitIndex = units.indexOfFirst { it is OverviewUnit.StandaloneTab && it.tab.id == draggedTabId }
        if (sourceUnitIndex == -1) return Pair(tabs, folders)

        val isTargetInFolder = units.any { unit ->
            unit is OverviewUnit.FolderUnit && (
                unit.memberTabs.any { "tab_${it.id}" == targetKey } ||
                    targetKey.startsWith("folder_spacer_${unit.folder.id}_")
            )
        }

        if (isTargetInFolder) {
            val standaloneUnit = units.removeAt(sourceUnitIndex) as OverviewUnit.StandaloneTab
            insertTabIntoFolder(units, standaloneUnit.tab, targetKey)
            return unitsToTabsAndFolders(units)
        }

        // Subcase B2: Target is another StandaloneTab or a folder card/header
        val targetUnitIdx = findTargetUnitIndex(units, targetKey)
        if (targetUnitIdx != -1) {
            val standaloneUnit = units.removeAt(sourceUnitIndex) as OverviewUnit.StandaloneTab
            units.add(targetUnitIdx.coerceIn(0, units.size), standaloneUnit)
            return unitsToTabsAndFolders(units)
        }
    }

    return Pair(tabs, folders)
}

private fun findTargetUnitIndex(units: List<OverviewUnit>, targetKey: String): Int {
    return units.indexOfFirst { unit ->
        when (unit) {
            is OverviewUnit.StandaloneTab -> "tab_${unit.tab.id}" == targetKey
            is OverviewUnit.FolderUnit -> {
                "folder_${unit.folder.id}" == targetKey ||
                    "folder_header_${unit.folder.id}" == targetKey ||
                    unit.memberTabs.any { "tab_${it.id}" == targetKey } ||
                    targetKey.startsWith("folder_spacer_${unit.folder.id}_")
            }
        }
    }
}

private fun insertTabIntoFolder(
    units: MutableList<OverviewUnit>,
    tab: Tab,
    targetKey: String,
) {
    val targetFolderUnit = units.firstOrNull { it is OverviewUnit.FolderUnit && (
        it.memberTabs.any { m -> "tab_${m.id}" == targetKey } ||
            targetKey.startsWith("folder_spacer_${it.folder.id}_")
    ) } as? OverviewUnit.FolderUnit ?: return

    val actualTargetIdx = units.indexOf(targetFolderUnit)
    val newMembers = targetFolderUnit.memberTabs.toMutableList()
    val insertIdx = if (targetKey.startsWith("folder_spacer_")) {
        newMembers.size
    } else {
        val targetTabId = targetKey.removePrefix("tab_")
        val idx = newMembers.indexOfFirst { it.id == targetTabId }
        if (idx != -1) idx else newMembers.size
    }
    newMembers.add(insertIdx.coerceIn(0, newMembers.size), tab.copy(folderId = targetFolderUnit.folder.id))
    units[actualTargetIdx] = targetFolderUnit.copy(memberTabs = newMembers)
}

/**
 * State holder for direct-manipulation drag reordering and hover grouping in [TabOverviewGrid].
 */
internal class TabOverviewReorderState(
    val coroutineScope: CoroutineScope,
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    var dragProxyPosition by mutableStateOf(Offset.Zero)
        private set
    var isDragging by mutableStateOf(false)
        private set

    var isHoverGrouping by mutableStateOf(false)
        private set
    var hoverGroupingKey by mutableStateOf<String?>(null)
        private set

    var workingTabs by mutableStateOf<List<Tab>>(emptyList())
        private set
    var workingFolders by mutableStateOf<List<TabFolder>>(emptyList())
        private set

    private var initialTabs: List<Tab> = emptyList()
    private var initialFolders: List<TabFolder> = emptyList()

    private var currentHoverCandidateKey: String? = null
    private var lastHoverTargetKey: String? = null
    private var hoverJob: Job? = null

    fun onDragStart(
        key: String,
        initialPosition: Offset,
        tabs: List<Tab>,
        folders: List<TabFolder>,
    ) {
        draggedKey = key
        dragProxyPosition = initialPosition
        isDragging = true
        initialTabs = tabs
        initialFolders = folders
        workingTabs = tabs
        workingFolders = folders
        isHoverGrouping = false
        hoverGroupingKey = null
        currentHoverCandidateKey = null
        lastHoverTargetKey = null
    }

    fun onDragMove(
        position: Offset,
        visibleItems: List<LazyGridItemInfo>,
        vibration: VibrationFeedback,
    ) {
        if (!isDragging || draggedKey == null) return
        dragProxyPosition = position

        val hovered = visibleItems.firstOrNull { item ->
            val x = position.x.toInt()
            val y = position.y.toInt()
            x >= item.offset.x && x <= item.offset.x + item.size.width &&
                y >= item.offset.y && y <= item.offset.y + item.size.height
        }

        val targetKey = hovered?.key?.toString()
        val currentDraggedKey = draggedKey ?: return

        if (hovered != null && targetKey != null && targetKey != currentDraggedKey) {
            val currentTabsList = workingTabs.ifEmpty { initialTabs }
            val canGroup = isEligibleForGrouping(currentDraggedKey, targetKey, currentTabsList)

            if (canGroup) {
                val cx = hovered.offset.x + hovered.size.width / 2f
                val cy = hovered.offset.y + hovered.size.height / 2f
                val halfW = hovered.size.width / 2f
                val halfH = hovered.size.height / 2f

                // Center grouping zone: inner 65% of the target card.
                // Target card NEVER moves or swaps beneath the finger in this zone.
                val isCenterZone = abs(position.x - cx) <= halfW * 0.65f &&
                    abs(position.y - cy) <= halfH * 0.65f

                if (isCenterZone) {
                    lastHoverTargetKey = targetKey
                    if (targetKey != hoverGroupingKey) {
                        hoverGroupingKey = targetKey
                        isHoverGrouping = true
                        vibration.tick()
                    }
                } else {
                    // Outer margin: only reorder if pointer has crossed past center to the opposite edge
                    val currentDraggedIdx = workingTabs.indexOfFirst { "tab_${it.id}" == currentDraggedKey }
                    val targetIdx = workingTabs.indexOfFirst { "tab_${it.id}" == targetKey }

                    val hasMovedPast = if (targetIdx > currentDraggedIdx) {
                        position.x > cx + halfW * 0.45f || position.y > cy + halfH * 0.45f
                    } else if (targetIdx != -1 && currentDraggedIdx != -1) {
                        position.x < cx - halfW * 0.45f || position.y < cy - halfH * 0.45f
                    } else {
                        false
                    }

                    if (hasMovedPast) {
                        if (isHoverGrouping || hoverGroupingKey != null) {
                            isHoverGrouping = false
                            hoverGroupingKey = null
                        }
                        lastHoverTargetKey = targetKey
                        val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
                            tabs = workingTabs,
                            folders = workingFolders,
                            draggedKey = currentDraggedKey,
                            targetKey = targetKey,
                        )
                        workingTabs = reorderedTabs
                        workingFolders = reorderedFolders
                    } else {
                        // Entering target card from the origin side: lock grouping, no swap
                        lastHoverTargetKey = targetKey
                        if (targetKey != hoverGroupingKey) {
                            hoverGroupingKey = targetKey
                            isHoverGrouping = true
                        }
                    }
                }
            } else {
                // Non-groupable item (e.g. dragging a folder or spacer): live reorder slots
                if (isHoverGrouping || hoverGroupingKey != null) {
                    isHoverGrouping = false
                    hoverGroupingKey = null
                }
                lastHoverTargetKey = targetKey

                val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
                    tabs = workingTabs,
                    folders = workingFolders,
                    draggedKey = currentDraggedKey,
                    targetKey = targetKey,
                )
                workingTabs = reorderedTabs
                workingFolders = reorderedFolders
            }
        } else if (hovered == null && visibleItems.isNotEmpty()) {
            val closest = visibleItems.filter { !it.key.toString().startsWith("folder_spacer_") }
                .minByOrNull { item ->
                    val cx = item.offset.x + item.size.width / 2f
                    val cy = item.offset.y + item.size.height / 2f
                    val dx = position.x - cx
                    val dy = position.y - cy
                    dx * dx + dy * dy
                }

            val closestKey = closest?.key?.toString()
            if (closest != null && closestKey != null && closestKey != currentDraggedKey) {
                val cx = closest.offset.x + closest.size.width / 2f
                val cy = closest.offset.y + closest.size.height / 2f

                if (currentDraggedKey.startsWith("tab_")) {
                    // For tabs: only reorder into empty gap or past-end if pointer has moved PAST closest's center
                    val currentDraggedIdx = workingTabs.indexOfFirst { "tab_${it.id}" == currentDraggedKey }
                    val targetIdx = workingTabs.indexOfFirst { "tab_${it.id}" == closestKey }

                    val hasMovedPast = if (targetIdx > currentDraggedIdx) {
                        position.x > cx || position.y > cy
                    } else if (targetIdx != -1 && currentDraggedIdx != -1) {
                        position.x < cx || position.y < cy
                    } else {
                        true
                    }

                    if (hasMovedPast) {
                        if (isHoverGrouping || hoverGroupingKey != null) {
                            isHoverGrouping = false
                            hoverGroupingKey = null
                        }
                        lastHoverTargetKey = closestKey
                        val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
                            tabs = workingTabs,
                            folders = workingFolders,
                            draggedKey = currentDraggedKey,
                            targetKey = closestKey,
                        )
                        workingTabs = reorderedTabs
                        workingFolders = reorderedFolders
                    }
                } else {
                    // For folders: reorder smoothly anywhere in the grid
                    if (isHoverGrouping || hoverGroupingKey != null) {
                        isHoverGrouping = false
                        hoverGroupingKey = null
                    }
                    lastHoverTargetKey = closestKey
                    val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
                        tabs = workingTabs,
                        folders = workingFolders,
                        draggedKey = currentDraggedKey,
                        targetKey = closestKey,
                    )
                    workingTabs = reorderedTabs
                    workingFolders = reorderedFolders
                }
            }
        }
    }

    fun onDragEnd(
        onEvent: (BrowserUiEvent) -> Unit,
        vibration: VibrationFeedback,
    ) {
        val currentDraggedKey = draggedKey
        val targetGroupingKey = hoverGroupingKey

        try {
            if (isHoverGrouping && targetGroupingKey != null && currentDraggedKey != null && currentDraggedKey.startsWith("tab_")) {
                val tabId = currentDraggedKey.removePrefix("tab_")
                if (targetGroupingKey.startsWith("folder_header_")) {
                    val folderId = targetGroupingKey.removePrefix("folder_header_")
                    vibration.tick()
                    onEvent(BrowserUiEvent.AddTabToFolder(tabId = tabId, folderId = folderId))
                } else if (targetGroupingKey.startsWith("folder_")) {
                    val folderId = targetGroupingKey.removePrefix("folder_")
                    vibration.tick()
                    onEvent(BrowserUiEvent.AddTabToFolder(tabId = tabId, folderId = folderId))
                } else if (targetGroupingKey.startsWith("tab_")) {
                    val targetTabId = targetGroupingKey.removePrefix("tab_")
                    val targetTab = workingTabs.find { it.id == targetTabId } ?: initialTabs.find { it.id == targetTabId }
                    if (targetTab?.folderId != null) {
                        vibration.tick()
                        onEvent(BrowserUiEvent.AddTabToFolder(tabId = tabId, folderId = targetTab.folderId))
                    } else {
                        vibration.tick()
                        onEvent(BrowserUiEvent.CreateFolderFromTabs(tabId1 = tabId, tabId2 = targetTabId))
                    }
                }
            } else {
                // Reorder or cross-boundary drop
                if (currentDraggedKey != null && currentDraggedKey.startsWith("tab_")) {
                    val tabId = currentDraggedKey.removePrefix("tab_")
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
            }
        } finally {
            reset()
        }
    }

    fun onDragCancel() {
        reset()
    }

    private fun reset() {
        draggedKey = null
        dragProxyPosition = Offset.Zero
        isDragging = false
        isHoverGrouping = false
        hoverGroupingKey = null
        currentHoverCandidateKey = null
        lastHoverTargetKey = null
        workingTabs = emptyList()
        workingFolders = emptyList()
        initialTabs = emptyList()
        initialFolders = emptyList()
    }

    private fun isEligibleForGrouping(
        draggedKey: String,
        targetKey: String?,
        tabs: List<Tab>,
    ): Boolean {
        if (!draggedKey.startsWith("tab_") || targetKey == null || targetKey == draggedKey) return false
        if (targetKey.startsWith("folder_spacer_")) return false

        val draggedTabId = draggedKey.removePrefix("tab_")
        val draggedTab = tabs.find { it.id == draggedTabId } ?: return false

        if (targetKey.startsWith("folder_header_") || targetKey.startsWith("folder_")) {
            val folderId = if (targetKey.startsWith("folder_header_")) {
                targetKey.removePrefix("folder_header_")
            } else {
                targetKey.removePrefix("folder_")
            }
            // Cannot group into the folder it already belongs to
            return draggedTab.folderId != folderId
        }

        if (targetKey.startsWith("tab_")) {
            val targetTabId = targetKey.removePrefix("tab_")
            val targetTab = tabs.find { it.id == targetTabId } ?: return false
            if (targetTab.folderId != null) {
                // Can group into target tab's folder as long as it's a different folder
                return draggedTab.folderId != targetTab.folderId
            }
            // If target tab has no folder, we can only create a folder if dragged tab ALSO has no folder
            return draggedTab.folderId == null
        }

        return false
    }
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
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 8.dp.toPx() }

    return Modifier.pointerInput(touchSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
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
                            initialPosition = currentPos,
                            tabs = currentTabs,
                            folders = currentFolders,
                        )
                    }

                    if (isDragging) {
                        reorderState.onDragMove(
                            position = currentPos,
                            visibleItems = gridState.layoutInfo.visibleItemsInfo,
                            vibration = vibration,
                        )
                    }
                }

                if (!isDragging) {
                    // Stationary long press: open folder edit bottom sheet if folder header or card
                    if (downKey.startsWith("folder_header_")) {
                        val folderId = downKey.removePrefix("folder_header_")
                        currentOnEvent(BrowserUiEvent.OpenEditFolderSheet(folderId))
                    } else if (downKey.startsWith("folder_")) {
                        val folderId = downKey.removePrefix("folder_")
                        currentOnEvent(BrowserUiEvent.OpenEditFolderSheet(folderId))
                    }
                } else {
                    reorderState.onDragEnd(
                        onEvent = currentOnEvent,
                        vibration = vibration,
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
