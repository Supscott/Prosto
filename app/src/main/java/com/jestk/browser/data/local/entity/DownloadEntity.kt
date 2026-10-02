package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["downloadId"]),
        Index(value = ["status"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String?,
    val contentLength: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadId: Long = -1,
    val status: String = "COMPLETED",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val etag: String? = null
)
