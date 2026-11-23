package com.jestk.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.DownloadFileNameResolver
import com.jestk.browser.engine.download.HoriDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns WebView download requests (http, data: and blob: URLs) into queued downloads.
 * Owned by [WebViewPool], which supplies the collaborators it may swap at runtime.
 */
internal class WebViewDownloads(
    private val applicationContext: Context,
    private val pendingBlobDownloads: PendingBlobDownloads,
    private val downloadRepository: () -> DownloadRepository?,
    private val downloadManager: () -> HoriDownloadManager?,
    private val activeScope: () -> CoroutineScope,
    private val onDownloadStarted: (DownloadItem) -> Unit,
    private val onDownloadFailed: (fileName: String?, message: String) -> Unit
) {

    /** Called on the main thread when the file was already downloaded; invoke `proceed` to download it again. */
    var onDuplicateDownload: ((existing: DownloadItem, proceed: () -> Unit) -> Unit)? = null

    /**
     * Called on the main thread before a download the page started is saved; invoke `proceed` to
     * save it, drop it to cancel. Null means no UI is attached (tests): downloads start at once.
     * Explicit user actions (`confirmed = true`) and "download again" skip it.
     */
    var onConfirmDownload: ((request: DownloadConfirmRequest, proceed: () -> Unit) -> Unit)? = null

    /**
     * Host of the page behind the latest `blob:` request. The blob bridge hands back only the
     * bytes, not the blob URL, so the prompt takes the host from the newest request.
     */
    private var lastBlobHost: String? = null

    private companion object {
        private const val TAG = "WebViewPool"
    }

    private fun resolveManager(): HoriDownloadManager? = downloadManager() ?: downloadRepository()?.let {
        HoriDownloadManager.getInstance(applicationContext, it)
    }

    fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        webView: WebView?,
        confirmed: Boolean = false,
    ) {
        // Check for data: URI scheme
        if (url.startsWith("data:", ignoreCase = true)) {
            // WebView passes no Content-Disposition for data: URLs; the page script's `download` name stands in.
            val disposition = contentDisposition?.takeIf { it.isNotBlank() }
                ?: webView?.let(pendingBlobDownloads::consumeDataLinkDisposition)
            startDataUrlDownload(url, disposition, mimeType, host = downloadHostOf(webView?.safeUrl()), confirmed)
            return
        }

        // Check for blob: URI scheme
        if (url.startsWith("blob:", ignoreCase = true)) {
            handleBlobDownload(url, contentDisposition, mimeType, webView)
            return
        }

        try {
            val fileName = DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
            Log.d(TAG, "Download requested: $fileName from $url")
            val downloadMgr = resolveManager()
            val refererUrl = try {
                webView?.url
            } catch (t: Throwable) {
                Log.d(TAG, "Best-effort read of referer URL failed, continuing without referer", t)
                null
            }

            if (downloadMgr != null) {
                val enqueue: suspend () -> Unit = {
                    try {
                        val downloadId = downloadMgr.enqueueDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            contentLength = contentLength,
                            referer = refererUrl,
                            customFileName = fileName
                        )
                        val item = downloadRepository()?.getDownloadById(downloadId)
                        if (item != null) {
                            withContext(Dispatchers.Main) {
                                onDownloadStarted(item)
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        Log.e(TAG, "Failed to enqueue download", t)
                        withContext(Dispatchers.Main) { onDownloadFailed(fileName, t.message.orEmpty()) }
                    }
                }
                activeScope().launch {
                    val existing = downloadRepository()?.let { findExistingDownload(it, url) }
                    val prompt = onDuplicateDownload
                    if (existing != null && prompt != null) {
                        withContext(Dispatchers.Main) {
                            prompt(existing) { activeScope().launch { enqueue() } }
                        }
                    } else {
                        val confirm = onConfirmDownload
                        if (confirmed || confirm == null) {
                            enqueue()
                        } else {
                            val request = DownloadConfirmRequest(
                                fileName = fileName,
                                sizeBytes = contentLength.takeIf { it > 0 },
                                host = downloadHostOf(refererUrl),
                            )
                            withContext(Dispatchers.Main) {
                                confirm(request) { activeScope().launch { enqueue() } }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed for $url", e)
            onDownloadFailed(null, e.message.orEmpty())
        }
    }

    /** Entry point of the blob bridge: the page's blob bytes arrived as a data URL. */
    fun handleDataUrlDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ) = startDataUrlDownload(url, contentDisposition, mimeType, host = lastBlobHost, confirmed = false)

    /**
     * For `data:` and `blob:` downloads the prompt comes after the bytes are in hand. Pages revoke
     * a blob URL within milliseconds of starting the download, so asking first and reading after
     * the answer would find nothing; the bytes wait in memory while the card is shown.
     */
    private fun startDataUrlDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        host: String?,
        confirmed: Boolean,
    ) {
        val confirm = onConfirmDownload
        if (confirmed || confirm == null) {
            saveDataUrl(url, contentDisposition, mimeType)
        } else {
            confirm(dataUrlConfirmRequest(url, contentDisposition, mimeType, host)) {
                saveDataUrl(url, contentDisposition, mimeType)
            }
        }
    }

    private fun saveDataUrl(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val downloadMgr = resolveManager()

        if (downloadMgr != null) {
            activeScope().launch {
                try {
                    val downloadId = downloadMgr.handleDataUrl(url, contentDisposition, mimeType)
                    val item = downloadRepository()?.getDownloadById(downloadId)
                    if (item != null) {
                        withContext(Dispatchers.Main) {
                            onDownloadStarted(item)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Data URL download failed", e)
                    withContext(Dispatchers.Main) {
                        onDownloadFailed(null, e.message.orEmpty())
                    }
                }
            }
        }
    }

    fun handleBlobDownload(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        webView: WebView?
    ) {
        if (webView == null) {
            Log.w(TAG, "Cannot handle blob download without originating WebView reference")
            onDownloadFailed(null, "")
            return
        }

        Log.d(TAG, "Preparing blob download: $blobUrl")
        lastBlobHost = blobOriginHost(blobUrl) ?: downloadHostOf(webView.safeUrl())

        try {
            pendingBlobDownloads.request(webView, blobUrl, contentDisposition, mimeType) {
                onDownloadFailed(null, "")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to evaluate blob download script", t)
            onDownloadFailed(null, t.message.orEmpty())
        }
    }
}
