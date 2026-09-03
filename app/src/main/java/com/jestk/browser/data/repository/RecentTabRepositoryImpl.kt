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

    override fun getRecentTabs(limit: Int): Flow<List<RecentTabItem>> =
        recentTabDao.getRecentTabsFlow(limit).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun recordRecentTab(
        url: String,
        title: String,
        snapshotPath: String?,
        faviconUrl: String?
    ) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100).firstOrNull { it.url == url }
        val entity = RecentTabEntity(
            url = url,
            title = title.ifBlank { existing?.title ?: url },
            snapshotPath = snapshotPath ?: existing?.snapshotPath,
            faviconUrl = faviconUrl ?: existing?.faviconUrl,
            lastVisitedTimestamp = System.currentTimeMillis()
        )
        recentTabDao.upsertRecentTab(entity)
    }

    override suspend fun updateSnapshot(url: String, snapshotPath: String) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100).firstOrNull { it.url == url }
        if (existing != null) {
            recentTabDao.upsertRecentTab(existing.copy(snapshotPath = snapshotPath))
        }
    }

    override suspend fun updateFavicon(url: String, faviconUrl: String) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100).firstOrNull { it.url == url }
        if (existing != null) {
            recentTabDao.upsertRecentTab(existing.copy(faviconUrl = faviconUrl))
        }
    }

    override suspend fun updateTitle(url: String, title: String) = withContext(ioDispatcher) {
        if (url.isBlank() || url == "about:blank") return@withContext
        val existing = recentTabDao.getRecentTabs(100).firstOrNull { it.url == url }
        if (existing != null) {
            recentTabDao.upsertRecentTab(existing.copy(title = title))
        }
    }

    override suspend fun deleteRecentTab(url: String) = withContext(ioDispatcher) {
        recentTabDao.deleteRecentTabByUrl(url)
    }

    override suspend fun clearRecentTabs() = withContext(ioDispatcher) {
        recentTabDao.clearRecentTabs()
    }
}
