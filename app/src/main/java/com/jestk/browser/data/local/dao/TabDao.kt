package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jestk.browser.data.local.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabsFlow(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    suspend fun getAllTabs(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: String): TabEntity?

    @Query("SELECT * FROM tabs WHERE url = :url AND isIncognito = :isIncognito AND url != '' AND url != 'about:blank' LIMIT 1")
    suspend fun getTabByUrl(
        url: String,
        isIncognito: Boolean,
    ): TabEntity?

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    fun getTabByIdFlow(id: String): Flow<TabEntity?>

    @Query("SELECT * FROM tabs ORDER BY lastActiveTimestamp DESC LIMIT :limit")
    fun getRecentTabsFlow(limit: Int = 6): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY lastActiveTimestamp DESC LIMIT :limit")
    suspend fun getRecentTabs(limit: Int = 6): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTab(): TabEntity?

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    fun getActiveTabFlow(): Flow<TabEntity?>

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun getTabsCount(): Int

    @Query("SELECT MAX(position) FROM tabs")
    suspend fun getMaxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Update
    suspend fun updateTabs(tabs: List<TabEntity>)

    @Delete
    suspend fun deleteTab(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteTabById(id: String)

    @Query("DELETE FROM tabs")
    suspend fun deleteAllTabs()

    @Query("UPDATE tabs SET snapshotPath = :snapshotPath WHERE id = :id")
    suspend fun updateSnapshotPath(
        id: String,
        snapshotPath: String,
    )

    @Query("UPDATE tabs SET faviconUrl = :faviconUrl WHERE id = :id")
    suspend fun updateFavicon(
        id: String,
        faviconUrl: String?,
    )

    @Query("UPDATE tabs SET themeColor = :themeColor WHERE id = :id")
    suspend fun updateThemeColor(
        id: String,
        themeColor: Int?,
    )

    @Query("UPDATE tabs SET url = :url, title = :title, canGoBack = :canGoBack, canGoForward = :canGoForward WHERE id = :id")
    suspend fun updateTabDetails(
        id: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    )

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    fun updatePosition(
        id: String,
        position: Int,
    )

    @Query("UPDATE tabs SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE tabs SET isActive = 1, lastActiveTimestamp = :timestamp WHERE id = :id")
    suspend fun markActive(
        id: String,
        timestamp: Long,
    )

    @Query(
        "UPDATE tabs SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END, lastActiveTimestamp = CASE WHEN id = :activeId THEN :timestamp ELSE lastActiveTimestamp END",
    )
    suspend fun setActiveTabAtomic(
        activeId: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    suspend fun setActiveTab(activeId: String) {
        setActiveTabAtomic(activeId, System.currentTimeMillis())
    }

    @Transaction
    fun reorderTabs(orderedTabs: List<TabEntity>) {
        orderedTabs.forEachIndexed { index, tab ->
            updatePosition(tab.id, index)
        }
    }

    @Transaction
    fun reorderTabIds(orderedTabIds: List<String>) {
        orderedTabIds.forEachIndexed { index, tabId ->
            updatePosition(tabId, index)
        }
    }
}
