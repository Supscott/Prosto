package com.jestk.browser.engine

import android.webkit.WebView
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.engine.download.DownloadFileNameResolver
import java.net.URI

/** The current URL of this WebView; null when the WebView refuses to say (destroyed, wrong thread). */
internal fun WebView.safeUrl(): String? = runCatching { url }.getOrNull()

/** The host of [url], or null when it has none (opaque origin, malformed URL). */
internal fun downloadHostOf(url: String?): String? =
    url?.let { runCatching { URI(it).host }.getOrNull() }?.takeIf { it.isNotBlank() }

/** The host of the page that created a `blob:` URL: the origin is embedded in it (`blob:https://host/id`). */
internal fun blobOriginHost(blobUrl: String): String? = downloadHostOf(blobUrl.substringAfter("blob:", ""))

/**
 * The number of trailing base64 padding characters of [url] (0..2), after [payloadStart]: a
 * literal `=` or a percent-encoded `%3D`. Scans from the end, no copy.
 */
internal fun base64PaddingCount(url: String, payloadStart: Int): Int {
    var end = url.length
    var pads = 0
    while (pads < 2) {
        if (end > payloadStart && url[end - 1] == '=') {
            end -= 1
        } else if (end - 3 >= payloadStart && url[end - 1].let { it == 'D' || it == 'd' } &&
            url[end - 2] == '3' && url[end - 3] == '%'
        ) {
            end -= 3
        } else {
            break
        }
        pads++
    }
    return pads
}

/**
 * Builds the prompt text for a `data:` download without decoding the payload: the name comes from
 * the same resolver the saver uses, the size is estimated from the payload length.
 */
internal fun dataUrlConfirmRequest(
    url: String,
    contentDisposition: String?,
    mimeType: String?,
    host: String?,
): DownloadConfirmRequest {
    val comma = url.indexOf(',')
    val metadata = if (comma > 5) url.substring(5, comma) else ""
    val payloadLength = if (comma >= 0) url.length - comma - 1 else 0
    val decodedLength = payloadLength - 2L * url.count { it == '%' }
    val size = if (metadata.contains(";base64", ignoreCase = true)) {
        // `decodedLength` counts a `%3D` as one character, like the `=` it stands for.
        decodedLength * 3L / 4 - base64PaddingCount(url, comma + 1)
    } else {
        decodedLength
    }
    val rawMime = metadata.substringBefore(';').trim().ifEmpty { null } ?: mimeType ?: "text/plain"
    val fileName = DownloadFileNameResolver.resolveFileName("data:$rawMime", contentDisposition, rawMime)
    return DownloadConfirmRequest(fileName = fileName, sizeBytes = size.takeIf { it > 0 }, host = host)
}
