package com.jestk.browser.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Remembers the icon found on a site, or that it has none, so a cold start does not ask every
 * site again. One file per origin in [dir]: `<sha1>.icon` holds the bytes, `<sha1>.miss` is an
 * empty marker for an origin where no candidate was an image. The file's modification time is the
 * age. A hit is valid for [HIT_TTL_MS], a miss for [MISS_TTL_MS]. It lives in the cache directory,
 * which the system may clear at any time.
 */
class SiteIconDiskCache(private val dir: File) {
    sealed interface Entry {
        class Hit(val icon: SiteIconBytes) : Entry

        /** No candidate was an image the last time; do not ask again yet. */
        object Missing : Entry
    }

    /** The fresh entry of [origin], or null when there is none (expired files are deleted). */
    fun read(origin: String, now: Long): Entry? {
        val icon = fileFor(origin, ICON_SUFFIX)
        if (icon.isFile) {
            if (now - icon.lastModified() < HIT_TTL_MS) {
                val bytes = runCatching { icon.readBytes() }.getOrNull()
                val mime = bytes?.let { SiteIconDownloader.sniffImageMime(it) }
                if (bytes != null && mime != null) return Entry.Hit(SiteIconBytes(bytes, mime))
            }
            icon.delete()
        }
        val miss = fileFor(origin, MISS_SUFFIX)
        if (miss.isFile) {
            if (now - miss.lastModified() < MISS_TTL_MS) return Entry.Missing
            miss.delete()
        }
        return null
    }

    fun writeHit(origin: String, icon: SiteIconBytes, now: Long) {
        fileFor(origin, MISS_SUFFIX).delete()
        write(fileFor(origin, ICON_SUFFIX), icon.bytes, now)
    }

    fun writeMiss(origin: String, now: Long) {
        fileFor(origin, ICON_SUFFIX).delete()
        write(fileFor(origin, MISS_SUFFIX), ByteArray(0), now)
    }

    private fun write(target: File, bytes: ByteArray, now: Long) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            target.parentFile?.mkdirs()
            temp.writeBytes(bytes)
            temp.setLastModified(now)
            if (!temp.renameTo(target)) temp.delete()
        } catch (_: IOException) {
            // A cache that cannot be written only costs a refetch.
            temp.delete()
        }
    }

    private fun fileFor(origin: String, suffix: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(origin.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + suffix)
    }

    companion object {
        const val HIT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000
        private const val ICON_SUFFIX = ".icon"
        private const val MISS_SUFFIX = ".miss"

        fun forContext(context: Context): SiteIconDiskCache =
            SiteIconDiskCache(File(context.applicationContext.cacheDir, "site_icons"))
    }
}
