package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.RecentTabDao
import com.jestk.browser.data.local.entity.RecentTabEntity
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.repository.RecentTabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RecentTabRepositoryImpl(
    private val recentTabDao: RecentTabDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecentTabRepository {

    override fun getRecentTabs(limit: Int, workspaceId: String): Flow<List<RecentTabItem>> =
        recentTabDao.getRecentTabsFlow(limit, workspaceId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun recordRecentTab(
        url: String,
        title: String,
        snapshotPath: String?,
        faviconUrl: String?,
        workspaceId: String,
    ) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100, workspaceId).firstOrNull { it.url == url }
        val entity = RecentTabEntity(
            url = url,
            title = title.ifBlank { existing?.title ?: url },
            snapshotPath = snapshotPath ?: existing?.snapshotPath,
            faviconUrl = faviconUrl ?: existing?.faviconUrl,
            lastVisitedTimestamp = System.currentTimeMillis(),
            workspaceId = workspaceId,
        )
        recentTabDao.upsertRecentTab(entity)
    }

    override suspend fun updateSnapshot(url: String, snapshotPath: String, workspaceId: String) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100, workspaceId).firstOrNull { it.url == url }
        if (existing != null) {
            recentTabDao.upsertRecentTab(existing.copy(snapshotPath = snapshotPath))
        }
    }

    override suspend fun updateFavicon(url: String, faviconUrl: String, workspaceId: String) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100, workspaceId).firstOrNull { it.url == url }
        if (existing != null) {
            recentTabDao.upsertRecentTab(existing.copy(faviconUrl = faviconUrl))
        }
    }

    override suspend fun deleteRecentTab(url: String, workspaceId: String) = withContext(ioDispatcher) {
        recentTabDao.deleteRecentTabByUrl(url, workspaceId)
    }

    override suspend fun clearRecentTabs(workspaceId: String) = withContext(ioDispatcher) {
        recentTabDao.clearRecentTabs(workspaceId)
    }
}
