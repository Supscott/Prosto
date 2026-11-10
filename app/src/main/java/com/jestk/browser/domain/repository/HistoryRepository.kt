package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<HistoryItem>>
    fun getRecentHistory(limit: Int = 20, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<HistoryItem>>
    fun searchHistory(query: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<HistoryItem>>
    suspend fun getPagedHistory(limit: Int = 20, offset: Int = 0, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): List<HistoryItem>
    suspend fun searchHistoryPaged(query: String, limit: Int = 20, offset: Int = 0, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): List<HistoryItem>
    suspend fun recordVisit(url: String, title: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
    suspend fun clearHistory(workspaceId: String? = null)
}
