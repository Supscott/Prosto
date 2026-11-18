package com.jestk.browser.ui.viewmodel

/**
 * A download that could not be started, shown in an in-app pill until dismissed.
 * [id] is unique per failure so the pill restarts its auto-dismiss timer when a new one arrives.
 */
data class DownloadFailure(
    val id: Long,
    val fileName: String?,
    val message: String,
)
