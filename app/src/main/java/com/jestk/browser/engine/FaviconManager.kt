package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FaviconManager {

    fun extractFallbackFaviconUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host.isBlank()) return null
            if (host == "localhost" ||
                host.startsWith("127.") ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host.startsWith("172.16.") ||
                host.endsWith(".local")
            ) {
                return null
            }
            "https://www.google.com/s2/favicons?sz=128&domain=${host.removePrefix("www.")}"
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun saveFavicon(context: Context, tabId: String, icon: Bitmap): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "favicons").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(dir, "favicon_${tabId}_${System.currentTimeMillis()}.png")
                FileOutputStream(targetFile).use { out ->
                    icon.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Clean up previous tab favicon files for this tab
                dir.listFiles { _, name ->
                    (name.startsWith("favicon_${tabId}_") || name == "favicon_${tabId}.png") && name != targetFile.name
                }?.forEach { it.delete() }
                targetFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }

    fun deleteTabFavicons(context: Context, tabId: String) {
        try {
            val dir = File(context.filesDir, "favicons")
            if (dir.exists()) {
                dir.listFiles { _, name ->
                    name.startsWith("favicon_${tabId}_") || name == "favicon_${tabId}.png"
                }?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    fun toImageModel(urlOrPath: String?): Any? {
        if (urlOrPath.isNullOrBlank()) return null
        if (urlOrPath.startsWith("file:///android_asset/") || urlOrPath.startsWith("file:/android_asset/")) {
            return Uri.parse(urlOrPath)
        }
        if (urlOrPath.startsWith("/android_asset/")) {
            return Uri.parse("file://$urlOrPath")
        }
        return if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://")) {
            File(urlOrPath.removePrefix("file://"))
        } else {
            urlOrPath
        }
    }

    suspend fun persistFaviconForFavorite(context: Context, sourcePath: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanPath = sourcePath.removePrefix("file://")
                val sourceFile = File(cleanPath)
                if (!sourceFile.exists() || !sourceFile.isFile) return@withContext null
                val dir = File(context.filesDir, "favicons").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(dir, "fav_${java.util.UUID.randomUUID()}.png")
                sourceFile.copyTo(targetFile, overwrite = true)
                targetFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
}
