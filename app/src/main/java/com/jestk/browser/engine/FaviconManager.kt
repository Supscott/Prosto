package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Typed description of where a favicon image comes from.
 *
 * The variants mirror the three sources the app can render: a packaged asset, a file in the
 * app-private storage, or a remote URL that has to be fetched over the network. Using a sealed
 * type instead of a bare `Any?` keeps the call sites exhaustive and documents the contract of
 * every conversion.
 */
sealed interface FaviconImageModel {
    /** A resource packaged with the application, e.g. `file:///android_asset/icons/google.svg`. */
    data class Asset(val uri: Uri) : FaviconImageModel

    /** A file stored on the local filesystem, e.g. a cached favicon in the app-private files dir. */
    data class LocalFile(val file: File) : FaviconImageModel

    /** A remote image that has to be downloaded before it can be displayed. */
    data class Remote(val url: String) : FaviconImageModel
}

object FaviconManager {

    private const val TAG = "FaviconManager"

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

    fun getApplicationContext(): Context? = applicationContext

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
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to extract host from URL", e)
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
            writeIconAtomically(domainFile, icon)
            val path = domainFile.absolutePath
            domainFaviconCache[host] = path
            path
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache favicon bitmap for domain $host", e)
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
                    val bytes = sourceFile.readBytes()
                    if (!targetFile.exists() || targetFile.length() != bytes.size.toLong() || !targetFile.readBytes().contentEquals(bytes)) {
                        writeBytesAtomically(targetFile, bytes)
                    }
                }
                val path = targetFile.absolutePath
                domainFaviconCache[host] = path
                return path
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy favicon into app storage for domain $host, using source path", e)
            }
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
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to validate local favicon path", e)
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
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to build fallback favicon URL", e)
            null
        }
    }

    /**
     * Persists the favicon of a tab under a stable per-tab path (`favicon_<tabId>.png`).
     *
     * The path never changes for a given tab: pages report their icon repeatedly (every navigation
     * and, for dynamic sites, several times per second), and a timestamped file would publish a new
     * URL on every report. That forces every UI bound to the favicon to drop and reload the image,
     * which shows up as a flickering icon in the dock.
     *
     * The file is replaced atomically and only when the bitmap actually changed, so the
     * modification time stays stable and image loaders keep their cache entry valid.
     */
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
            val targetFile = File(dir, "favicon_$tabId.png")
            writeIconAtomically(targetFile, icon)

            // Drop legacy timestamped files left by older versions of the app.
            dir.listFiles { _, name ->
                name.startsWith("favicon_${tabId}_")
            }?.forEach { it.delete() }

            if (!pageUrl.isNullOrBlank()) {
                cacheFaviconForDomain(pageUrl, icon, context)
            }

            targetFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save favicon for tab $tabId", e)
            null
        }
    }

    /**
     * Writes [icon] as PNG to [target], replacing the file atomically.
     *
     * The previous content is kept when the encoded bytes are identical, so the modification time
     * of an unchanged icon stays untouched and image loaders do not discard their cache entry.
     */
    private fun writeIconAtomically(target: File, icon: Bitmap) {
        val encoded = java.io.ByteArrayOutputStream().use { out ->
            icon.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        if (target.exists() && target.length() == encoded.size.toLong() && target.readBytes().contentEquals(encoded)) {
            return
        }
        writeBytesAtomically(target, encoded)
    }

    /**
     * Writes [bytes] to [target] through a temporary file so readers never observe a partial image.
     */
    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out -> out.write(bytes) }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    FileOutputStream(target).use { out -> out.write(bytes) }
                }
            }
        } finally {
            if (temp.exists()) temp.delete()
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
                    name == "favicon_$tabId.png" ||
                        name == "favicon_$tabId.png.tmp" ||
                        name.startsWith("favicon_${tabId}_")
                }?.forEach { it.delete() }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to delete favicons for tab $tabId", e)
        }
    }

    fun deleteAllFavicons(context: Context? = applicationContext) {
        clearMemoryCache()
        context?.let { ctx ->
            try {
                val dir = File(ctx.applicationContext.filesDir, "favicons")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to delete all cached favicons", e)
            }
        }
    }

    /**
     * Converts a favicon location into a typed [FaviconImageModel].
     *
     * The mapping is:
     * - `null` or a blank string -> `null`
     * - `file:///android_asset/...` or `file:/android_asset/...` -> [FaviconImageModel.Asset]
     * - `/android_asset/...` -> [FaviconImageModel.Asset] carrying a `file://` URI
     * - a path starting with `/` or `file://` -> [FaviconImageModel.LocalFile]
     * - anything else -> [FaviconImageModel.Remote]
     */
    fun toFaviconImageModel(urlOrPath: String?): FaviconImageModel? {
        if (urlOrPath.isNullOrBlank()) return null
        if (urlOrPath.startsWith("file:///android_asset/") || urlOrPath.startsWith("file:/android_asset/")) {
            return FaviconImageModel.Asset(Uri.parse(urlOrPath))
        }
        if (urlOrPath.startsWith("/android_asset/")) {
            return FaviconImageModel.Asset(Uri.parse("file://$urlOrPath"))
        }
        return if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://")) {
            FaviconImageModel.LocalFile(File(urlOrPath.removePrefix("file://")))
        } else {
            FaviconImageModel.Remote(urlOrPath)
        }
    }

    /**
     * Maps a favicon location to the runtime value Coil accepts as an image model.
     *
     * This is the bridge for the call sites that hand the value straight to `AsyncImage`.
     */
    fun toImageModelForCoil(urlOrPath: String?): Any? = toFaviconImageModel(urlOrPath)?.toCoilModel()

    /**
     * Legacy entry point kept for the existing UI call sites, which pass the result straight to
     * Coil. It returns exactly the same runtime values as before (a `Uri`, a `File`, a `String`
     * or `null`).
     *
     * New code should prefer [toFaviconImageModel], which is type safe and exhaustive.
     */
    fun toImageModel(urlOrPath: String?): Any? = toImageModelForCoil(urlOrPath)

    /**
     * Releases the in-memory state of the manager.
     *
     * The manager owns no coroutine scope of its own: every write runs inside the scope of its
     * caller (`withContext(Dispatchers.IO)`), so there is nothing to cancel here. The method
     * exists to give the app a single, explicit teardown hook for the favicon subsystem.
     */
    fun shutdown() {
        clearMemoryCache()
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist favicon for favorite", e)
                null
            }
        }
}

/**
 * Maps the typed favicon model to the runtime value Coil accepts as an image model:
 * a `Uri` for packaged assets, a `File` for local files and the URL string for remote images.
 */
fun FaviconImageModel.toCoilModel(): Any =
    when (this) {
        is FaviconImageModel.Asset -> uri
        is FaviconImageModel.LocalFile -> file
        is FaviconImageModel.Remote -> url
    }
