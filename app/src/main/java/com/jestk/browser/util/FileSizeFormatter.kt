package com.jestk.browser.util

import java.util.Locale

object FileSizeFormatter {
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatProgressText(bytesDownloaded: Long, totalBytes: Long): String {
        val downloadedStr = formatFileSize(bytesDownloaded).ifEmpty { "0 B" }
        return if (totalBytes > 0L) {
            val totalStr = formatFileSize(totalBytes)
            val percent = ((bytesDownloaded * 100) / totalBytes).coerceIn(0, 100)
            "$downloadedStr / $totalStr ($percent%)"
        } else {
            downloadedStr
        }
    }
}
