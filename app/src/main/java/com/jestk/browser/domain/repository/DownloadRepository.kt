package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadItem>>
    suspend fun recordDownload(
        url: String,
        fileName: String,
        filePath: String,
        mimeType: String?,
        contentLength: Long,
        downloadId: Long = -1L
    ): Long
    suspend fun deleteDownload(id: Long)
    suspend fun clearAllDownloads()
}
