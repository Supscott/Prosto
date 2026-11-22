package com.jestk.browser.engine

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.engine.download.HoriDownloadManagerTest.FakeDownloadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewDownloadsConfirmTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    // Not a child of [scope]: the engine hops to Dispatchers.Main, which only runs when the test
    // idles the main looper, so runTest must not wait for these coroutines (it would deadlock).
    private val engineScope = kotlinx.coroutines.CoroutineScope(dispatcher)
    private lateinit var repository: FakeDownloadRepository
    private lateinit var downloads: WebViewDownloads
    private val requests = mutableListOf<DownloadConfirmRequest>()
    private var proceed: (() -> Unit)? = null

    private val dataUrl = "data:text/plain;base64,SGVsbG8gSG9yaSBCcm93c2Vy"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = FakeDownloadRepository()
        val manager = HoriDownloadManager(context, repository, dispatcher)
        downloads = WebViewDownloads(
            applicationContext = context,
            pendingBlobDownloads = PendingBlobDownloads(),
            downloadRepository = { repository },
            downloadManager = { manager },
            activeScope = { engineScope },
            onDownloadStarted = {},
            onDownloadFailed = { _, _ -> },
        )
    }

    private fun attachPrompt() {
        downloads.onConfirmDownload = { request, go ->
            requests += request
            proceed = go
        }
    }

    private suspend fun savedCount() = repository.getAllDownloads().first().size

    @Test
    fun withoutPromptCallback_dataUrlIsSavedAtOnce() = scope.runTest {
        downloads.handleDataUrlDownload(dataUrl, "inline; filename=\"hello.txt\"", "text/plain")
        advanceUntilIdle()

        assertThat(savedCount()).isEqualTo(1)
    }

    @Test
    fun withPromptCallback_dataUrlWaitsForProceed() = scope.runTest {
        attachPrompt()

        downloads.handleDataUrlDownload(dataUrl, "inline; filename=\"hello.txt\"", "text/plain")
        advanceUntilIdle()

        assertThat(requests.single().fileName).isEqualTo("hello.txt")
        assertThat(requests.single().sizeBytes).isEqualTo(18L)
        assertThat(savedCount()).isEqualTo(0)

        proceed!!.invoke()
        advanceUntilIdle()

        assertThat(savedCount()).isEqualTo(1)
    }

    @Test
    fun withPromptCallback_droppedProceedSavesNothing() = scope.runTest {
        attachPrompt()

        downloads.handleDataUrlDownload(dataUrl, "inline; filename=\"hello.txt\"", "text/plain")
        advanceUntilIdle()

        assertThat(savedCount()).isEqualTo(0)
    }

    @Test
    fun confirmedDownload_skipsThePrompt() = scope.runTest {
        attachPrompt()

        downloads.handleDownload(dataUrl, null, "inline; filename=\"hello.txt\"", "text/plain", 0L, null, confirmed = true)
        advanceUntilIdle()

        assertThat(requests).isEmpty()
        assertThat(savedCount()).isEqualTo(1)
    }

    @Test
    fun pageStartedDataDownload_isPrompted() = scope.runTest {
        attachPrompt()

        downloads.handleDownload(dataUrl, null, "inline; filename=\"hello.txt\"", "text/plain", 0L, null)
        advanceUntilIdle()

        assertThat(requests).hasSize(1)
        assertThat(savedCount()).isEqualTo(0)
    }

    @Test
    fun httpDownload_isPromptedWithNameSizeAndNothingIsEnqueuedBeforeProceed() = scope.runTest {
        attachPrompt()

        downloads.handleDownload("https://example.com/files/report.pdf", null, null, "application/pdf", 2048L, null)
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(requests.single()).isEqualTo(DownloadConfirmRequest("report.pdf", 2048L, null))
        assertThat(savedCount()).isEqualTo(0)
    }

    @Test
    fun alreadyDownloadedFile_showsOnlyTheDuplicateCard() = scope.runTest {
        val url = "https://example.com/files/report.pdf"
        val existingFile = java.io.File.createTempFile("report", ".pdf").also { it.deleteOnExit() }
        repository.recordDownload(
            url, "report.pdf", existingFile.absolutePath, "application/pdf", 10L,
            status = com.jestk.browser.domain.model.DownloadStatus.COMPLETED,
        )
        attachPrompt()
        var duplicates = 0
        downloads.onDuplicateDownload = { _, _ -> duplicates++ }

        downloads.handleDownload(url, null, null, "application/pdf", 2048L, null)
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(duplicates).isEqualTo(1)
        assertThat(requests).isEmpty()
    }

    @Test
    fun unknownContentLength_hasNoSize() = scope.runTest {
        attachPrompt()

        downloads.handleDownload("https://example.com/files/report.pdf", null, null, "application/pdf", -1L, null)
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(requests.single().sizeBytes).isNull()
    }

    @Test
    fun requestHelpers_extractHostsAndDataSizes() {
        assertThat(downloadHostOf("https://www.example.com/a?b=1")).isEqualTo("www.example.com")
        assertThat(downloadHostOf("about:blank")).isNull()
        assertThat(downloadHostOf(null)).isNull()
        assertThat(blobOriginHost("blob:https://files.example.org/1f2e-3d")).isEqualTo("files.example.org")
        assertThat(blobOriginHost("blob:null/1f2e")).isNull()

        val plain = dataUrlConfirmRequest("data:text/plain,hello%20world", null, null, "x.com")
        assertThat(plain.sizeBytes).isEqualTo(11L)
        assertThat(plain.host).isEqualTo("x.com")
        assertThat(dataUrlConfirmRequest("data:text/plain,", null, null, null).sizeBytes).isNull()
    }
}
