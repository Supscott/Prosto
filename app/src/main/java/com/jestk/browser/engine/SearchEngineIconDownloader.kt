package com.jestk.browser.engine

import android.content.Context
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object SearchEngineIconDownloader {
    private const val ICONS_DIR = "search_icons"
    private const val CONNECT_TIMEOUT_SECONDS = 6L
    private const val READ_TIMEOUT_SECONDS = 6L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getIconFile(
        context: Context,
        engineId: String,
    ): File {
        val dir =
            File(context.applicationContext.filesDir, ICONS_DIR).apply {
                if (!exists()) mkdirs()
            }
        return File(dir, "engine_$engineId.png")
    }

    suspend fun downloadIconForEngine(
        context: Context,
        engine: SearchEngine,
    ): String? =
        withContext(Dispatchers.IO) {
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

    suspend fun downloadIcons(
        context: Context,
        engines: List<SearchEngine>,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, String>()
            for (engine in engines) {
                val localPath = downloadIconForEngine(context, engine)
                if (localPath != null) {
                    result[engine.id] = localPath
                }
            }
            result
        }

    private fun downloadToFile(
        urlStr: String,
        destinationFile: File,
    ): Boolean {
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 ProstoBrowser/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false

                destinationFile.parentFile?.mkdirs()
                val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
                tempFile.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.renameTo(destinationFile)
                } else {
                    tempFile.delete()
                    false
                }
            }
        } catch (_: Throwable) {
            // Network errors are non-fatal; the app falls back to remote URL or letter initial
            false
        }
    }
}
