package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jestk.browser.R
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
    """,
    )
    fun getAllHistoryFlow(workspaceId: String = "default_personal"): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit
    """,
    )
    fun getRecentHistoryFlow(
        limit: Int = 20,
        workspaceId: String = "default_personal",
    ): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit
    """,
    )
    suspend fun getRecentHistory(
        limit: Int = 20,
        workspaceId: String = "default_personal",
    ): List<HistoryEntity>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit OFFSET :offset
    """,
    )
    suspend fun getPagedHistory(
        limit: Int = 20,
        offset: Int = 0,
        workspaceId: String = "default_personal",
    ): List<HistoryEntity>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId AND (h.title LIKE '%' || :query || '%' OR h.url LIKE '%' || :query || '%')
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit
    """,
    )
    fun searchHistoryFlow(
        query: String,
        limit: Int = 15,
        workspaceId: String = "default_personal",
    ): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId AND (h.title LIKE '%' || :query || '%' OR h.url LIKE '%' || :query || '%')
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit
    """,
    )
    suspend fun searchHistory(
        query: String,
        limit: Int = 15,
        workspaceId: String = "default_personal",
    ): List<HistoryEntity>

    @Query(
        """
        SELECT h.* FROM history AS h
        INNER JOIN (
            SELECT url, MAX(timestamp) AS max_ts FROM history WHERE workspaceId = :workspaceId GROUP BY url
        ) AS latest ON h.url = latest.url AND h.timestamp = latest.max_ts
        WHERE h.workspaceId = :workspaceId AND (h.title LIKE '%' || :query || '%' OR h.url LIKE '%' || :query || '%')
        GROUP BY h.url
        ORDER BY h.timestamp DESC, h.id DESC
        LIMIT :limit OFFSET :offset
    """,
    )
    suspend fun searchHistoryPaged(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        workspaceId: String = "default_personal",
    ): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url AND workspaceId = :workspaceId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getRecentByUrl(url: String, workspaceId: String = "default_personal"): HistoryEntity?

    @Query("SELECT COUNT(*) FROM history WHERE workspaceId = :workspaceId")
    suspend fun getHistoryCount(workspaceId: String = "default_personal"): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity): Long

    @Query("UPDATE history SET timestamp = :timestamp, visitCount = visitCount + 1, title = :title WHERE id = :id")
    suspend fun updateVisit(
        id: Long,
        title: String,
        timestamp: Long,
    )

    @Transaction
    suspend fun recordVisit(
        url: String,
        title: String,
        workspaceId: String = "default_personal",
    ) {
        if (url.isBlank() || url.startsWith("about:")) return
        val existing = getRecentByUrl(url, workspaceId)
        val now = System.currentTimeMillis()
        val effectiveTitle = if (title.isNotBlank()) title else url
        if (existing != null) {
            updateVisit(existing.id, if (title.isNotBlank()) title else existing.title, now)
        } else {
            insertHistory(
                HistoryEntity(
                    url = url,
                    title = effectiveTitle,
                    timestamp = now,
                    visitCount = 1,
                    workspaceId = workspaceId,
                ),
            )
        }
    }

    @Delete
    suspend fun deleteHistory(item: HistoryEntity)

    @Query("DELETE FROM history WHERE url = (SELECT url FROM history WHERE id = :id)")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryBefore(cutoffTimestamp: Long)

    @Query("DELETE FROM history WHERE workspaceId = :workspaceId")
    suspend fun clearHistoryByWorkspace(workspaceId: String = "default_personal")

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
