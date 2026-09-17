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
}
