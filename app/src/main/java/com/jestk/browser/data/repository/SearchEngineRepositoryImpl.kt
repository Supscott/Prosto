package com.jestk.browser.data.repository

import android.content.Context
import com.jestk.browser.data.local.dao.SearchEngineDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.SearchEngineIconDownloader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SearchEngineRepositoryImpl(
    private val searchEngineDao: SearchEngineDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val context: Context? = null
) : SearchEngineRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

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
        } else {
            // Delete bing if present
            searchEngineDao.deleteById("bing")

            // Ensure missing built-in default engines are present
            val existing = searchEngineDao.getAllSearchEngines()
            val existingIds = existing.map { it.id }.toSet()
            val missing = SearchEngine.DEFAULT_ENGINES.filter { it.id !in existingIds }
            if (missing.isNotEmpty()) {
                val startPos = existing.size
                val toInsert = missing.mapIndexed { idx, engine ->
                    engine.copy(orderPosition = startPos + idx).toEntity()
                }
                searchEngineDao.insertAll(toInsert)
            }

            // Migrate any legacy asset URLs
            existing.forEach { entity ->
                if (entity.iconUrl.contains("android_asset")) {
                    val fallback = SearchEngine.DEFAULT_ENGINES.firstOrNull { it.id == entity.id }?.iconUrl
                        ?: FaviconManager.extractFallbackFaviconUrl(entity.searchUrlTemplate)
                    if (fallback != null) {
                        searchEngineDao.update(entity.copy(iconUrl = fallback))
                    }
                }
            }
        }

        if (context != null) {
            repositoryScope.launch {
                downloadIcons(context)
            }
        }
    }

    suspend fun downloadIcons(ctx: Context) = withContext(ioDispatcher) {
        try {
            val allEngines = searchEngineDao.getAllSearchEngines().map { it.toDomain() }
            for (engine in allEngines) {
                if (engine.iconUrl.startsWith("file://") || engine.iconUrl.startsWith("/")) {
                    val localPath = engine.iconUrl.removePrefix("file://")
                    val localFile = File(localPath)
                    if (localFile.exists() && localFile.length() > 0) {
                        continue
                    }
                }
                val localFilePath = SearchEngineIconDownloader.downloadIconForEngine(ctx, engine)
                if (localFilePath != null) {
                    val fileUri = "file://$localFilePath"
                    searchEngineDao.update(engine.copy(iconUrl = fileUri).toEntity())
                }
            }
        } catch (_: Throwable) {
            // Non-fatal, will retry on subsequent launches
        }
    }
}
