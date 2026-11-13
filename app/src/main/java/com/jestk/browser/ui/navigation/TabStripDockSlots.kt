package com.jestk.browser.ui.navigation

import com.jestk.browser.domain.model.Tab

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
internal fun TabStripDragState.dockSlots(
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

internal fun TabStripDragState.getSlotStart(
    key: String,
    tabs: List<Tab>,
    metrics: TabStripLayoutMetrics,
): Float = dockSlots(tabs, metrics).firstOrNull { it.key == key }?.startPx ?: 0f

internal fun TabStripDragState.getSlotIndex(
    key: String,
    tabs: List<Tab>,
    metrics: TabStripLayoutMetrics,
): Int = dockSlots(tabs, metrics).indexOfFirst { it.key == key }

/** Visible region owned by [folderId]: the indenting indicator plus its rendered members. */
internal fun groupBounds(
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
