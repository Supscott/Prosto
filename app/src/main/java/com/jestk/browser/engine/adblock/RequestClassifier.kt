package com.jestk.browser.engine.adblock

/**
 * Derives an adblock resource type and a source URL for a WebView request.
 *
 * WebView does not expose the resource type, so it is inferred from request headers and the URL.
 * `Sec-Fetch-*` headers may be absent from `WebResourceRequest.requestHeaders`; CB-06 logs which
 * headers WebView actually supplies on a device. Pure Kotlin, never throws.
 */
object RequestClassifier {
    private val SCRIPT_EXT = setOf("js", "mjs")
    private val STYLE_EXT = setOf("css")
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp")
    private val FONT_EXT = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXT = setOf("mp4", "webm", "m3u8", "mpd", "ts", "mp3", "m4a", "ogg", "aac")
    private val HTML_EXT = setOf("html", "htm")

    fun type(url: String, isForMainFrame: Boolean, headers: Map<String, String>): RequestType {
        if (isForMainFrame) return RequestType.DOCUMENT
        fromFetchDest(header(headers, "Sec-Fetch-Dest"))?.let { return it }
        fromAccept(header(headers, "Accept"))?.let { return it }
        fromExtension(url)?.let { return it }
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme == "ws" || scheme == "wss") return RequestType.WEBSOCKET
        return RequestType.OTHER
    }

    fun sourceUrl(
        url: String,
        isForMainFrame: Boolean,
        headers: Map<String, String>,
        tabUrl: String?,
    ): String {
        if (isForMainFrame) return url
        header(headers, "Referer")?.trim()?.takeIf { isHttp(it) }?.let { return it }
        header(headers, "Origin")?.trim()?.takeIf { isHttp(it) }?.let { return it.trimEnd('/') + "/" }
        return tabUrl?.takeIf { it.isNotBlank() } ?: url
    }

    private fun header(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun isHttp(value: String): Boolean {
        val lower = value.lowercase()
        val rest = when {
            lower.startsWith("https://") -> lower.removePrefix("https://")
            lower.startsWith("http://") -> lower.removePrefix("http://")
            else -> return false
        }
        return rest.isNotEmpty() && rest[0] != '/'
    }

    private fun fromFetchDest(value: String?): RequestType? = when (value?.trim()?.lowercase()) {
        "document", "iframe", "frame", "embed", "object" -> RequestType.SUBDOCUMENT
        "script", "worker", "sharedworker", "serviceworker" -> RequestType.SCRIPT
        "style" -> RequestType.STYLESHEET
        "image" -> RequestType.IMAGE
        "font" -> RequestType.FONT
        "audio", "video", "track" -> RequestType.MEDIA
        "empty" -> RequestType.XMLHTTPREQUEST
        "report" -> RequestType.PING
        else -> null
    }

    private fun fromAccept(value: String?): RequestType? {
        val accept = value?.trim()?.lowercase() ?: return null
        return when {
            accept.startsWith("text/html") -> RequestType.SUBDOCUMENT
            accept.contains("text/css") -> RequestType.STYLESHEET
            accept.startsWith("image/") -> RequestType.IMAGE
            accept.startsWith("video/") || accept.startsWith("audio/") -> RequestType.MEDIA
            accept.startsWith("application/json") -> RequestType.XMLHTTPREQUEST
            else -> null
        }
    }

    private fun fromExtension(url: String): RequestType? {
        if (!isHttp(url)) return null
        val afterScheme = url.substringAfter("://")
        val path = afterScheme.substringBefore('#').substringBefore('?')
        val slash = path.indexOf('/')
        if (slash < 0) return null
        val file = path.substring(slash).substringAfterLast('/')
        val dot = file.lastIndexOf('.')
        if (dot < 0) return null
        val ext = file.substring(dot + 1).lowercase()
        return when (ext) {
            in SCRIPT_EXT -> RequestType.SCRIPT
            in STYLE_EXT -> RequestType.STYLESHEET
            in IMAGE_EXT -> RequestType.IMAGE
            in FONT_EXT -> RequestType.FONT
            in MEDIA_EXT -> RequestType.MEDIA
            in HTML_EXT -> RequestType.SUBDOCUMENT
            else -> null
        }
    }
}
