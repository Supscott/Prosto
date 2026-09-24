package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryItem>>
    fun getRecentHistory(limit: Int = 20): Flow<List<HistoryItem>>
    fun searchHistory(query: String): Flow<List<HistoryItem>>
    suspend fun getPagedHistory(limit: Int = 20, offset: Int = 0): List<HistoryItem>
    suspend fun searchHistoryPaged(query: String, limit: Int = 20, offset: Int = 0): List<HistoryItem>
    suspend fun recordVisit(url: String, title: String)
    suspend fun deleteHistory(id: Long)
    suspend fun deleteHistoryItem(id: Long)
    suspend fun clearHistory()
}
