package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["downloadId"])
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
    val downloadId: Long = -1
)
