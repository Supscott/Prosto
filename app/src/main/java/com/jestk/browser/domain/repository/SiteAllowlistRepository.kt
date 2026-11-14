package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.SiteAllowlistEntry
import kotlinx.coroutines.flow.Flow

/** Persisted per-site switch-off of content blocking (PLAN Q8: scoped by workspace id). */
interface SiteAllowlistRepository {
    fun observe(): Flow<List<SiteAllowlistEntry>>

    /** Adds [host] for [workspaceId], or for all workspaces when null. Blank hosts are ignored. */
    suspend fun add(host: String, workspaceId: String?)

    suspend fun remove(host: String, workspaceId: String?)

    /** Whether [host] (or a parent domain of it) is allowlisted for [workspaceId] or for all workspaces. */
    suspend fun isAllowlisted(host: String, workspaceId: String?): Boolean
}
