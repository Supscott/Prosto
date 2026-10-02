package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.DownloadDao
import com.jestk.browser.data.local.entity.DownloadEntity
import com.jestk.browser.data.mapper.toDomain
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloadsFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getDownloadById(id: Long): DownloadItem? = withContext(ioDispatcher) {
        downloadDao.getDownloadById(id)?.toDomain()
    }

    override suspend fun getActiveOrPendingDownloads(): List<DownloadItem> = withContext(ioDispatcher) {
        downloadDao.getActiveOrPendingDownloads().map { it.toDomain() }
    }

    override suspend fun recordDownload(
        url: String,
        fileName: String,
        filePath: String,
        mimeType: String?,
        contentLength: Long,
        downloadId: Long,
        status: DownloadStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
        etag: String?
    ): Long = withContext(ioDispatcher) {
        val entity = DownloadEntity(
            url = url,
            fileName = fileName,
            filePath = filePath,
            mimeType = mimeType,
            contentLength = contentLength,
            timestamp = System.currentTimeMillis(),
            downloadId = downloadId,
            status = status.name,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            etag = etag
        )
        downloadDao.insertDownload(entity)
    }

    override suspend fun updateProgress(
        id: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
        status: DownloadStatus
    ) = withContext(ioDispatcher) {
        downloadDao.updateProgress(
            id = id,
            status = status.name,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes
        )
    }

    override suspend fun updateStatus(
        id: Long,
        status: DownloadStatus,
        errorMessage: String?
    ) = withContext(ioDispatcher) {
        downloadDao.updateStatus(id, status.name, errorMessage)
    }

    override suspend fun updateFilePath(
        id: Long,
        filePath: String,
        fileName: String
    ) = withContext(ioDispatcher) {
        downloadDao.updateFilePath(id, filePath, fileName)
    }

    override suspend fun deleteDownload(id: Long, deleteFile: Boolean) = withContext(ioDispatcher) {
        if (deleteFile) {
            val entity = downloadDao.getDownloadById(id)
            if (entity != null) {
                try {
                    val file = File(entity.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    val partFile = File("${entity.filePath}.crdownload")
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                } catch (_: Throwable) {}
            }
        }
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun clearAllDownloads(deleteFiles: Boolean) = withContext(ioDispatcher) {
        if (deleteFiles) {
            val entities = downloadDao.getActiveOrPendingDownloads()
            for (entity in entities) {
                try {
                    File(entity.filePath).delete()
                    File("${entity.filePath}.crdownload").delete()
                } catch (_: Throwable) {}
            }
        }
        downloadDao.clearAllDownloads()
    }
}
