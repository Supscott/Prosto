package com.jestk.browser.ui.navigation

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder

/**
 * Represents an item rendered in [TabStripRow]'s LazyRow:
 * either a folder indicator or a tab item (which may belong to a folder).
 */
sealed interface DockItem {
    val key: String

    data class TabItem(
        val tab: Tab,
        val folder: TabFolder? = null,
        val isFirstInFolder: Boolean = false,
        val isLastInFolder: Boolean = false,
    ) : DockItem {
        override val key: String get() = tab.id
    }

    data class FolderItem(
        val folder: TabFolder,
        val hasVisibleTabs: Boolean = false,
    ) : DockItem {
        override val key: String get() = "folder_${folder.id}"
    }
}

/**
 * Computes ordered dock items from current tabs and folders.
 * When a folder is collapsed, its member tabs are omitted from the returned list.
 * When a folder is expanded, the folder indicator is followed by its member tabs.
 */
fun computeDockItems(
    tabs: List<Tab>,
    folders: List<TabFolder>,
): List<DockItem> {
    val folderMap = folders.associateBy { it.id }
    val result = mutableListOf<DockItem>()
    val seenFolders = mutableSetOf<String>()

    for (tab in tabs) {
        val folderId = tab.folderId
        if (folderId == null) {
            result.add(DockItem.TabItem(tab = tab))
        } else if (folderId !in seenFolders) {
            seenFolders.add(folderId)
            val folder = folderMap[folderId]
            if (folder != null) {
                val memberTabs = tabs.filter { it.folderId == folderId }
                val isCollapsed = folder.isCollapsed
                val hasVisibleTabs = !isCollapsed && memberTabs.isNotEmpty()
                result.add(DockItem.FolderItem(folder = folder, hasVisibleTabs = hasVisibleTabs))
                if (!isCollapsed) {
                    memberTabs.forEachIndexed { index, mTab ->
                        result.add(
                            DockItem.TabItem(
                                tab = mTab,
                                folder = folder,
                                isFirstInFolder = false,
                                isLastInFolder = index == memberTabs.lastIndex,
                            )
                        )
                    }
                }
            } else {
                result.add(DockItem.TabItem(tab = tab))
            }
        }
    }

    for (folder in folders) {
        if (folder.id !in seenFolders) {
            result.add(DockItem.FolderItem(folder = folder, hasVisibleTabs = false))
        }
    }

    return result
}
