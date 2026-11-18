package com.jestk.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.DownloadFileNameResolver
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.util.findActivity
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
    private val onDownloadStarted: (DownloadItem) -> Unit
) {

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
        context: Context,
        webView: WebView?
    ) {
        val targetContext = context.findActivity() ?: applicationContext

        // Check for data: URI scheme
        if (url.startsWith("data:", ignoreCase = true)) {
            handleDataUrlDownload(url, contentDisposition, mimeType, targetContext)
            return
        }

        // Check for blob: URI scheme
        if (url.startsWith("blob:", ignoreCase = true)) {
            handleBlobDownload(url, contentDisposition, mimeType, targetContext, webView)
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
                activeScope().launch {
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
                        Log.e(TAG, "Failed to enqueue download", t)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed for $url", e)
            val failureContext = context.findActivity() ?: applicationContext
            try {
                Toast.makeText(failureContext, downloadFailedText(failureContext, e), Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to show download error toast", t)
            }
        }
    }

    fun handleDataUrlDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context
    ) {
        val targetContext = context.findActivity() ?: applicationContext
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
                        Toast.makeText(targetContext, downloadFailedText(targetContext, e), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun handleBlobDownload(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context,
        webView: WebView?
    ) {
        val targetContext = context.findActivity() ?: applicationContext
        if (webView == null) {
            Log.w(TAG, "Cannot handle blob download without originating WebView reference")
            Toast.makeText(targetContext, R.string.downloads_failed, Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Preparing blob download: $blobUrl")

        try {
            pendingBlobDownloads.request(webView, blobUrl, contentDisposition, mimeType) {
                Toast.makeText(targetContext, R.string.downloads_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to evaluate blob download script", t)
            Toast.makeText(targetContext, downloadFailedText(targetContext, t), Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFailedText(context: Context, error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.toast_download_failed_reason, it) }
            ?: context.getString(R.string.downloads_failed)
}
