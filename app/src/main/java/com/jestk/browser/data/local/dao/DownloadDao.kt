package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, bytesDownloaded: Long, totalBytes: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String? = null)

    @Query("UPDATE downloads SET filePath = :filePath, fileName = :fileName WHERE id = :id")
    suspend fun updateFilePath(id: Long, fileName: String, filePath: String)

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING', 'PAUSED') ORDER BY timestamp ASC")
    suspend fun getActiveOrPendingDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()
}
