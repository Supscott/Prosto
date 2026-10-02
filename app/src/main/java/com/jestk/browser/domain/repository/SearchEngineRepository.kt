package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.flow.Flow

interface SearchEngineRepository {
    fun getSearchEngines(): Flow<List<SearchEngine>>
    suspend fun getAllSearchEngines(): List<SearchEngine>
    suspend fun getSearchEngineById(id: String): SearchEngine?
    suspend fun getSearchEngineByShortcut(shortcut: String): SearchEngine?
    suspend fun saveSearchEngine(engine: SearchEngine)
    suspend fun deleteSearchEngine(id: String)
    suspend fun reorderSearchEngines(engines: List<SearchEngine>)
    suspend fun ensureDefaultEnginesSeeded()
}
