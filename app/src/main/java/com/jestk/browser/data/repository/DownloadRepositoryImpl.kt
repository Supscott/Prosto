package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.DownloadDao
import com.jestk.browser.data.local.entity.DownloadEntity
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloadsFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun recordDownload(
        url: String,
        fileName: String,
        filePath: String,
        mimeType: String?,
        contentLength: Long,
        downloadId: Long
    ): Long = withContext(ioDispatcher) {
        val entity = DownloadEntity(
            url = url,
            fileName = fileName,
            filePath = filePath,
            mimeType = mimeType,
            contentLength = contentLength,
            timestamp = System.currentTimeMillis(),
            downloadId = downloadId
        )
        downloadDao.insertDownload(entity)
    }

    override suspend fun deleteDownload(id: Long) = withContext(ioDispatcher) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun clearAllDownloads() = withContext(ioDispatcher) {
        downloadDao.clearAllDownloads()
    }
}
