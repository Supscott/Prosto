package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.SiteAllowlistDao
import com.jestk.browser.data.local.entity.SiteAllowlistEntity
import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.SiteAllowlistHosts
import com.jestk.browser.domain.repository.SiteAllowlistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SiteAllowlistRepositoryImpl(
    private val dao: SiteAllowlistDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : SiteAllowlistRepository {

    override fun observe(): Flow<List<SiteAllowlistEntry>> =
        dao.observeAll()
            .map { rows ->
                rows.map {
                    SiteAllowlistEntry(
                        host = it.host,
                        workspaceId = it.workspaceScope.takeUnless { scope -> scope == SiteAllowlistEntity.ALL_WORKSPACES },
                        createdAt = it.createdAt,
                    )
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun add(host: String, workspaceId: String?) {
        val normalized = SiteAllowlistHosts.normalize(host)
        if (normalized.isEmpty()) return
        withContext(ioDispatcher) { dao.insert(SiteAllowlistEntity(normalized, scopeOf(workspaceId), clock())) }
    }

    override suspend fun remove(host: String, workspaceId: String?) {
        withContext(ioDispatcher) { dao.delete(SiteAllowlistHosts.normalize(host), scopeOf(workspaceId)) }
    }

    override suspend fun isAllowlisted(host: String, workspaceId: String?): Boolean {
        val hosts = SiteAllowlistHosts.candidates(host)
        if (hosts.isEmpty()) return false
        val scopes = listOfNotNull(SiteAllowlistEntity.ALL_WORKSPACES, workspaceId)
        return withContext(ioDispatcher) { dao.countMatching(hosts, scopes) > 0 }
    }

    private fun scopeOf(workspaceId: String?) = workspaceId ?: SiteAllowlistEntity.ALL_WORKSPACES
}
