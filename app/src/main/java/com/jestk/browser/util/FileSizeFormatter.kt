package com.jestk.browser.util

import android.content.res.Resources
import com.jestk.browser.R
import java.util.Locale

object FileSizeFormatter {
    fun formatFileSize(resources: Resources, bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        fun oneDecimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
        return when {
            gb >= 1.0 -> resources.getString(R.string.file_size_gb, oneDecimal(gb))
            mb >= 1.0 -> resources.getString(R.string.file_size_mb, oneDecimal(mb))
            kb >= 1.0 -> resources.getString(R.string.file_size_kb, oneDecimal(kb))
            else -> resources.getString(R.string.file_size_bytes, bytes.toString())
        }
    }

    fun formatProgressText(resources: Resources, bytesDownloaded: Long, totalBytes: Long): String {
        val downloadedStr = formatFileSize(resources, bytesDownloaded)
            .ifEmpty { resources.getString(R.string.file_size_bytes, "0") }
        return if (totalBytes > 0L) {
            val totalStr = formatFileSize(resources, totalBytes)
            val percent = ((bytesDownloaded * 100) / totalBytes).coerceIn(0, 100)
            "$downloadedStr / $totalStr ($percent%)"
        } else {
            downloadedStr
        }
    }

    fun formatSpeed(resources: Resources, bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return resources.getString(R.string.downloads_speed, formatFileSize(resources, bytesPerSec))
    }
}
