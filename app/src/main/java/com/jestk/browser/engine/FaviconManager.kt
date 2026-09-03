package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FaviconManager {

    fun extractFallbackFaviconUrl(url: String?): String? {
        if (url.isNullOrEmpty() || url.startsWith("about:")) return null
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            if (host.isNotBlank()) {
                "https://www.google.com/s2/favicons?sz=64&domain=${host.removePrefix("www.")}"
            } else {
                null
            }
        } catch (_: Exception) {
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
