package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.RecentTabEntity
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTabDao {
    @Query("SELECT * FROM recent_tabs WHERE workspaceId = :workspaceId ORDER BY lastVisitedTimestamp DESC, url DESC LIMIT :limit")
    fun getRecentTabsFlow(limit: Int = 10, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): Flow<List<RecentTabEntity>>

    @Query("SELECT * FROM recent_tabs WHERE workspaceId = :workspaceId ORDER BY lastVisitedTimestamp DESC, url DESC LIMIT :limit")
    suspend fun getRecentTabs(limit: Int = 10, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID): List<RecentTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentTab(tab: RecentTabEntity)

    @Query("DELETE FROM recent_tabs WHERE url = :url AND workspaceId = :workspaceId")
    suspend fun deleteRecentTabByUrl(url: String, workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)

    @Query("DELETE FROM recent_tabs WHERE workspaceId = :workspaceId")
    suspend fun clearRecentTabs(workspaceId: String = Workspace.DEFAULT_PERSONAL_ID)
}
