package com.jestk.browser.domain.model

/**
 * What the "Download file?" prompt shows for a download the page started.
 * [sizeBytes] is null when the size is unknown; [host] is the host of the page that started it.
 */
data class DownloadConfirmRequest(
    val fileName: String,
    val sizeBytes: Long?,
    val host: String?,
)
