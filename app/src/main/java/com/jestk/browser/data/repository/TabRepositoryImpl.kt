package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.data.mapper.toEntity
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TabRepositoryImpl(
    private val tabDao: TabDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TabRepository {

    private val repositoryMutex = Mutex()

    override fun getAllTabs(): Flow<List<Tab>> =
        tabDao.getAllTabsFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getRecentTabs(limit: Int): Flow<List<Tab>> =
        tabDao.getRecentTabsFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getInitialTabs(): List<Tab> = withContext(ioDispatcher) {
        tabDao.getAllTabs().map { it.toDomain() }
    }

    override suspend fun getTabById(id: String): Tab? = withContext(ioDispatcher) {
        tabDao.getTabById(id)?.toDomain()
    }

    override fun getTabByIdFlow(id: String): Flow<Tab?> =
        tabDao.getTabByIdFlow(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun createTab(url: String, title: String, isIncognito: Boolean): Tab = withContext(ioDispatcher) {
        repositoryMutex.withLock {
            val maxPos = tabDao.getMaxPosition() ?: -1
            val nextPosition = maxPos + 1
            val newTab = Tab(
                id = UUID.randomUUID().toString(),
                url = url,
                title = title.ifEmpty { if (url.isEmpty()) "Home" else "New Tab" },
                lastActiveTimestamp = System.currentTimeMillis(),
                snapshotPath = null,
                position = nextPosition,
                isIncognito = isIncognito,
                isActive = false,
                canGoBack = false,
                canGoForward = false
            )
            tabDao.insertTab(newTab.toEntity())
            newTab
        }
    }

    override suspend fun updateTab(tab: Tab) = withContext(ioDispatcher) {
        tabDao.updateTab(tab.toEntity())
    }

    override suspend fun updateTabDetails(
        id: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) = withContext(ioDispatcher) {
        tabDao.updateTabDetails(id, url, title, canGoBack, canGoForward)
    }

    override suspend fun updateSnapshot(id: String, snapshotPath: String) = withContext(ioDispatcher) {
        tabDao.updateSnapshotPath(id, snapshotPath)
    }

    override suspend fun updateFavicon(id: String, faviconUrl: String) = withContext(ioDispatcher) {
        tabDao.updateFavicon(id, faviconUrl)
    }

    override suspend fun updateThemeColor(id: String, themeColor: Int?) = withContext(ioDispatcher) {
        tabDao.updateThemeColor(id, themeColor)
    }

    override suspend fun setActiveTab(id: String) = withContext(ioDispatcher) {
        tabDao.setActiveTab(id)
    }

    override suspend fun deleteTab(id: String) = withContext(ioDispatcher) {
        tabDao.deleteTabById(id)
        val remaining = tabDao.getAllTabs()
        tabDao.reorderTabs(remaining)
    }

    override suspend fun closeTab(id: String) = deleteTab(id)

    override suspend fun deleteAllTabs() = withContext(ioDispatcher) {
        tabDao.deleteAllTabs()
    }

    override suspend fun closeAllTabs() = deleteAllTabs()

    override suspend fun reorderTabs(orderedTabs: List<Tab>) = withContext(ioDispatcher) {
        tabDao.reorderTabs(orderedTabs.map { it.toEntity() })
    }

    override suspend fun clearRecentTabs() = withContext(ioDispatcher) {
        // No-op for TabRepository since recent tabs are derived from tabs table or managed by RecentTabRepository
    }
}
