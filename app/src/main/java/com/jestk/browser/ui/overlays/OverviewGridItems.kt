package com.jestk.browser.ui.overlays

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder

internal sealed interface OverviewGridItem {
    val key: String

    data class TabCard(val tab: Tab) : OverviewGridItem {
        override val key: String get() = "tab_${tab.id}"
    }

    data class FolderCard(val folder: TabFolder, val tabs: List<Tab>) : OverviewGridItem {
        override val key: String get() = "folder_${folder.id}"
    }

    data class FolderHeader(val folder: TabFolder, val tabs: List<Tab>) : OverviewGridItem {
        override val key: String get() = "folder_header_${folder.id}"
    }

    data class FolderTabCard(
        val folder: TabFolder,
        val tab: Tab,
        val memberIndex: Int,
        val totalMembers: Int,
    ) : OverviewGridItem {
        override val key: String get() = "tab_${tab.id}"
    }

    data class FolderSpacer(val folderId: String, val index: Int) : OverviewGridItem {
        override val key: String get() = "folder_spacer_${folderId}_$index"
    }
}

internal fun computeOverviewItems(
    tabs: List<Tab>,
    folders: List<TabFolder>,
    searchQuery: String,
    expandedFolderIds: Set<String> = emptySet(),
): List<OverviewGridItem> {
    val folderTabsMap = tabs.filter { it.folderId != null }.groupBy { it.folderId!! }
    fun matchesQuery(text: String) = text.contains(searchQuery, ignoreCase = true)
    fun tabMatches(tab: Tab) = matchesQuery(tab.title) || matchesQuery(tab.url)
    fun folderMatches(folder: TabFolder, memberTabs: List<Tab>): Boolean {
        return matchesQuery(folder.title) || memberTabs.any { tabMatches(it) }
    }

    val items = mutableListOf<OverviewGridItem>()
    val processedFolderIds = mutableSetOf<String>()

    fun addFolderItems(folder: TabFolder, memberTabs: List<Tab>) {
        val isExpanded = folder.id in expandedFolderIds || searchQuery.isNotBlank()
        if (isExpanded) {
            items.add(OverviewGridItem.FolderHeader(folder, memberTabs))
            memberTabs.forEachIndexed { index, tab ->
                items.add(OverviewGridItem.FolderTabCard(folder, tab, index, memberTabs.size))
            }
            val remainder = memberTabs.size % 3
            val padding = if (remainder == 0) 0 else 3 - remainder
            for (i in 0 until padding) {
                items.add(OverviewGridItem.FolderSpacer(folder.id, i))
            }
        } else {
            items.add(OverviewGridItem.FolderCard(folder, memberTabs))
        }
    }

    for (tab in tabs) {
        val folderId = tab.folderId
        if (folderId == null) {
            if (searchQuery.isBlank() || tabMatches(tab)) {
                items.add(OverviewGridItem.TabCard(tab))
            }
        } else {
            if (folderId !in processedFolderIds) {
                processedFolderIds.add(folderId)
                val folder = folders.find { it.id == folderId }
                val memberTabs = folderTabsMap[folderId] ?: emptyList()
                if (folder != null) {
                    if (searchQuery.isBlank()) {
                        addFolderItems(folder, memberTabs)
                    } else if (folderMatches(folder, memberTabs)) {
                        val visibleTabs = if (matchesQuery(folder.title)) {
                            memberTabs
                        } else {
                            memberTabs.filter { tabMatches(it) }
                        }
                        addFolderItems(folder, visibleTabs)
                    }
                }
            }
        }
    }

    for (folder in folders) {
        if (folder.id !in processedFolderIds) {
            val memberTabs = folderTabsMap[folder.id] ?: emptyList()
            if (searchQuery.isBlank() || folderMatches(folder, memberTabs)) {
                val visibleTabs = if (searchQuery.isBlank() || matchesQuery(folder.title)) {
                    memberTabs
                } else {
                    memberTabs.filter { tabMatches(it) }
                }
                addFolderItems(folder, visibleTabs)
            }
        }
    }

    return items
}
