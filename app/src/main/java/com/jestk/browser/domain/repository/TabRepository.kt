package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    fun getAllTabs(): Flow<List<Tab>>
    fun getRecentTabs(limit: Int = 6, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<Tab>>
    suspend fun getInitialTabs(): List<Tab>
    suspend fun getTabById(id: String): Tab?
    fun getTabByIdFlow(id: String): Flow<Tab?>
    suspend fun createTab(
        url: String = "",
        title: String = "",
        isIncognito: Boolean = false,
        id: String = java.util.UUID.randomUUID().toString(),
        workspaceId: String = Workspace.DEFAULT_PERSONAL_ID,
    ): Tab
    suspend fun updateTab(tab: Tab)
    suspend fun updateTabDetails(id: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    suspend fun updateSnapshot(id: String, snapshotPath: String)
    suspend fun updateFavicon(id: String, faviconUrl: String?)
    suspend fun updateThemeColor(id: String, themeColor: Int?)
    suspend fun setActiveTab(id: String)
    suspend fun deleteTab(id: String)
    suspend fun closeTab(id: String)
    suspend fun deleteAllTabs()
    suspend fun closeAllTabs()
    suspend fun reorderTabs(orderedTabs: List<Tab>)
    suspend fun updateFolderId(tabId: String, folderId: String?)
    suspend fun clearFolderId(folderId: String)
    suspend fun updateWorkspaceId(id: String, workspaceId: String, folderId: String? = null)
    suspend fun clearRecentTabs() {}
}
