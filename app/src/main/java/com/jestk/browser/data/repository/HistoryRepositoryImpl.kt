package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HistoryRepositoryImpl(
    private val historyDao: HistoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<HistoryItem>> =
        historyDao.getAllHistoryFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getRecentHistory(limit: Int): Flow<List<HistoryItem>> =
        historyDao.getRecentHistoryFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun searchHistory(query: String): Flow<List<HistoryItem>> =
        historyDao.searchHistoryFlow(query)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getPagedHistory(limit: Int, offset: Int): List<HistoryItem> =
        withContext(ioDispatcher) {
            historyDao.getPagedHistory(limit, offset).map { it.toDomain() }
        }

    override suspend fun searchHistoryPaged(query: String, limit: Int, offset: Int): List<HistoryItem> =
        withContext(ioDispatcher) {
            historyDao.searchHistoryPaged(query, limit, offset).map { it.toDomain() }
        }

    override suspend fun recordVisit(url: String, title: String) = withContext(ioDispatcher) {
        if (url.isNotBlank() && !url.startsWith("about:")) {
            historyDao.recordVisit(url, title)
        }
    }

    override suspend fun deleteHistory(id: Long) = withContext(ioDispatcher) {
        historyDao.deleteHistoryById(id)
    }

    override suspend fun deleteHistoryItem(id: Long) = deleteHistory(id)

    override suspend fun clearHistory() = withContext(ioDispatcher) {
        historyDao.clearAllHistory()
    }
}
