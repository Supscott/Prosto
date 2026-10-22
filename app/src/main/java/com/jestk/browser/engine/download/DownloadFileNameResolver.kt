package com.jestk.browser.engine.download

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object DownloadFileNameResolver {

    private const val TAG = "DownloadFileNameResolver"

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\r\\n\\t\\u0000-\\u001f]")
    private val RFC5987_PATTERN = Pattern.compile("filename\\*\\s*=\\s*([^']*)'([^']*)'([^\";\\s]*)", Pattern.CASE_INSENSITIVE)
    private val RFC2616_QUOTED_PATTERN = Pattern.compile("filename\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE)
    private val RFC2616_TOKEN_PATTERN = Pattern.compile("filename\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE)

    private val KNOWN_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "wav", "flac", "opus", "wma", "mid", "midi", "amr"
    )
    private val KNOWN_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "3gp", "m4v", "ts"
    )
    private val KNOWN_DOC_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "rtf"
    )
    private val KNOWN_ARCHIVE_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "dmg", "iso"
    )
    private val KNOWN_IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "tiff"
    )

    private val KNOWN_EXTENSIONS = KNOWN_AUDIO_EXTENSIONS +
            KNOWN_VIDEO_EXTENSIONS +
            KNOWN_DOC_EXTENSIONS +
            KNOWN_ARCHIVE_EXTENSIONS +
            KNOWN_IMAGE_EXTENSIONS

    private val MIME_TO_EXT_MAP = mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/x-mp3" to "mp3",
        "audio/x-mpeg" to "mp3",
        "audio/mp4" to "m4a",
        "audio/m4a" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/aac" to "aac",
        "audio/ogg" to "ogg",
        "audio/vorbis" to "ogg",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/wave" to "wav",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/opus" to "opus",
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "video/x-matroska" to "mkv",
        "video/quicktime" to "mov",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/x-zip-compressed" to "zip",
        "application/x-rar-compressed" to "rar",
        "application/x-7z-compressed" to "7z",
        "application/vnd.android.package-archive" to "apk",
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/svg+xml" to "svg"
    )

    /**
     * Resolves a sanitized, user-friendly filename for downloads, avoiding the .bin bug.
     */
    fun resolveFileName(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): String {
        // 1. Try Content-Disposition
        var extractedName = parseContentDisposition(contentDisposition)

        // 2. If no valid filename from header, try extracting from URL path
        if (extractedName.isNullOrBlank()) {
            extractedName = extractFileNameFromUrl(url)
        }

        // 3. Fallback name if nothing could be extracted
        if (extractedName.isNullOrBlank() || extractedName == "/" || extractedName == ".") {
            val prefix = when {
                mimeType?.startsWith("audio/") == true -> "audio"
                mimeType?.startsWith("video/") == true -> "video"
                mimeType?.startsWith("image/") == true -> "image"
                else -> "download"
            }
            extractedName = "${prefix}_${System.currentTimeMillis()}"
        }

        // 4. Sanitize characters (preserve Unicode / Cyrillic, remove filesystem illegal characters)
        var sanitized = sanitizeFileName(extractedName)

        // 5. Ensure valid extension without turning MP3 / media into .bin
        sanitized = fixOrAppendExtension(sanitized, mimeType, url)

        return sanitized.ifBlank { "download_${System.currentTimeMillis()}" }
    }

    /**
     * Resolves a non-colliding File in [targetDir] by appending " (1)", " (2)", etc.
     */
    fun resolveUniqueFile(targetDir: File, fileName: String): File {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val initialFile = File(targetDir, fileName)
        if (!initialFile.exists()) {
            return initialFile
        }

        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var index = 1
        var candidate: File
        do {
            candidate = File(targetDir, "$baseName ($index)$extension")
            index++
        } while (candidate.exists())

        return candidate
    }

    fun parseContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null

        // 1. RFC 5987 / RFC 6266 filename*=charset'lang'encoded-value
        val rfc5987Matcher = RFC5987_PATTERN.matcher(contentDisposition)
        if (rfc5987Matcher.find()) {
            val charsetName = rfc5987Matcher.group(1).ifBlank { "UTF-8" }
            val rawValue = rfc5987Matcher.group(3)
            if (!rawValue.isNullOrBlank()) {
                try {
                    val decoded = URLDecoder.decode(rawValue, charsetName)
                    if (decoded.isNotBlank()) {
                        return File(decoded).name
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to decode RFC 5987 filename with charset $charsetName", e)
                    try {
                        val fallback = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
                        if (fallback.isNotBlank()) {
                            return File(fallback).name
                        }
                    } catch (fallbackError: Throwable) {
                        Log.w(TAG, "Failed to decode RFC 5987 filename with the UTF-8 fallback", fallbackError)
                    }
                }
            }
        }

        // 2. Standard filename="..."
        val quotedMatcher = RFC2616_QUOTED_PATTERN.matcher(contentDisposition)
        if (quotedMatcher.find()) {
            val value = quotedMatcher.group(1)
            if (!value.isNullOrBlank()) {
                return File(value).name
            }
        }

        // 3. Token filename=...
        val tokenMatcher = RFC2616_TOKEN_PATTERN.matcher(contentDisposition)
        if (tokenMatcher.find()) {
            val value = tokenMatcher.group(1)
            if (!value.isNullOrBlank()) {
                return File(value).name
            }
        }

        return null
    }

    fun extractFileNameFromUrl(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val cleanUrl = url.substringBefore('#').substringBefore('?')
            val uri = Uri.parse(cleanUrl)
            val path = uri.path ?: return null
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment.isNotBlank()) {
                URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to parse file name from URL, falling back to the last path segment", e)
            val afterSlash = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            if (afterSlash.isNotBlank()) afterSlash else null
        }
    }

    fun sanitizeFileName(name: String): String {
        // Strip path traversal and directory separators
        val base = File(name).name
        // Replace illegal filesystem characters with underscore
        var cleaned = base.replace(ILLEGAL_CHARS, "_").trim()
        // Trim leading and trailing dots or spaces
        cleaned = cleaned.trimStart('.').trimEnd('.', ' ')
        return cleaned
    }

    private fun fixOrAppendExtension(fileName: String, mimeType: String?, originalUrl: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        val existingExt = if (lastDotIndex != -1 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            null
        }

        // If existing extension is .bin and we have a valid media/audio mime or URL indicating mp3/etc:
        val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase()

        // Case A: File already has a known valid extension (e.g. .mp3, .pdf, .zip, .png)
        if (existingExt != null && existingExt != "bin") {
            // Keep the extension! Never let generic application/octet-stream turn .mp3 into .bin!
            return fileName
        }

        // Case B: Existing extension is .bin or missing altogether
        // Check if original URL path contains a known extension before query parameters
        val urlExt = extractExtensionFromUrl(originalUrl)
        if (urlExt != null && urlExt in KNOWN_EXTENSIONS) {
            val nameWithoutExt = if (existingExt == "bin") fileName.substring(0, lastDotIndex) else fileName
            return "$nameWithoutExt.$urlExt"
        }

        // Check MIME type map
        val mappedExt = cleanMime?.let { MIME_TO_EXT_MAP[it] }
            ?: cleanMime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }

        if (!mappedExt.isNullOrBlank() && mappedExt != "bin") {
            val nameWithoutExt = if (existingExt == "bin") fileName.substring(0, lastDotIndex) else fileName
            return "$nameWithoutExt.$mappedExt"
        }

        // If already has .bin, keep it; if no extension at all, don't force .bin unless mime is binary
        return if (existingExt == null && cleanMime?.contains("octet-stream") == true) {
            "$fileName.bin"
        } else {
            fileName
        }
    }

    private fun extractExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val lastSegment = path.substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        return if (dot != -1 && dot < lastSegment.length - 1) {
            lastSegment.substring(dot + 1).lowercase()
        } else {
            null
        }
    }
}
