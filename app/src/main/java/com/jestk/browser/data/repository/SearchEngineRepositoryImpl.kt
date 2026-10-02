package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.SearchEngineDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.SearchEngineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SearchEngineRepositoryImpl(
    private val searchEngineDao: SearchEngineDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SearchEngineRepository {

    override fun getSearchEngines(): Flow<List<SearchEngine>> =
        searchEngineDao.getAllSearchEnginesFlow()
            .map { entities ->
                if (entities.isEmpty()) {
                    SearchEngine.DEFAULT_ENGINES
                } else {
                    entities.map { it.toDomain() }
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun getAllSearchEngines(): List<SearchEngine> = withContext(ioDispatcher) {
        val list = searchEngineDao.getAllSearchEngines()
        if (list.isEmpty()) {
            ensureDefaultEnginesSeeded()
            SearchEngine.DEFAULT_ENGINES
        } else {
            list.map { it.toDomain() }
        }
    }

    override suspend fun getSearchEngineById(id: String): SearchEngine? = withContext(ioDispatcher) {
        searchEngineDao.getSearchEngineById(id)?.toDomain()
            ?: SearchEngine.DEFAULT_ENGINES.firstOrNull { it.id == id }
    }

    override suspend fun getSearchEngineByShortcut(shortcut: String): SearchEngine? = withContext(ioDispatcher) {
        searchEngineDao.getSearchEngineByShortcut(shortcut.trim())?.toDomain()
            ?: SearchEngine.DEFAULT_ENGINES.firstOrNull { it.shortcut?.equals(shortcut.trim(), ignoreCase = true) == true }
    }

    override suspend fun saveSearchEngine(engine: SearchEngine) = withContext(ioDispatcher) {
        ensureDefaultEnginesSeeded()
        val existing = searchEngineDao.getSearchEngineById(engine.id)
        if (existing != null) {
            searchEngineDao.update(engine.toEntity())
        } else {
            val count = searchEngineDao.getCount()
            val engineWithPos = if (engine.orderPosition == 0 && count > 0) {
                engine.copy(orderPosition = count)
            } else {
                engine
            }
            searchEngineDao.insert(engineWithPos.toEntity())
        }
    }

    override suspend fun deleteSearchEngine(id: String) = withContext(ioDispatcher) {
        ensureDefaultEnginesSeeded()
        searchEngineDao.deleteById(id)
    }

    override suspend fun reorderSearchEngines(engines: List<SearchEngine>) = withContext(ioDispatcher) {
        searchEngineDao.reorder(engines.map { it.toEntity() })
    }

    override suspend fun ensureDefaultEnginesSeeded() = withContext(ioDispatcher) {
        val count = searchEngineDao.getCount()
        if (count == 0) {
            searchEngineDao.insertAll(SearchEngine.DEFAULT_ENGINES.map { it.toEntity() })
        }
    }
}
