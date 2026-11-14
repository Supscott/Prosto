package com.jestk.browser.engine.adblock


data class ParsedUrl(
    val host: String,
    val path: String,
    val rawUrl: String,
)

internal object UrlParser {

    private val lastParsedUrl = ThreadLocal<ParsedUrl?>()

    fun parseUrl(url: String?): ParsedUrl? {
        if (url.isNullOrBlank()) return null
        val cached = lastParsedUrl.get()
        if (cached != null && (cached.rawUrl === url || cached.rawUrl == url)) {
            return cached
        }
        val parsed = extractHostAndPath(url)
        lastParsedUrl.set(parsed)
        return parsed
    }

    internal fun extractHostAndPath(url: String): ParsedUrl? {
        var start = 0
        val len = url.length
        while (start < len && url[start] <= ' ') {
            start++
        }
        var end = len
        while (end > start && url[end - 1] <= ' ') {
            end--
        }
        if (start >= end) return null

        if (url.regionMatches(start, "data:", 0, 5, ignoreCase = true) ||
            url.regionMatches(start, "javascript:", 0, 11, ignoreCase = true) ||
            url.regionMatches(start, "about:", 0, 6, ignoreCase = true) ||
            url.regionMatches(start, "file:", 0, 5, ignoreCase = true) ||
            url.regionMatches(start, "content:", 0, 8, ignoreCase = true) ||
            url.regionMatches(start, "mailto:", 0, 7, ignoreCase = true) ||
            url.regionMatches(start, "tel:", 0, 4, ignoreCase = true) ||
            url.regionMatches(start, "sms:", 0, 4, ignoreCase = true) ||
            url.regionMatches(start, "geo:", 0, 4, ignoreCase = true)
        ) {
            return ParsedUrl(host = "", path = "", rawUrl = url)
        }

        var hostStart = -1
        if (start + 1 < end && url[start] == '/' && url[start + 1] == '/') {
            hostStart = start + 2
        } else {
            val schemeDelim = url.indexOf("://", start)
            if (schemeDelim != -1 && schemeDelim < end) {
                var hasBreakBeforeScheme = false
                for (i in start until schemeDelim) {
                    val c = url[i]
                    if (c == '/' || c == '?' || c == '#') {
                        hasBreakBeforeScheme = true
                        break
                    }
                }
                if (!hasBreakBeforeScheme) {
                    hostStart = schemeDelim + 3
                }
            }
        }

        if (hostStart == -1) {
            if (url[start] == '/') {
                return ParsedUrl(host = "", path = "", rawUrl = url)
            }
            hostStart = start
        }

        for (i in hostStart until end) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') break
            if (c == '@') {
                hostStart = i + 1
                break
            }
        }

        var hostEnd = end
        var pathStart = -1

        if (hostStart < end && url[hostStart] == '[') {
            val closeBracket = url.indexOf(']', hostStart)
            if (closeBracket != -1 && closeBracket < end) {
                hostEnd = closeBracket + 1
            }
        } else {
            for (i in hostStart until end) {
                val c = url[i]
                if (c == ':' || c == '/' || c == '?' || c == '#') {
                    hostEnd = i
                    break
                }
            }
        }

        if (hostEnd <= hostStart) {
            return ParsedUrl(host = "", path = "", rawUrl = url)
        }

        for (i in hostEnd until end) {
            val c = url[i]
            if (c == '/') {
                pathStart = i
                break
            } else if (c == '?' || c == '#') {
                break
            }
        }

        val host = run {
            var hasUpper = false
            for (i in hostStart until hostEnd) {
                val c = url[i]
                if (c in 'A'..'Z') {
                    hasUpper = true
                    break
                }
            }
            if (hasUpper) {
                url.substring(hostStart, hostEnd).lowercase()
            } else {
                url.substring(hostStart, hostEnd)
            }
        }

        val path = if (pathStart != -1) {
            var pathEnd = end
            for (i in pathStart until end) {
                val c = url[i]
                if (c == '?' || c == '#') {
                    pathEnd = i
                    break
                }
            }
            var hasUpper = false
            for (i in pathStart until pathEnd) {
                val c = url[i]
                if (c in 'A'..'Z') {
                    hasUpper = true
                    break
                }
            }
            if (hasUpper) {
                url.substring(pathStart, pathEnd).lowercase()
            } else {
                url.substring(pathStart, pathEnd)
            }
        } else {
            ""
        }

        return ParsedUrl(host = host, path = path, rawUrl = url)
    }

    fun reset() {
        lastParsedUrl.remove()
    }
}
