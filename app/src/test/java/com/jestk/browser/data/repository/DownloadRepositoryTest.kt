package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DownloadRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = DownloadRepositoryImpl(db.downloadDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun recordDownload_and_getAllDownloads() = runTest(testDispatcher) {
        val id = repository.recordDownload(
            url = "https://example.com/document.pdf",
            fileName = "document.pdf",
            filePath = "/storage/emulated/0/Download/document.pdf",
            mimeType = "application/pdf",
            contentLength = 4096L,
            downloadId = 101L
        )
        assertThat(id).isGreaterThan(0L)

        val downloads = repository.getAllDownloads().first()
        assertThat(downloads).hasSize(1)
        val item = downloads.first()
        assertThat(item.fileName).isEqualTo("document.pdf")
        assertThat(item.filePath).isEqualTo("/storage/emulated/0/Download/document.pdf")
        assertThat(item.contentLength).isEqualTo(4096L)
        assertThat(item.downloadId).isEqualTo(101L)
    }

    @Test
    fun deleteDownload_removesItem() = runTest(testDispatcher) {
        val id = repository.recordDownload(
            url = "https://example.com/archive.zip",
            fileName = "archive.zip",
            filePath = "/storage/emulated/0/Download/archive.zip",
            mimeType = "application/zip",
            contentLength = 8192L
        )

        val before = repository.getAllDownloads().first()
        assertThat(before).hasSize(1)

        repository.deleteDownload(id)

        val after = repository.getAllDownloads().first()
        assertThat(after).isEmpty()
    }

    @Test
    fun clearAllDownloads_removesAllEntries() = runTest(testDispatcher) {
        repository.recordDownload(
            url = "https://example.com/file1.png",
            fileName = "file1.png",
            filePath = "/storage/emulated/0/Download/file1.png",
            mimeType = "image/png",
            contentLength = 1024L
        )
        repository.recordDownload(
            url = "https://example.com/file2.png",
            fileName = "file2.png",
            filePath = "/storage/emulated/0/Download/file2.png",
            mimeType = "image/png",
            contentLength = 2048L
        )

        val before = repository.getAllDownloads().first()
        assertThat(before).hasSize(2)

        repository.clearAllDownloads()

        val after = repository.getAllDownloads().first()
        assertThat(after).isEmpty()
    }

    @Test
    fun updateProgress_and_deleteWithPhysicalFile() = runTest(testDispatcher) {
        val tempFile = java.io.File.createTempFile("test_download", ".mp3")
        tempFile.writeText("sample audio content")
        assertThat(tempFile.exists()).isTrue()

        val id = repository.recordDownload(
            url = "https://example.com/song.mp3",
            fileName = tempFile.name,
            filePath = tempFile.absolutePath,
            mimeType = "audio/mpeg",
            contentLength = tempFile.length()
        )

        repository.updateProgress(
            id = id,
            bytesDownloaded = 500L,
            totalBytes = 1000L,
            status = com.jestk.browser.domain.model.DownloadStatus.DOWNLOADING
        )

        val item = repository.getDownloadById(id)
        assertThat(item?.status).isEqualTo(com.jestk.browser.domain.model.DownloadStatus.DOWNLOADING)
        assertThat(item?.bytesDownloaded).isEqualTo(500L)

        // Delete with physical file
        repository.deleteDownload(id, deleteFile = true)

        assertThat(repository.getDownloadById(id)).isNull()
        assertThat(tempFile.exists()).isFalse()
    }

    @Test
    fun deleteDownload_whenScopeCancelled_rethrowsCancellationException() = runTest(testDispatcher) {
        val job = kotlinx.coroutines.Job()
        job.cancel()
        var caughtCancellation = false
        try {
            kotlinx.coroutines.withContext(job) {
                repository.deleteDownload(1L, deleteFile = true)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            caughtCancellation = true
        }
        assertThat(caughtCancellation).isTrue()
    }

    @Test
    fun clearAllDownloads_whenScopeCancelled_rethrowsCancellationException() = runTest(testDispatcher) {
        val job = kotlinx.coroutines.Job()
        job.cancel()
        var caughtCancellation = false
        try {
            kotlinx.coroutines.withContext(job) {
                repository.clearAllDownloads(deleteFiles = true)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            caughtCancellation = true
        }
        assertThat(caughtCancellation).isTrue()
    }
}
