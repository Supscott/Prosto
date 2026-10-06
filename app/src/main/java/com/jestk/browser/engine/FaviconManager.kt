package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object FaviconManager {

    private var applicationContext: Context? = null
    private val domainFaviconCache = ConcurrentHashMap<String, String>()
    @Volatile
    private var isDomainCacheIndexed = false
    private val cacheInitMutex = Any()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        applicationContext = appCtx
        synchronized(cacheInitMutex) {
            val dir = File(appCtx.filesDir, "favicons")
            indexDomainFavicons(dir)
            isDomainCacheIndexed = true
        }
    }

    private fun ensureDomainCacheIndexed(context: Context? = applicationContext) {
        if (isDomainCacheIndexed) return
        synchronized(cacheInitMutex) {
            if (isDomainCacheIndexed) return
            val ctx = context ?: applicationContext ?: return
            val dir = File(ctx.filesDir, "favicons")
            indexDomainFavicons(dir)
            isDomainCacheIndexed = true
        }
    }

    private fun indexDomainFavicons(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles { _, name ->
            name.startsWith("domain_") && name.endsWith(".png")
        } ?: return
        for (file in files) {
            val hostPart = file.name.removePrefix("domain_").removeSuffix(".png")
            if (hostPart.isNotBlank() && file.exists() && file.isFile && file.length() > 0) {
                domainFaviconCache[hostPart.lowercase()] = file.absolutePath
            }
        }
    }

    fun extractHost(urlOrDomain: String?): String? {
        if (urlOrDomain.isNullOrBlank()) return null
        if (urlOrDomain == "about:blank" || urlOrDomain.startsWith("about:")) return null
        return try {
            val clean = if (urlOrDomain.contains("://")) urlOrDomain else "https://$urlOrDomain"
            val uri = Uri.parse(clean)
            val host = uri.host?.lowercase()?.trim() ?: return null
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
            host.removePrefix("www.")
        } catch (_: Throwable) {
            null
        }
    }

    fun sanitizeHost(host: String): String {
        return host.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    }

    fun getCachedFaviconForUrl(url: String?): String? {
        val host = extractHost(url) ?: return null
        return getCachedFaviconForDomain(host)
    }

    fun getCachedFaviconForDomain(domain: String?): String? {
        val host = extractHost(domain) ?: return null
        ensureDomainCacheIndexed()
        val path = domainFaviconCache[host] ?: return null
        val file = File(path)
        return if (file.exists() && file.isFile && file.length() > 0) {
            path
        } else {
            domainFaviconCache.remove(host)
            null
        }
    }

    suspend fun cacheFaviconForDomain(
        domainOrUrl: String,
        icon: Bitmap,
        context: Context? = applicationContext
    ): String? = withContext(Dispatchers.IO) {
        val host = extractHost(domainOrUrl) ?: return@withContext null
        val ctx = context ?: applicationContext ?: return@withContext null
        init(ctx)
        try {
            val dir = File(ctx.filesDir, "favicons").apply {
                if (!exists()) mkdirs()
            }
            val sanitized = sanitizeHost(host)
            val domainFile = File(dir, "domain_$sanitized.png")
            FileOutputStream(domainFile).use { out ->
                icon.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val path = domainFile.absolutePath
            domainFaviconCache[host] = path
            path
        } catch (_: Exception) {
            null
        }
    }

    fun cacheFaviconPathForDomain(
        domainOrUrl: String,
        filePath: String,
        context: Context? = applicationContext
    ): String? {
        val host = extractHost(domainOrUrl) ?: return null
        val cleanPath = filePath.removePrefix("file://")
        val sourceFile = File(cleanPath)
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0) return null

        val ctx = context ?: applicationContext
        if (ctx != null) {
            try {
                val dir = File(ctx.filesDir, "favicons").apply {
                    if (!exists()) mkdirs()
                }
                val sanitized = sanitizeHost(host)
                val targetFile = File(dir, "domain_$sanitized.png")
                if (sourceFile.absolutePath != targetFile.absolutePath) {
                    sourceFile.copyTo(targetFile, overwrite = true)
                }
                val path = targetFile.absolutePath
                domainFaviconCache[host] = path
                return path
            } catch (_: Exception) {}
        }
        domainFaviconCache[host] = sourceFile.absolutePath
        return sourceFile.absolutePath
    }

    fun isValidLocalFavicon(urlOrPath: String?): Boolean {
        if (urlOrPath.isNullOrBlank()) return false
        if (urlOrPath.startsWith("file:///android_asset/") || urlOrPath.startsWith("/android_asset/")) return true
        val cleanPath = urlOrPath.removePrefix("file://")
        return if (cleanPath.startsWith("/")) {
            try {
                val file = File(cleanPath)
                file.exists() && file.isFile && file.length() > 0
            } catch (_: Throwable) {
                false
            }
        } else false
    }

    internal fun clearMemoryCache() {
        domainFaviconCache.clear()
        isDomainCacheIndexed = false
    }

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

    suspend fun saveFavicon(
        context: Context,
        tabId: String,
        icon: Bitmap,
        pageUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        init(context)
        try {
            val dir = File(context.applicationContext.filesDir, "favicons").apply {
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

            if (!pageUrl.isNullOrBlank()) {
                cacheFaviconForDomain(pageUrl, icon, context)
            }

            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteTabFavicons(tabId: String) {
        applicationContext?.let { ctx ->
            deleteTabFavicons(ctx, tabId)
        }
    }

    fun deleteTabFavicons(context: Context, tabId: String) {
        init(context)
        try {
            val dir = File(context.applicationContext.filesDir, "favicons")
            if (dir.exists()) {
                dir.listFiles { _, name ->
                    name.startsWith("favicon_${tabId}_") || name == "favicon_${tabId}.png"
                }?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    fun deleteAllFavicons(context: Context? = applicationContext) {
        clearMemoryCache()
        context?.let { ctx ->
            try {
                val dir = File(ctx.applicationContext.filesDir, "favicons")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } catch (_: Throwable) {}
        }
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

    suspend fun persistFaviconForFavorite(sourcePath: String): String? {
        val ctx = applicationContext ?: return null
        return persistFaviconForFavorite(ctx, sourcePath)
    }

    suspend fun persistFaviconForFavorite(context: Context, sourcePath: String): String? =
        withContext(Dispatchers.IO) {
            init(context)
            try {
                val cleanPath = sourcePath.removePrefix("file://")
                val sourceFile = File(cleanPath)
                if (!sourceFile.exists() || !sourceFile.isFile) return@withContext null
                val dir = File(context.applicationContext.filesDir, "favicons").apply {
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
