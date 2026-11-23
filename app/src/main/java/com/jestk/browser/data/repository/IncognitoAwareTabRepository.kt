package com.jestk.browser.data.repository

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Keeps incognito tabs in memory only and sends everything else to [delegate] (the database).
 * No call whose id belongs to an incognito tab reaches [delegate], so nothing about such a tab
 * is ever written to disk; the tabs vanish with the process.
 *
 * Incognito tabs follow the regular tabs in every merged list, in creation order.
 */
class IncognitoAwareTabRepository(
    private val delegate: TabRepository,
) : TabRepository {
    private val incognitoTabs = MutableStateFlow<List<Tab>>(emptyList())

    private fun isIncognitoId(id: String): Boolean = incognitoTabs.value.any { it.id == id }

    private fun mergeTabs(stored: List<Tab>, incognito: List<Tab>): List<Tab> {
        if (incognito.isEmpty()) return stored
        val incognitoIsActive = incognito.any { it.isActive }
        val base = if (incognitoIsActive) stored.map { it.copy(isActive = false) } else stored
        val firstPosition = (stored.maxOfOrNull { it.position } ?: -1) + 1
        return base + incognito.mapIndexed { index, tab -> tab.copy(position = firstPosition + index) }
    }

    private fun updateIncognito(id: String, transform: (Tab) -> Tab) {
        incognitoTabs.update { tabs -> tabs.map { if (it.id == id) transform(it) else it } }
    }

    override fun getAllTabs(): Flow<List<Tab>> =
        combine(delegate.getAllTabs(), incognitoTabs, ::mergeTabs)

    override suspend fun getInitialTabs(): List<Tab> = mergeTabs(delegate.getInitialTabs(), incognitoTabs.value)

    override suspend fun getTabById(id: String): Tab? =
        incognitoTabs.value.find { it.id == id } ?: delegate.getTabById(id)

    override fun getTabByIdFlow(id: String): Flow<Tab?> =
        combine(delegate.getTabByIdFlow(id), incognitoTabs) { stored, incognito ->
            incognito.find { it.id == id } ?: stored
        }

    override suspend fun createTab(
        url: String,
        title: String,
        isIncognito: Boolean,
        id: String,
        workspaceId: String,
    ): Tab {
        if (!isIncognito) return delegate.createTab(url, title, false, id, workspaceId)
        val tab =
            Tab(
                id = id,
                url = url,
                title = title,
                lastActiveTimestamp = System.currentTimeMillis(),
                snapshotPath = null,
                position = incognitoTabs.value.size,
                isIncognito = true,
                isActive = false,
                canGoBack = false,
                canGoForward = false,
                workspaceId = workspaceId,
            )
        incognitoTabs.update { it + tab }
        return tab
    }

    override suspend fun updateTab(tab: Tab) {
        if (isIncognitoId(tab.id)) updateIncognito(tab.id) { tab } else delegate.updateTab(tab)
    }

    override suspend fun updateTabDetails(
        id: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) {
        if (isIncognitoId(id)) {
            updateIncognito(id) { it.copy(url = url, title = title, canGoBack = canGoBack, canGoForward = canGoForward) }
        } else {
            delegate.updateTabDetails(id, url, title, canGoBack, canGoForward)
        }
    }

    override suspend fun updateSnapshot(id: String, snapshotPath: String) {
        if (!isIncognitoId(id)) delegate.updateSnapshot(id, snapshotPath)
    }

    override suspend fun updateFavicon(id: String, faviconUrl: String?) {
        if (!isIncognitoId(id)) delegate.updateFavicon(id, faviconUrl)
    }

    override suspend fun updateThemeColor(id: String, themeColor: Int?) {
        if (isIncognitoId(id)) updateIncognito(id) { it.copy(themeColor = themeColor) } else delegate.updateThemeColor(id, themeColor)
    }

    override suspend fun setActiveTab(id: String) {
        val incognito = isIncognitoId(id)
        incognitoTabs.update { tabs -> tabs.map { it.copy(isActive = incognito && it.id == id) } }
        if (!incognito) delegate.setActiveTab(id)
    }

    override suspend fun deleteTab(id: String) {
        if (isIncognitoId(id)) {
            incognitoTabs.update { tabs -> tabs.filter { it.id != id } }
        } else {
            delegate.deleteTab(id)
        }
    }

    override suspend fun closeTab(id: String) = deleteTab(id)

    override suspend fun deleteAllTabs() {
        incognitoTabs.value = emptyList()
        delegate.deleteAllTabs()
    }

    override suspend fun closeAllTabs() = deleteAllTabs()

    override suspend fun reorderTabs(orderedTabs: List<Tab>) {
        val incognitoIds = incognitoTabs.value.mapTo(HashSet()) { it.id }
        val (incognito, stored) = orderedTabs.partition { it.id in incognitoIds }
        if (incognito.isNotEmpty()) {
            val order = incognito.mapIndexed { index, tab -> tab.id to index }.toMap()
            incognitoTabs.update { tabs -> tabs.sortedBy { order[it.id] ?: Int.MAX_VALUE } }
        }
        delegate.reorderTabs(stored)
    }

    override suspend fun updateFolderId(tabId: String, folderId: String?) {
        if (isIncognitoId(tabId)) updateIncognito(tabId) { it.copy(folderId = folderId) } else delegate.updateFolderId(tabId, folderId)
    }

    override suspend fun clearFolderId(folderId: String) {
        incognitoTabs.update { tabs -> tabs.map { if (it.folderId == folderId) it.copy(folderId = null) else it } }
        delegate.clearFolderId(folderId)
    }

    override suspend fun updateWorkspaceId(id: String, workspaceId: String, folderId: String?) {
        if (isIncognitoId(id)) {
            updateIncognito(id) { it.copy(workspaceId = workspaceId, folderId = folderId) }
        } else {
            delegate.updateWorkspaceId(id, workspaceId, folderId)
        }
    }
}
