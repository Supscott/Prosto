package com.jestk.browser.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives the content of a `blob:` download. The bridge is visible to every frame, so only
 * blob URLs that WebView itself reported to the DownloadListener are accepted: a page cannot
 * start a download of arbitrary data through it (review finding BOT-3).
 */
class BlobDownloadBridge internal constructor(
    private val pendingBlobUrls: MutableSet<String>,
    private val onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit,
    private val onDataLinkDisposition: (contentDisposition: String) -> Unit = {},
) {
    /**
     * The content script reports a tapped `<a download="name" href="data:...">` here: WebView's
     * DownloadListener gets no Content-Disposition for a `data:` URL, so the name would be lost.
     */
    @JavascriptInterface
    fun onDataLinkClicked(contentDisposition: String) {
        if (contentDisposition.length > MAX_DISPOSITION_LENGTH) return
        onDataLinkDisposition(contentDisposition)
    }

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
        const val MAX_DISPOSITION_LENGTH = 1024
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
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val urls: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val dataLinkHints = DataLinkDispositionHints(clock)

    fun createBridge(
        webView: WebView,
        onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit,
    ): BlobDownloadBridge {
        val owner = WeakReference(webView)
        return BlobDownloadBridge(urls, onBlobReceived) { disposition ->
            owner.get()?.let { dataLinkHints.put(it, disposition) }
        }
    }

    /**
     * The Content-Disposition a tap on a `data:` link with a `download` name left for [webView]:
     * one-shot, and only for [DataLinkDispositionHints.TTL_MS] after the tap.
     */
    fun consumeDataLinkDisposition(webView: WebView): String? = dataLinkHints.consume(webView)

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
