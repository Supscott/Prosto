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
                val targetFile = File(dir, "favicon_${tabId}.png")
                FileOutputStream(targetFile).use { out ->
                    icon.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                targetFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
}
