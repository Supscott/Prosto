package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.RecentTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTabDao {
    @Query("SELECT * FROM recent_tabs ORDER BY lastVisitedTimestamp DESC LIMIT :limit")
    fun getRecentTabsFlow(limit: Int = 10): Flow<List<RecentTabEntity>>

    @Query("SELECT * FROM recent_tabs ORDER BY lastVisitedTimestamp DESC LIMIT :limit")
    suspend fun getRecentTabs(limit: Int = 10): List<RecentTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentTab(tab: RecentTabEntity)

    @Query("DELETE FROM recent_tabs WHERE url = :url")
    suspend fun deleteRecentTabByUrl(url: String)

    @Query("DELETE FROM recent_tabs")
    suspend fun clearRecentTabs()
}
