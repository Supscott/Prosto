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
    val size = if (metadata.contains(";base64", ignoreCase = true)) {
        payloadLength * 3L / 4
    } else {
        payloadLength - 2L * url.count { it == '%' }
    }
    val rawMime = metadata.substringBefore(';').trim().ifEmpty { null } ?: mimeType ?: "text/plain"
    val fileName = DownloadFileNameResolver.resolveFileName("data:$rawMime", contentDisposition, rawMime)
    return DownloadConfirmRequest(fileName = fileName, sizeBytes = size.takeIf { it > 0 }, host = host)
}
