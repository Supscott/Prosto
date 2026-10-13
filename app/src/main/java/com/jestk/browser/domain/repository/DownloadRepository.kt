package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    suspend fun getDownloadById(id: Long): DownloadItem?
    suspend fun getActiveOrPendingDownloads(): List<DownloadItem>
    suspend fun recordDownload(
        url: String,
        fileName: String,
        filePath: String,
        mimeType: String?,
        contentLength: Long,
        downloadId: Long = -1L,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = contentLength,
        etag: String? = null
    ): Long
    suspend fun updateProgress(
        id: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
        status: DownloadStatus = DownloadStatus.DOWNLOADING
    )
    suspend fun updateStatus(id: Long, status: DownloadStatus, errorMessage: String? = null)
    suspend fun updateFilePath(id: Long, fileName: String, filePath: String)
    suspend fun deleteDownload(id: Long, deleteFile: Boolean = true)
    suspend fun clearAllDownloads(deleteFiles: Boolean = true)
}
