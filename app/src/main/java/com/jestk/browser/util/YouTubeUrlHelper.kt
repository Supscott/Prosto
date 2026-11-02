package com.jestk.browser.util

import android.net.Uri

/**
 * Utility for parsing and transforming YouTube video URLs into clean, privacy-enhanced embed URLs.
 */
object YouTubeUrlHelper {

    private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    /**
     * Extracts the 11-character YouTube video ID from supported YouTube URLs:
     * - youtube.com/watch?v=ID
     * - m.youtube.com/watch?v=ID
     * - youtu.be/ID
     * - youtube.com/shorts/ID
     * - m.youtube.com/shorts/ID
     * - youtube.com/embed/ID
     * - youtube-nocookie.com/embed/ID
     *
     * Returns null if [url] is not a valid YouTube video URL (e.g. channel page, homepage, non-YouTube URL).
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null

        val isYouTubeHost = host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtu.be" ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com")

        if (!isYouTubeHost) return null

        // 1. Check youtu.be/VIDEO_ID
        if (host == "youtu.be") {
            val pathId = uri.pathSegments.firstOrNull()
            if (pathId != null && isValidVideoId(pathId)) {
                return pathId
            }
        }

        // 2. Check query parameter ?v=VIDEO_ID
        val queryV = runCatching { uri.getQueryParameter("v") }.getOrNull()
        if (queryV != null && isValidVideoId(queryV)) {
            return queryV
        }

        // 3. Check /shorts/VIDEO_ID, /embed/VIDEO_ID, /v/VIDEO_ID
        val segments = uri.pathSegments
        if (segments.size >= 2) {
            val first = segments[0].lowercase()
            if (first == "shorts" || first == "embed" || first == "v") {
                val candidateId = segments[1]
                if (isValidVideoId(candidateId)) {
                    return candidateId
                }
            }
        }

        return null
    }

    /**
     * Validates whether [id] matches the standard 11-character YouTube video ID format.
     */
    fun isValidVideoId(id: String): Boolean {
        return VIDEO_ID_REGEX.matches(id)
    }

    /**
     * Parses start timestamp from query parameters ('t' or 'start') in seconds.
     * Supports formats like '120', '120s', '1h2m3s', '2m15s'.
     */
    fun extractStartTimeSeconds(url: String?): Int {
        if (url.isNullOrBlank()) return 0
        val trimmed = url.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return 0
        val tParam = runCatching { uri.getQueryParameter("t") ?: uri.getQueryParameter("start") }.getOrNull() ?: return 0

        // Direct integer: "120" or "120s"
        tParam.toIntOrNull()?.let { if (it > 0) return it }
        if (tParam.endsWith("s", ignoreCase = true)) {
            tParam.dropLast(1).toIntOrNull()?.let { if (it > 0) return it }
        }

        // Formats with h/m/s e.g. 1h2m3s, 2m30s
        var totalSeconds = 0
        var foundAny = false
        val hMatch = Regex("(\\d+)h", RegexOption.IGNORE_CASE).find(tParam)
        val mMatch = Regex("(\\d+)m", RegexOption.IGNORE_CASE).find(tParam)
        val sMatch = Regex("(\\d+)s", RegexOption.IGNORE_CASE).find(tParam)

        hMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            totalSeconds += it * 3600
            foundAny = true
        }
        mMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            totalSeconds += it * 60
            foundAny = true
        }
        sMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            totalSeconds += it
            foundAny = true
        }

        return if (foundAny) totalSeconds else 0
    }

    /**
     * Builds privacy-enhanced embed URL for the clean player overlay.
     */
    fun buildCleanEmbedUrl(videoId: String, startSeconds: Int = 0): String {
        val base = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
        return if (startSeconds > 0) {
            "$base&start=$startSeconds"
        } else {
            base
        }
    }

    /**
     * Builds the original web URL for "Open on YouTube".
     */
    fun buildOriginalWatchUrl(videoId: String, startSeconds: Int = 0): String {
        val base = "https://m.youtube.com/watch?v=$videoId"
        return if (startSeconds > 0) {
            "$base&t=$startSeconds"
        } else {
            base
        }
    }
}
