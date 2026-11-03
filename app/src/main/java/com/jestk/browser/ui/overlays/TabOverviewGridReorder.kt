package com.jestk.browser.ui.overlays

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder

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
 * Pure calculation that folds [sourceFolderId] into [targetFolderId].
 *
 * The target folder keeps its own title and color, the source members are appended in their
 * existing order right after the target's members, and the emptied source folder disappears.
 */
internal fun mergeFolderIntoFolder(
    tabs: List<Tab>,
    folders: List<TabFolder>,
    sourceFolderId: String,
    targetFolderId: String,
): Pair<List<Tab>, List<TabFolder>> {
    if (sourceFolderId == targetFolderId) return Pair(tabs, folders)

    val units = tabsAndFoldersToUnits(tabs, folders).toMutableList()
    val sourceUnit =
        units.firstOrNull { it is OverviewUnit.FolderUnit && it.folder.id == sourceFolderId }
            as? OverviewUnit.FolderUnit ?: return Pair(tabs, folders)
    val targetIndex =
        units.indexOfFirst { it is OverviewUnit.FolderUnit && it.folder.id == targetFolderId }
    if (targetIndex == -1) return Pair(tabs, folders)

    units.remove(sourceUnit)
    val targetUnit = units[units.indexOfFirst { it is OverviewUnit.FolderUnit && it.folder.id == targetFolderId }]
        as OverviewUnit.FolderUnit
    val mergedMembers = targetUnit.memberTabs + sourceUnit.memberTabs.map { it.copy(folderId = targetFolderId) }
    units[units.indexOf(targetUnit)] = targetUnit.copy(memberTabs = mergedMembers)
    return unitsToTabsAndFolders(units)
}

