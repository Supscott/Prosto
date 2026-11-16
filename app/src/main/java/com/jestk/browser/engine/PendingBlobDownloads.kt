package com.jestk.browser.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives the content of a `blob:` download. The bridge is visible to every frame, so only
 * blob URLs that WebView itself reported to the DownloadListener are accepted: a page cannot
 * start a download of arbitrary data through it (review finding BOT-3).
 */
class BlobDownloadBridge internal constructor(
    private val pendingBlobUrls: MutableSet<String>,
    private val onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit
) {
    @JavascriptInterface
    fun onBlobDownloaded(blobUrl: String, dataUrl: String, contentDisposition: String?, mimeType: String?) {
        if (!pendingBlobUrls.remove(blobUrl)) {
            Log.w(TAG, "Ignoring a blob download that was not requested by the DownloadListener")
            return
        }
        onBlobReceived(dataUrl, contentDisposition, mimeType)
    }

    private companion object {
        const val TAG = "BlobDownloadBridge"
    }
}

/**
 * The `blob:` downloads WebView reported to the DownloadListener that no frame has delivered yet.
 * Only a frame of the blob's origin can read it (see [BlobDownloadScript]); when none answers in
 * time (the frame is gone, the blob was revoked, an opaque origin) the request is dropped and
 * the caller tells the user, instead of leaving only the "Preparing download" toast.
 */
internal class PendingBlobDownloads(
    private val timeoutMs: Long = TIMEOUT_MS,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    private val urls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun createBridge(
        onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit,
    ) = BlobDownloadBridge(urls, onBlobReceived)

    fun isPending(blobUrl: String): Boolean = blobUrl in urls

    /** Asks the frames of the blob's origin to read it; [onTimeout] runs on the main thread. */
    fun request(
        webView: WebView,
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        onTimeout: () -> Unit,
    ) {
        urls.add(blobUrl)
        val js = BlobDownloadScript.build(blobUrl, contentDisposition, mimeType)
        mainHandler.post { webView.evaluateJavascript(js, null) }
        mainHandler.postDelayed({
            if (urls.remove(blobUrl)) {
                Log.w(TAG, "No frame delivered blob download $blobUrl")
                onTimeout()
            }
        }, timeoutMs)
    }

    private companion object {
        const val TAG = "PendingBlobDownloads"

        /** Reading a large blob into a data URL and across the bridge takes a few seconds. */
        const val TIMEOUT_MS = 30_000L
    }
}
