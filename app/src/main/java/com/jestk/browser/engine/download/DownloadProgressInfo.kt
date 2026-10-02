package com.jestk.browser.engine.download

import com.jestk.browser.domain.model.DownloadStatus

data class DownloadProgressInfo(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val mimeType: String?,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null
) {
    val progressFraction: Float?
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null

    val progressPercentage: Int?
        get() = if (totalBytes > 0L) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else null

    val isIndeterminate: Boolean
        get() = totalBytes <= 0L && status == DownloadStatus.DOWNLOADING
}
