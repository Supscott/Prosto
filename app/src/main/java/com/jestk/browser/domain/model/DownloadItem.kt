package com.jestk.browser.domain.model

data class DownloadItem @JvmOverloads constructor(
    val id: Long = 0,
    val url: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val mimeType: String? = null,
    val contentLength: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadId: Long = -1L
)
