package com.jestk.browser.engine

import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import java.io.File

/**
 * Latest completed download of [url] whose file is still on disk, or null.
 * Only http(s) URLs qualify: data: and blob: downloads are never treated as duplicates.
 */
internal suspend fun findExistingDownload(
    repository: DownloadRepository,
    url: String,
    fileExists: (String) -> Boolean = { File(it).exists() },
): DownloadItem? {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
    return repository.findCompletedByUrl(url).firstOrNull { fileExists(it.filePath) }
}
