package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.RecentTabItem
import kotlinx.coroutines.flow.Flow

interface RecentTabRepository {
    fun getRecentTabs(limit: Int = 10): Flow<List<RecentTabItem>>
    suspend fun recordRecentTab(
        url: String,
        title: String,
        snapshotPath: String? = null,
        faviconUrl: String? = null
    )
    suspend fun updateSnapshot(url: String, snapshotPath: String)
    suspend fun updateFavicon(url: String, faviconUrl: String)
    suspend fun updateTitle(url: String, title: String)
    suspend fun deleteRecentTab(url: String)
    suspend fun clearRecentTabs()
}
