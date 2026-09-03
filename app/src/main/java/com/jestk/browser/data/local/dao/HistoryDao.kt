package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 20): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchHistoryFlow(query: String, limit: Int = 15): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchHistory(query: String, limit: Int = 15): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecentByUrl(url: String): HistoryEntity?

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity): Long

    @Query("UPDATE history SET timestamp = :timestamp, visitCount = visitCount + 1, title = :title WHERE id = :id")
    suspend fun updateVisit(id: Long, title: String, timestamp: Long)

    suspend fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val existing = getRecentByUrl(url)
        val now = System.currentTimeMillis()
        val effectiveTitle = if (title.isNotBlank()) title else url
        if (existing != null && (now - existing.timestamp) < 300_000L) {
            updateVisit(existing.id, if (title.isNotBlank()) title else existing.title, now)
        } else {
            insertHistory(
                HistoryEntity(
                    url = url,
                    title = effectiveTitle,
                    timestamp = now,
                    visitCount = 1
                )
            )
        }
    }

    @Delete
    suspend fun deleteHistory(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryBefore(cutoffTimestamp: Long)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
