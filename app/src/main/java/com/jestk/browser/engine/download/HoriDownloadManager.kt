package com.jestk.browser.engine.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.domain.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HoriDownloadManager(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                try {
                    val cookieManager = CookieManager.getInstance()
                    val urlStr = url.toString()
                    for (cookie in cookies) {
                        cookieManager.setCookie(urlStr, cookie.toString())
                    }
                } catch (_: Throwable) {}
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return try {
                    val cookieManager = CookieManager.getInstance()
                    val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
                    cookieString.split(";").mapNotNull {
                        Cookie.parse(url, it.trim())
                    }
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        })
        .build()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgressInfo>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadProgressInfo>> = _activeDownloads.asStateFlow()
    val downloadProgress: StateFlow<Map<Long, DownloadProgressInfo>> get() = activeDownloads

    private val _completedDownloads = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 64)
    val completedDownloads: SharedFlow<DownloadItem> = _completedDownloads.asSharedFlow()

    companion object {
        private const val TAG = "HoriDownloadManager"
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_EMIT_INTERVAL_MS = 150L
        private const val DB_UPDATE_INTERVAL_MS = 1000L

        @Volatile
        private var instance: HoriDownloadManager? = null

        fun getInstance(context: Context, downloadRepository: DownloadRepository): HoriDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: HoriDownloadManager(
                    context.applicationContext,
                    downloadRepository
                ).also { instance = it }
            }
        }
    }

    /**
     * Enqueues a new HTTP / HTTPS download, saves initial state to Room, and starts HoriDownloadService.
     */
    suspend fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        referer: String? = null,
        customFileName: String? = null
    ): Long = withContext(ioDispatcher) {
        val fileName = customFileName ?: DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = DownloadFileNameResolver.resolveUniqueFile(downloadDir, fileName)

        val id = downloadRepository.recordDownload(
            url = url,
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            mimeType = mimeType,
            contentLength = contentLength,
            status = DownloadStatus.PENDING,
            bytesDownloaded = 0L,
            totalBytes = if (contentLength > 0L) contentLength else 0L
        )

        _activeDownloads.update { current ->
            current + (id to DownloadProgressInfo(
                id = id,
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                mimeType = mimeType,
                status = DownloadStatus.PENDING,
                bytesDownloaded = 0L,
                totalBytes = if (contentLength > 0L) contentLength else 0L
            ))
        }

        startServiceWithAction(HoriDownloadService.ACTION_START_DOWNLOAD, id, referer, userAgent)
        id
    }

    /**
     * Pauses an ongoing download.
     */
    fun pauseDownload(id: Long) {
        val job = activeJobs.remove(id)
        job?.cancel()

        coroutineScope.launch {
            val item = downloadRepository.getDownloadById(id)
            val currentProgress = _activeDownloads.value[id]
            val bytes = currentProgress?.bytesDownloaded ?: item?.bytesDownloaded ?: 0L
            val total = currentProgress?.totalBytes ?: item?.totalBytes ?: 0L

            downloadRepository.updateProgress(id, bytes, total, DownloadStatus.PAUSED)

            _activeDownloads.update { current ->
                val prev = current[id]
                if (prev != null) {
                    current + (id to prev.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L))
                } else {
                    current
                }
            }

            startServiceWithAction(HoriDownloadService.ACTION_UPDATE_NOTIFICATION, id)
        }
    }

    /**
     * Resumes a paused or interrupted download.
     */
    fun resumeDownload(id: Long) {
        startServiceWithAction(HoriDownloadService.ACTION_RESUME, id)
    }

    /**
     * Cancels an ongoing or paused download, deleting any partial .crdownload file.
     */
    fun cancelDownload(id: Long) {
        val job = activeJobs.remove(id)
        val wasActive = job != null || _activeDownloads.value.containsKey(id)
        job?.cancel()

        coroutineScope.launch {
            val item = downloadRepository.getDownloadById(id)
            if (item != null) {
                try {
                    val crdownload = File("${item.filePath}.crdownload")
                    if (crdownload.exists()) {
                        crdownload.delete()
                    }
                } catch (_: Throwable) {}
            }

            downloadRepository.updateStatus(id, DownloadStatus.CANCELLED)
            _activeDownloads.update { it - id }

            if (wasActive) {
                startServiceWithAction(HoriDownloadService.ACTION_CANCEL, id)
            }
        }
    }

    /**
     * Retries a failed or cancelled download from the beginning.
     */
    fun retryDownload(id: Long) {
        coroutineScope.launch {
            val item = downloadRepository.getDownloadById(id) ?: return@launch
            try {
                val crdownload = File("${item.filePath}.crdownload")
                if (crdownload.exists()) {
                    crdownload.delete()
                }
            } catch (_: Throwable) {}

            downloadRepository.updateProgress(id, 0L, item.totalBytes, DownloadStatus.PENDING)
            startServiceWithAction(HoriDownloadService.ACTION_RESUME, id)
        }
    }

    /**
     * Deletes a download from history and disk.
     */
    suspend fun deleteDownload(id: Long, deleteFile: Boolean = true) = withContext(ioDispatcher) {
        val wasActive = activeJobs.containsKey(id) || _activeDownloads.value.containsKey(id)
        if (wasActive) {
            cancelDownload(id)
        }
        downloadRepository.deleteDownload(id, deleteFile)
    }

    /**
     * Called by HoriDownloadService to execute the actual download stream.
     */
    fun performDownload(
        id: Long,
        referer: String? = null,
        userAgent: String? = null,
        onProgress: (DownloadProgressInfo) -> Unit,
        onComplete: (DownloadItem) -> Unit,
        onError: (Long, String) -> Unit
    ) {
        if (activeJobs[id]?.isActive == true) return

        val job = coroutineScope.launch {
            var item = downloadRepository.getDownloadById(id)
            if (item == null) {
                onError(id, "Download record not found")
                return@launch
            }

            val finalFile = File(item.filePath)
            val partFile = File("${item.filePath}.crdownload")
            var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

            downloadRepository.updateStatus(id, DownloadStatus.DOWNLOADING)

            var progressInfo = DownloadProgressInfo(
                id = id,
                fileName = item.fileName,
                filePath = item.filePath,
                mimeType = item.mimeType,
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = downloadedBytes,
                totalBytes = item.totalBytes
            )
            _activeDownloads.update { it + (id to progressInfo) }
            onProgress(progressInfo)

            try {
                val requestBuilder = Request.Builder().url(item.url)

                val defaultUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                val effectiveUserAgent = if (!userAgent.isNullOrBlank()) userAgent else defaultUserAgent
                requestBuilder.header("User-Agent", effectiveUserAgent)
                if (!referer.isNullOrBlank()) {
                    requestBuilder.header("Referer", referer)
                }
                requestBuilder.header("Accept", "*/*")
                requestBuilder.header("Accept-Language", Locale.getDefault().toLanguageTag().ifEmpty { "en-US,en;q=0.9" })
                requestBuilder.header("Connection", "keep-alive")

                var isResume = false
                if (downloadedBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                    if (!item.etag.isNullOrBlank()) {
                        requestBuilder.header("If-Range", item.etag)
                    }
                    isResume = true
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseCode = response.code

                if (responseCode == 416 && isResume) {
                    // Range not satisfiable -> restart from beginning
                    partFile.delete()
                    downloadedBytes = 0L
                    response.close()
                    performDownload(id, referer, userAgent, onProgress, onComplete, onError)
                    return@launch
                }

                if (!response.isSuccessful && responseCode != 206) {
                    throw IOException("HTTP error code: $responseCode")
                }

                val responseBody = response.body ?: throw IOException("Empty response body")
                val responseContentLength = responseBody.contentLength()
                val isPartial = responseCode == 206

                var totalBytes = if (isPartial) {
                    downloadedBytes + (if (responseContentLength > 0L) responseContentLength else 0L)
                } else {
                    if (!isResume) {
                        downloadedBytes = 0L
                    }
                    if (responseContentLength > 0L) responseContentLength else item.totalBytes
                }

                val newEtag = response.header("ETag") ?: item.etag

                // Update content disposition or mime if server gave better info
                val respDisposition = response.header("Content-Disposition")
                val respMime = response.header("Content-Type")?.substringBefore(';')?.trim()
                if (!respMime.isNullOrBlank() && item.mimeType.isNullOrBlank()) {
                    item = item.copy(mimeType = respMime)
                }

                // If server redirected to a better filename via Content-Disposition, refine filename
                if (!respDisposition.isNullOrBlank()) {
                    val serverSuggestedName = DownloadFileNameResolver.resolveFileName(response.request.url.toString(), respDisposition, respMime)
                    if (serverSuggestedName.isNotBlank() && serverSuggestedName != item.fileName) {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val newFinalFile = DownloadFileNameResolver.resolveUniqueFile(downloadDir, serverSuggestedName)
                        item = item.copy(fileName = newFinalFile.name, filePath = newFinalFile.absolutePath)
                        downloadRepository.updateFilePath(id, newFinalFile.name, newFinalFile.absolutePath)
                    }
                }

                val inputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(partFile, isPartial && downloadedBytes > 0L)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var lastProgressEmitTime = System.currentTimeMillis()
                var lastDbUpdateTime = System.currentTimeMillis()
                var speedBytesAccumulator = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var currentSpeed = 0L

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        speedBytesAccumulator += bytesRead

                        val now = System.currentTimeMillis()
                        val speedDelta = now - lastSpeedCalcTime
                        if (speedDelta >= 1000L) {
                            currentSpeed = (speedBytesAccumulator * 1000L) / speedDelta
                            speedBytesAccumulator = 0L
                            lastSpeedCalcTime = now
                        }

                        if (now - lastProgressEmitTime >= PROGRESS_EMIT_INTERVAL_MS) {
                            progressInfo = progressInfo.copy(
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = currentSpeed
                            )
                            _activeDownloads.update { it + (id to progressInfo) }
                            onProgress(progressInfo)
                            lastProgressEmitTime = now
                        }

                        if (now - lastDbUpdateTime >= DB_UPDATE_INTERVAL_MS) {
                            downloadRepository.updateProgress(id, downloadedBytes, totalBytes, DownloadStatus.DOWNLOADING)
                            lastDbUpdateTime = now
                        }
                    }

                    outputStream.flush()
                } finally {
                    try { outputStream.close() } catch (_: Throwable) {}
                    try { inputStream.close() } catch (_: Throwable) {}
                    try { response.close() } catch (_: Throwable) {}
                }

                // Atomic rename from .crdownload to final destination
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                val renameSuccess = partFile.renameTo(finalFile)
                if (!renameSuccess) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val completedTotal = if (totalBytes > 0L) totalBytes else downloadedBytes
                downloadRepository.updateProgress(id, completedTotal, completedTotal, DownloadStatus.COMPLETED)

                val completedItem = item.copy(
                    status = DownloadStatus.COMPLETED,
                    bytesDownloaded = completedTotal,
                    totalBytes = completedTotal,
                    etag = newEtag
                )

                _activeDownloads.update { it - id }
                _completedDownloads.emit(completedItem)

                // Index in Android MediaStore so file managers & players detect it immediately
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFile.absolutePath),
                        if (completedItem.mimeType != null) arrayOf(completedItem.mimeType) else null,
                        null
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaScanner indexing failed", t)
                }

                onComplete(completedItem)

            } catch (e: CancellationException) {
                // Task was paused or cancelled intentionally
                Log.d(TAG, "Download $id was cancelled or paused")
            } catch (t: Throwable) {
                Log.e(TAG, "Download $id failed", t)
                val errorMsg = t.message ?: "Download failed"
                downloadRepository.updateStatus(id, DownloadStatus.FAILED, errorMsg)

                _activeDownloads.update { current ->
                    val prev = current[id]
                    if (prev != null) {
                        current + (id to prev.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
                    } else {
                        current
                    }
                }
                onError(id, errorMsg)
            } finally {
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
    }

    /**
     * Handles data: URI scheme downloads directly without network calls.
     */
    suspend fun handleDataUrl(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): Long = withContext(ioDispatcher) {
        val commaIndex = url.indexOf(',')
        if (commaIndex == -1) throw IllegalArgumentException("Invalid data URL")

        val metadata = url.substring(5, commaIndex)
        val base64Data = url.substring(commaIndex + 1)
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)

        val metaMime = metadata.substringBefore(';').trim().ifEmpty { null }
        val resolvedMime = metaMime ?: mimeType ?: "application/octet-stream"

        val fileName = DownloadFileNameResolver.resolveFileName("data:$resolvedMime", contentDisposition, resolvedMime)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = DownloadFileNameResolver.resolveUniqueFile(downloadDir, fileName)

        targetFile.writeBytes(bytes)

        val id = downloadRepository.recordDownload(
            url = if (url.length > 512) url.take(512) + "..." else url,
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            mimeType = resolvedMime,
            contentLength = bytes.size.toLong(),
            status = DownloadStatus.COMPLETED,
            bytesDownloaded = bytes.size.toLong(),
            totalBytes = bytes.size.toLong()
        )

        val item = downloadRepository.getDownloadById(id)
        if (item != null) {
            _completedDownloads.emit(item)
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(resolvedMime),
                    null
                )
            } catch (_: Throwable) {}
        }

        id
    }

    private fun startServiceWithAction(action: String, downloadId: Long, referer: String? = null, userAgent: String? = null) {
        val intent = Intent(context, HoriDownloadService::class.java).apply {
            this.action = action
            putExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, downloadId)
            if (referer != null) putExtra(HoriDownloadService.EXTRA_REFERER, referer)
            if (userAgent != null) putExtra(HoriDownloadService.EXTRA_USER_AGENT, userAgent)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (action == HoriDownloadService.ACTION_START_DOWNLOAD || action == HoriDownloadService.ACTION_RESUME) {
                    context.startForegroundService(intent)
                } else {
                    try {
                        context.startService(intent)
                    } catch (_: Throwable) {
                        context.startForegroundService(intent)
                    }
                }
            } else {
                context.startService(intent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start HoriDownloadService with action $action", t)
        }
    }
}
