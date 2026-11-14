package com.jestk.browser.engine.adblock

import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object BlockedResponses {
    // Minimal 1x1 32-bit transparent PNG to fulfill blocked image requests cleanly
    val TRANSPARENT_1X1_PNG: ByteArray by lazy {
        Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
    }

    fun createBlockedResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun corsHeaders(cleanUrl: String, requestHeaders: Map<String, String>?): Map<String, String> {
        val requestingOrigin = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
            ?: requestHeaders?.entries
                ?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                ?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                ?.let { if (it.scheme != null && it.host != null) "${it.scheme}://${it.host}" else null }
            ?: if (cleanUrl.contains("youtube.com") || cleanUrl.contains("doubleclick.net") || cleanUrl.contains("google")) "https://m.youtube.com" else "*"

        val corsHeaders = buildMap<String, String> {
            put("Access-Control-Allow-Origin", requestingOrigin)
            if (requestingOrigin != "*") {
                put("Access-Control-Allow-Credentials", "true")
            }
            put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
            put("Access-Control-Allow-Headers", "*")
        }
        return corsHeaders
    }

    /**
     * Decodes a `data:<mime>;base64,<payload>` redirect resource into a response with the same
     * CORS headers as [createSmartBlockedResponse]. A malformed data URL yields the empty response.
     */
    fun fromDataUrl(dataUrl: String, requestHeaders: Map<String, String>? = null): WebResourceResponse {
        if (!dataUrl.startsWith("data:", ignoreCase = true)) return createBlockedResponse()
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return createBlockedResponse()
        val meta = dataUrl.substring("data:".length, comma).split(';')
        if (meta.none { it.equals("base64", ignoreCase = true) }) return createBlockedResponse()
        val mime = meta.first().ifBlank { "text/plain" }
        val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
            ?: return createBlockedResponse()
        val encoding = if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "UTF-8" else null
        return WebResourceResponse(
            mime,
            encoding,
            200,
            "OK",
            corsHeaders("", requestHeaders),
            ByteArrayInputStream(bytes),
        )
    }

    /**
     * Creates a content-aware blocked response tailored to the resource type:
     * - Images: Returns a 1x1 transparent PNG to eliminate broken image icons and avoid layout shift.
     * - Scripts: Returns application/javascript with a comment or harmless stub to stop syntax errors.
     * - Beacons/Analytics: Returns HTTP 204 No Content.
     * - HTML: Returns an empty HTML document.
     * - Fallback: Returns empty text/plain.
     */
    fun createSmartBlockedResponse(
        url: String?,
        mimeTypeHint: String? = null,
        requestHeaders: Map<String, String>? = null,
    ): WebResourceResponse {
        val cleanUrl = url?.lowercase()?.trim().orEmpty()
        val acceptHeader =
            requestHeaders?.get("Accept")?.lowercase()
                ?: requestHeaders?.get("accept")?.lowercase()
                ?: ""

        val corsHeaders = corsHeaders(cleanUrl, requestHeaders)

        val isImage =
            mimeTypeHint?.startsWith("image/") == true ||
                acceptHeader.contains("image/") ||
                cleanUrl.endsWith(".png") || cleanUrl.endsWith(".jpg") ||
                cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".gif") ||
                cleanUrl.endsWith(".webp") || cleanUrl.endsWith(".svg") ||
                cleanUrl.endsWith(".ico") || cleanUrl.contains("/pixel")

        if (isImage) {
            return WebResourceResponse(
                "image/png",
                null,
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(TRANSPARENT_1X1_PNG),
            )
        }

        val isScript =
            mimeTypeHint?.contains("javascript") == true ||
                acceptHeader.contains("javascript") ||
                cleanUrl.endsWith(".js") ||
                cleanUrl.contains(".js?") ||
                cleanUrl.contains("/adsbygoogle") ||
                cleanUrl.contains("/gpt.js") ||
                cleanUrl.contains("context.js") ||
                cleanUrl.contains("header-bidding") ||
                cleanUrl.contains("ads-async.js") ||
                cleanUrl.contains("sdk.standalone.js") ||
                cleanUrl.contains("adfox") ||
                cleanUrl.contains("ad_status")

        if (isScript) {
            val stubContent =
                when {
                    cleanUrl.contains("ad_status") -> {
                        "window.google_ad_status = 1;"
                    }

                    cleanUrl.contains("adsbygoogle") -> {
                        "window.adsbygoogle = window.adsbygoogle || []; window.adsbygoogle.loaded = true; window.adsbygoogle.push = function(o){return 0;};"
                    }

                    cleanUrl.contains("gpt.js") || cleanUrl.contains("pubads") -> {
                        "window.googletag = window.googletag || {cmd:[]}; window.googletag.cmd.push = function(f){if(typeof f==='function'){try{f();}catch(e){}}};"
                    }

                    cleanUrl.contains("context.js") || cleanUrl.contains("header-bidding") || cleanUrl.contains("adfox") -> {
                        "window.yaContextCb = window.yaContextCb || []; window.Ya = window.Ya || {}; window.Ya.adfoxCode = window.Ya.adfoxCode || {create:function(c){if(c&&c.containerId){var e=document.getElementById(c.containerId);if(e){e.style.setProperty('display','none','important');e.style.setProperty('height','0','important');}}},createScroll:function(c){if(c&&c.containerId){var e=document.getElementById(c.containerId);if(e){e.style.setProperty('display','none','important');e.style.setProperty('height','0','important');}}},destroy:function(){},clearSession:function(){},reload:function(){},hbCallbacks:[]};"
                    }

                    cleanUrl.contains("sdk.standalone.js") || cleanUrl.contains("x5media") -> {
                        "window.x5media = window.x5media || {init:function(){return Promise.resolve();},render:function(){}};"
                    }

                    else -> {
                        "/* blocked by Prosto */"
                    }
                }
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(stubContent.toByteArray(Charsets.UTF_8)),
            )
        }

        val isBeaconOrAnalytics =
            cleanUrl.contains("/collect") ||
                cleanUrl.contains("/beacon") ||
                cleanUrl.contains("/analytics") ||
                cleanUrl.contains("/telemetry")

        if (isBeaconOrAnalytics) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                204,
                "No Content",
                corsHeaders,
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        val isMedia =
            mimeTypeHint?.startsWith("video/") == true ||
                mimeTypeHint?.startsWith("audio/") == true ||
                acceptHeader.contains("video/") ||
                acceptHeader.contains("audio/") ||
                cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".webm") ||
                cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".ts") ||
                cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".ogg") ||
                cleanUrl.endsWith(".m4a") || cleanUrl.endsWith(".m4v") ||
                cleanUrl.contains(".mp4?") || cleanUrl.contains(".m3u8?") ||
                cleanUrl.contains(".webm?")

        if (isMedia) {
            val isAudio = mimeTypeHint?.startsWith("audio/") == true ||
                cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".ogg") || cleanUrl.endsWith(".m4a")
            return WebResourceResponse(
                if (isAudio) "audio/mpeg" else "video/mp4",
                "UTF-8",
                204,
                "No Content",
                corsHeaders,
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        val isJson =
            mimeTypeHint?.contains("json") == true ||
                acceptHeader.contains("application/json") ||
                cleanUrl.endsWith(".json") ||
                cleanUrl.contains(".json?") ||
                cleanUrl.contains("pagead/id") ||
                cleanUrl.contains("/player/ad_break") ||
                cleanUrl.contains("/get_midroll") ||
                cleanUrl.contains("/ad_break")

        if (isJson) {
            return WebResourceResponse(
                "application/json",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8)),
            )
        }

        val isHtml =
            mimeTypeHint?.contains("html") == true ||
                cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm")

        if (isHtml) {
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream("<!DOCTYPE html><html><body></body></html>".toByteArray(Charsets.UTF_8)),
            )
        }

        return createBlockedResponse()
    }
}
