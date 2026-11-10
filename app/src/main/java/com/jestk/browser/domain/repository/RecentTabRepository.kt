package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface RecentTabRepository {
    fun getRecentTabs(limit: Int = 10, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<RecentTabItem>>
    suspend fun recordRecentTab(
        url: String,
        title: String,
        snapshotPath: String? = null,
        faviconUrl: String? = null,
        workspaceId: String = Workspace.DEFAULT_PERSONAL_ID,
    )
    suspend fun updateSnapshot(url: String, snapshotPath: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
    suspend fun updateFavicon(url: String, faviconUrl: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
    suspend fun updateTitle(url: String, title: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
    suspend fun deleteRecentTab(url: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
    suspend fun clearRecentTabs(workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
}
