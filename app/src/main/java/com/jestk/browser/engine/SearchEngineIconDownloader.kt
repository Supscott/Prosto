package com.jestk.browser.engine

import android.content.Context
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SearchEngineIconDownloader {

    private const val ICONS_DIR = "search_icons"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6000
    private const val MAX_REDIRECTS = 5

    fun getIconFile(context: Context, engineId: String): File {
        val dir = File(context.applicationContext.filesDir, ICONS_DIR).apply {
            if (!exists()) mkdirs()
        }
        return File(dir, "engine_$engineId.png")
    }

    suspend fun downloadIconForEngine(context: Context, engine: SearchEngine): String? = withContext(Dispatchers.IO) {
        val targetFile = getIconFile(context, engine.id)
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile.absolutePath
        }

        val primaryUrl = engine.iconUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val fallbackUrl = FaviconManager.extractFallbackFaviconUrl(engine.searchUrlTemplate)

        val candidateUrls = listOfNotNull(primaryUrl, fallbackUrl).distinct()
        for (urlStr in candidateUrls) {
            if (downloadToFile(urlStr, targetFile)) {
                FaviconManager.cacheFaviconPathForDomain(engine.searchUrlTemplate, targetFile.absolutePath, context)
                return@withContext targetFile.absolutePath
            }
        }
        null
    }

    suspend fun downloadIcons(context: Context, engines: List<SearchEngine>): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        for (engine in engines) {
            val localPath = downloadIconForEngine(context, engine)
            if (localPath != null) {
                result[engine.id] = localPath
            }
        }
        result
    }

    private fun downloadToFile(urlStr: String, destinationFile: File): Boolean {
        var currentUrl = urlStr
        var redirects = 0
        var connection: HttpURLConnection? = null

        try {
            while (redirects < MAX_REDIRECTS) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 HoriBrowser/1.0")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(url, location).toString()
                    }
                    redirects++
                    connection.disconnect()
                    continue
                }

                if (code in 200..299) {
                    destinationFile.parentFile?.mkdirs()
                    val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.renameTo(destinationFile)
                    } else {
                        tempFile.delete()
                        false
                    }
                }
                break
            }
        } catch (_: Throwable) {
            // Network errors are non-fatal; the app falls back to remote URL or letter initial
        } finally {
            connection?.disconnect()
        }
        return false
    }
}
