package com.jestk.browser.engine.download

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.util.FileSizeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HoriDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fakeRepository: FakeDownloadRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var downloadManager: HoriDownloadManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeDownloadRepository()
        downloadManager = HoriDownloadManager(
            context = context,
            downloadRepository = fakeRepository,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun enqueueDownload_recordsInRepositoryAndUpdatesDownloadProgress() = testScope.runTest {
        val url = "https://example.com/files/document.pdf"
        val userAgent = "Mozilla/5.0 HoriBrowser"
        val contentDisposition = "attachment; filename=\"document.pdf\""
        val mimeType = "application/pdf"
        val contentLength = 10_485_760L // 10 MB

        val downloadId = downloadManager.enqueueDownload(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            contentLength = contentLength,
            referer = "https://example.com"
        )
        advanceUntilIdle()

        assertThat(downloadId).isGreaterThan(0L)

        // Verify repository recorded item
        val recordedItem = fakeRepository.getDownloadById(downloadId)
        assertThat(recordedItem).isNotNull()
        assertThat(recordedItem?.url).isEqualTo(url)
        assertThat(recordedItem?.fileName).isEqualTo("document.pdf")
        assertThat(recordedItem?.status).isEqualTo(DownloadStatus.PENDING)
        assertThat(recordedItem?.totalBytes).isEqualTo(contentLength)

        // Verify downloadProgress StateFlow has active entry
        val progressMap = downloadManager.downloadProgress.value
        assertThat(progressMap).containsKey(downloadId)
        val progressInfo = progressMap[downloadId]!!
        assertThat(progressInfo.id).isEqualTo(downloadId)
        assertThat(progressInfo.fileName).isEqualTo("document.pdf")
        assertThat(progressInfo.status).isEqualTo(DownloadStatus.PENDING)
        assertThat(progressInfo.bytesDownloaded).isEqualTo(0L)
        assertThat(progressInfo.totalBytes).isEqualTo(contentLength)
        assertThat(progressInfo.progressFraction).isEqualTo(0f)
        assertThat(progressInfo.progressPercentage).isEqualTo(0)
        assertThat(progressInfo.isIndeterminate).isFalse()

        // Verify start download service intent was dispatched
        val app = context as Application
        val serviceIntent = shadowOf(app).nextStartedService
        assertThat(serviceIntent).isNotNull()
        assertThat(serviceIntent.action).isEqualTo(HoriDownloadService.ACTION_START_DOWNLOAD)
        assertThat(serviceIntent.getLongExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, -1L)).isEqualTo(downloadId)
        assertThat(serviceIntent.getStringExtra(HoriDownloadService.EXTRA_USER_AGENT)).isEqualTo(userAgent)
    }

    @Test
    fun downloadProgress_progressFractionAndPercentageCalculations() {
        val boundedInfo = DownloadProgressInfo(
            id = 1L,
            fileName = "movie.mp4",
            filePath = "/downloads/movie.mp4",
            mimeType = "video/mp4",
            status = DownloadStatus.DOWNLOADING,
            bytesDownloaded = 25 * 1024 * 1024L,
            totalBytes = 100 * 1024 * 1024L,
            speedBytesPerSec = 2 * 1024 * 1024L
        )

        assertThat(boundedInfo.progressFraction).isEqualTo(0.25f)
        assertThat(boundedInfo.progressPercentage).isEqualTo(25)
        assertThat(boundedInfo.isIndeterminate).isFalse()

        // Test boundary 0%
        val zeroProgress = boundedInfo.copy(bytesDownloaded = 0L)
        assertThat(zeroProgress.progressFraction).isEqualTo(0f)
        assertThat(zeroProgress.progressPercentage).isEqualTo(0)

        // Test boundary 100%
        val fullProgress = boundedInfo.copy(bytesDownloaded = 100 * 1024 * 1024L)
        assertThat(fullProgress.progressFraction).isEqualTo(1.0f)
        assertThat(fullProgress.progressPercentage).isEqualTo(100)

        // Test indeterminate (totalBytes <= 0)
        val indeterminateInfo = boundedInfo.copy(
            totalBytes = 0L,
            bytesDownloaded = 5 * 1024 * 1024L
        )
        assertThat(indeterminateInfo.progressFraction).isNull()
        assertThat(indeterminateInfo.progressPercentage).isNull()
        assertThat(indeterminateInfo.isIndeterminate).isTrue()
    }

    @Test
    fun pauseDownload_updatesStatusAndEmitsPausedState() = testScope.runTest {
        val downloadId = downloadManager.enqueueDownload(
            url = "https://example.com/audio/song.mp3",
            userAgent = null,
            contentDisposition = null,
            mimeType = "audio/mpeg",
            contentLength = 5 * 1024 * 1024L
        )
        advanceUntilIdle()

        // Consume initial start service intent
        shadowOf(context as Application).nextStartedService

        downloadManager.pauseDownload(downloadId)
        advanceUntilIdle()

        // Repository should be updated to PAUSED
        val repoItem = fakeRepository.getDownloadById(downloadId)
        assertThat(repoItem?.status).isEqualTo(DownloadStatus.PAUSED)

        // downloadProgress should reflect PAUSED status and zero speed
        val progressInfo = downloadManager.downloadProgress.value[downloadId]
        assertThat(progressInfo).isNotNull()
        assertThat(progressInfo?.status).isEqualTo(DownloadStatus.PAUSED)
        assertThat(progressInfo?.speedBytesPerSec).isEqualTo(0L)

        // Verify notification update intent was dispatched
        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertThat(serviceIntent).isNotNull()
        assertThat(serviceIntent.action).isEqualTo(HoriDownloadService.ACTION_UPDATE_NOTIFICATION)
        assertThat(serviceIntent.getLongExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, -1L)).isEqualTo(downloadId)
    }

    @Test
    fun resumeDownload_dispatchesResumeServiceAction() = testScope.runTest {
        val downloadId = 42L
        downloadManager.resumeDownload(downloadId)

        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertThat(serviceIntent).isNotNull()
        assertThat(serviceIntent.action).isEqualTo(HoriDownloadService.ACTION_RESUME)
        assertThat(serviceIntent.getLongExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, -1L)).isEqualTo(downloadId)
    }

    @Test
    fun cancelDownload_deletesPartialFileAndRemovesFromDownloadProgress() = testScope.runTest {
        // Enqueue download
        val enqueueId = downloadManager.enqueueDownload(
            url = "https://example.com/video.mp4",
            userAgent = null,
            contentDisposition = null,
            mimeType = "video/mp4",
            contentLength = 20 * 1024 * 1024L
        )
        advanceUntilIdle()

        // Consume initial start service intent
        shadowOf(context as Application).nextStartedService

        val itemBeforeCancel = fakeRepository.getDownloadById(enqueueId)
        assertThat(itemBeforeCancel).isNotNull()
        val crdownloadFile = File("${itemBeforeCancel!!.filePath}.crdownload")
        crdownloadFile.writeText("partial download stream")
        assertThat(crdownloadFile.exists()).isTrue()

        downloadManager.cancelDownload(enqueueId)
        advanceUntilIdle()

        // Verify partial file deleted
        assertThat(crdownloadFile.exists()).isFalse()

        // Verify removed from activeDownloads / downloadProgress
        assertThat(downloadManager.downloadProgress.value).doesNotContainKey(enqueueId)

        // Verify repository item marked CANCELLED
        val item = fakeRepository.getDownloadById(enqueueId)
        assertThat(item?.status).isEqualTo(DownloadStatus.CANCELLED)

        // Verify service received ACTION_CANCEL
        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertThat(serviceIntent).isNotNull()
        assertThat(serviceIntent.action).isEqualTo(HoriDownloadService.ACTION_CANCEL)
        assertThat(serviceIntent.getLongExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, -1L)).isEqualTo(enqueueId)
    }

    @Test
    fun retryDownload_resetsProgressAndDispatchesResume() = testScope.runTest {
        val targetFile = File(tempFolder.root, "archive.zip")
        val crdownloadFile = File(tempFolder.root, "archive.zip.crdownload")
        crdownloadFile.writeText("stale partial data")

        val downloadId = fakeRepository.recordDownload(
            url = "https://example.com/archive.zip",
            fileName = "archive.zip",
            filePath = targetFile.absolutePath,
            mimeType = "application/zip",
            contentLength = 50 * 1024 * 1024L,
            status = DownloadStatus.FAILED,
            bytesDownloaded = 10 * 1024 * 1024L
        )

        downloadManager.retryDownload(downloadId)
        advanceUntilIdle()

        // Partial file deleted
        assertThat(crdownloadFile.exists()).isFalse()

        // Repository reset to 0 bytes and PENDING status
        val item = fakeRepository.getDownloadById(downloadId)
        assertThat(item?.bytesDownloaded).isEqualTo(0L)
        assertThat(item?.status).isEqualTo(DownloadStatus.PENDING)

        // Service action dispatched
        val serviceIntent = shadowOf(context as Application).nextStartedService
        assertThat(serviceIntent).isNotNull()
        assertThat(serviceIntent.action).isEqualTo(HoriDownloadService.ACTION_RESUME)
        assertThat(serviceIntent.getLongExtra(HoriDownloadService.EXTRA_DOWNLOAD_ID, -1L)).isEqualTo(downloadId)
    }

    @Test
    fun deleteDownload_removesFromRepositoryAndDisk() = testScope.runTest {
        val testFile = File(tempFolder.root, "report.pdf")
        testFile.writeText("downloaded pdf content")
        assertThat(testFile.exists()).isTrue()

        val downloadId = fakeRepository.recordDownload(
            url = "https://example.com/report.pdf",
            fileName = "report.pdf",
            filePath = testFile.absolutePath,
            mimeType = "application/pdf",
            contentLength = 1024L,
            status = DownloadStatus.COMPLETED,
            bytesDownloaded = 1024L
        )

        downloadManager.deleteDownload(downloadId, deleteFile = true)
        advanceUntilIdle()

        // Repository record deleted
        val item = fakeRepository.getDownloadById(downloadId)
        assertThat(item).isNull()

        // File removed from disk
        assertThat(testFile.exists()).isFalse()
    }

    @Test
    fun handleDataUrl_decodesBase64AndCompletesSuccessfully() = testScope.runTest {
        val rawText = "Hello Hori Browser!"
        val base64Content = android.util.Base64.encodeToString(rawText.toByteArray(), android.util.Base64.NO_WRAP)
        val dataUrl = "data:text/plain;base64,$base64Content"

        val downloadId = downloadManager.handleDataUrl(
            url = dataUrl,
            contentDisposition = "inline; filename=\"hello.txt\"",
            mimeType = "text/plain"
        )
        advanceUntilIdle()

        assertThat(downloadId).isGreaterThan(0L)

        // Verify recorded in repository as COMPLETED
        val item = fakeRepository.getDownloadById(downloadId)
        assertThat(item).isNotNull()
        assertThat(item?.status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(item?.fileName).isEqualTo("hello.txt")
        assertThat(item?.bytesDownloaded).isEqualTo(rawText.length.toLong())

        // Verify completed file content
        val writtenFile = File(item!!.filePath)
        assertThat(writtenFile.exists()).isTrue()
        assertThat(writtenFile.readText()).isEqualTo(rawText)
        writtenFile.delete()
    }

    @Test
    fun handleDataUrl_decodesUrlEncodedPlaintextAndCompletesSuccessfully() = testScope.runTest {
        val rawText = "Hello Plaintext URL World!"
        val encodedText = java.net.URLEncoder.encode(rawText, "UTF-8")
        val dataUrl = "data:text/plain,$encodedText"

        val downloadId = downloadManager.handleDataUrl(
            url = dataUrl,
            contentDisposition = "inline; filename=\"plain.txt\"",
            mimeType = "text/plain"
        )
        advanceUntilIdle()

        assertThat(downloadId).isGreaterThan(0L)

        val item = fakeRepository.getDownloadById(downloadId)
        assertThat(item).isNotNull()
        assertThat(item?.status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(item?.fileName).isEqualTo("plain.txt")
        assertThat(item?.bytesDownloaded).isEqualTo(rawText.toByteArray().size.toLong())

        val writtenFile = File(item!!.filePath)
        assertThat(writtenFile.exists()).isTrue()
        assertThat(writtenFile.readText()).isEqualTo(rawText)
        writtenFile.delete()
    }

    @Test
    fun progressFormatting_viaFileSizeFormatter_matchesExpectedOutput() {
        val info = DownloadProgressInfo(
            id = 10L,
            fileName = "installer.apk",
            filePath = "/downloads/installer.apk",
            mimeType = "application/vnd.android.package-archive",
            status = DownloadStatus.DOWNLOADING,
            bytesDownloaded = 15 * 1024 * 1024L,
            totalBytes = 60 * 1024 * 1024L,
            speedBytesPerSec = 1_572_864L // 1.5 MB/s
        )

        // Progress text
        val progressText = FileSizeFormatter.formatProgressText(info.bytesDownloaded, info.totalBytes)
        assertThat(progressText).isEqualTo("15.0 MB / 60.0 MB (25%)")

        // Speed text
        val speedText = "${FileSizeFormatter.formatFileSize(info.speedBytesPerSec)}/s"
        assertThat(speedText).isEqualTo("1.5 MB/s")

        // Indeterminate formatting
        val indeterminateInfo = info.copy(totalBytes = 0L)
        val indeterminateText = FileSizeFormatter.formatProgressText(indeterminateInfo.bytesDownloaded, indeterminateInfo.totalBytes)
        assertThat(indeterminateText).isEqualTo("15.0 MB")
    }

    @Test
    fun performDownload_whenContentDispositionChangesFilename_renamesToRedirectDestination() = testScope.runTest {
        val downloadedContent = "Redirected PDF file content payload"
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val body = downloadedContent.toByteArray().toResponseBody("application/pdf".toMediaType())
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Disposition", "attachment; filename=\"redirected_final.pdf\"")
                    .header("Content-Type", "application/pdf")
                    .header("Content-Length", body.contentLength().toString())
                    .body(body)
                    .build()
            }
            .build()

        val downloadManagerWithMock = HoriDownloadManager(
            context = context,
            downloadRepository = fakeRepository,
            ioDispatcher = testDispatcher,
            okHttpClient = mockClient
        )

        val initialFile = File(tempFolder.root, "initial_name.bin")
        val downloadId = fakeRepository.recordDownload(
            url = "https://example.com/download/start",
            fileName = "initial_name.bin",
            filePath = initialFile.absolutePath,
            mimeType = "application/octet-stream",
            contentLength = downloadedContent.length.toLong(),
            status = DownloadStatus.PENDING,
            bytesDownloaded = 0L,
            totalBytes = downloadedContent.length.toLong()
        )

        var completedItem: DownloadItem? = null
        downloadManagerWithMock.performDownload(
            id = downloadId,
            referer = null,
            userAgent = null,
            onProgress = {},
            onComplete = { item -> completedItem = item },
            onError = { _, _ -> }
        )
        advanceUntilIdle()

        assertThat(completedItem).isNotNull()
        assertThat(completedItem?.fileName).isEqualTo("redirected_final.pdf")
        assertThat(completedItem?.status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(completedItem?.filePath).endsWith("redirected_final.pdf")

        // Final target file must exist with downloaded content
        val finalDestinationFile = File(completedItem!!.filePath)
        assertThat(finalDestinationFile.exists()).isTrue()
        assertThat(finalDestinationFile.readText()).isEqualTo(downloadedContent)

        // Initial pre-redirect destination file and .crdownload must NOT exist
        assertThat(initialFile.exists()).isFalse()
        assertThat(File("${initialFile.absolutePath}.crdownload").exists()).isFalse()

        // Repository must reflect updated fileName and filePath
        val repoItem = fakeRepository.getDownloadById(downloadId)
        assertThat(repoItem).isNotNull()
        assertThat(repoItem?.fileName).isEqualTo("redirected_final.pdf")
        assertThat(repoItem?.filePath).isEqualTo(finalDestinationFile.absolutePath)

        // Cleanup
        finalDestinationFile.delete()
    }

    /**
     * In-memory fake implementation of [DownloadRepository] for unit testing.
     */
    class FakeDownloadRepository : DownloadRepository {
        private val idGenerator = AtomicLong(1L)
        private val storage = ConcurrentHashMap<Long, DownloadItem>()
        private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())

        private fun notifyChange() {
            _downloadsFlow.value = storage.values.sortedByDescending { it.timestamp }
        }

        override fun getAllDownloads(): Flow<List<DownloadItem>> = _downloadsFlow

        override suspend fun getDownloadById(id: Long): DownloadItem? = storage[id]

        override suspend fun getActiveOrPendingDownloads(): List<DownloadItem> {
            return storage.values.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            }
        }

        override suspend fun recordDownload(
            url: String,
            fileName: String,
            filePath: String,
            mimeType: String?,
            contentLength: Long,
            downloadId: Long,
            status: DownloadStatus,
            bytesDownloaded: Long,
            totalBytes: Long,
            etag: String?
        ): Long {
            val id = idGenerator.getAndIncrement()
            val item = DownloadItem(
                id = id,
                url = url,
                fileName = fileName,
                filePath = filePath,
                mimeType = mimeType,
                contentLength = contentLength,
                downloadId = downloadId,
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                etag = etag,
                timestamp = System.currentTimeMillis()
            )
            storage[id] = item
            notifyChange()
            return id
        }

        override suspend fun updateProgress(
            id: Long,
            bytesDownloaded: Long,
            totalBytes: Long,
            status: DownloadStatus
        ) {
            val existing = storage[id] ?: return
            storage[id] = existing.copy(
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = status
            )
            notifyChange()
        }

        override suspend fun updateStatus(id: Long, status: DownloadStatus, errorMessage: String?) {
            val existing = storage[id] ?: return
            storage[id] = existing.copy(
                status = status,
                errorMessage = errorMessage
            )
            notifyChange()
        }

        override suspend fun updateFilePath(id: Long, fileName: String, filePath: String) {
            val existing = storage[id] ?: return
            storage[id] = existing.copy(
                fileName = fileName,
                filePath = filePath
            )
            notifyChange()
        }

        override suspend fun deleteDownload(id: Long, deleteFile: Boolean) {
            val item = storage.remove(id)
            if (deleteFile && item != null) {
                try {
                    val file = File(item.filePath)
                    if (file.exists()) file.delete()
                    val crdownload = File("${item.filePath}.crdownload")
                    if (crdownload.exists()) crdownload.delete()
                } catch (_: Throwable) {}
            }
            notifyChange()
        }

        override suspend fun clearAllDownloads(deleteFiles: Boolean) {
            if (deleteFiles) {
                storage.values.forEach { item ->
                    try {
                        val file = File(item.filePath)
                        if (file.exists()) file.delete()
                        val crdownload = File("${item.filePath}.crdownload")
                        if (crdownload.exists()) crdownload.delete()
                    } catch (_: Throwable) {}
                }
            }
            storage.clear()
            notifyChange()
        }
    }
}
