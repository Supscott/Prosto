package com.jestk.browser.engine

import android.content.Context
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SearchEngineIconDownloader {
    private const val ICONS_DIR = "search_icons"

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

    /**
     * Saves the engine's own site icon (see [SiteIcon]) to [getIconFile]. Engines with a bundled
     * icon and engines on a private host are never downloaded.
     */
    suspend fun downloadIconForEngine(
        context: Context,
        engine: SearchEngine,
        fetchIcon: suspend (origin: String) -> SiteIconBytes? = { SiteIconDownloader.fetch(it) },
    ): String? =
        withContext(Dispatchers.IO) {
            val targetFile = getIconFile(context, engine.id)
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext targetFile.absolutePath
            }
            if (BundledSearchIcons.isBundledRef(engine.iconUrl)) return@withContext null

            val origin = SiteIcon.originOfRef(engine.iconUrl) ?: SiteIcon.originOf(engine.searchUrlTemplate)
                ?: return@withContext null
            val icon = fetchIcon(origin) ?: return@withContext null
            if (!writeAtomically(icon.bytes, targetFile)) return@withContext null
            FaviconManager.cacheFaviconPathForDomain(engine.searchUrlTemplate, targetFile.absolutePath, context)
            targetFile.absolutePath
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

    private fun writeAtomically(
        bytes: ByteArray,
        destinationFile: File,
    ): Boolean {
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        return try {
            destinationFile.parentFile?.mkdirs()
            tempFile.writeBytes(bytes)
            tempFile.renameTo(destinationFile)
        } catch (_: Throwable) {
            // Non-fatal; the badge falls back to the letter tile
            tempFile.delete()
            false
        }
    }
}
