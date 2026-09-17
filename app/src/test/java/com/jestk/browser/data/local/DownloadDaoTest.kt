package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.DownloadDao
import com.jestk.browser.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DownloadDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var downloadDao: DownloadDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        downloadDao = db.downloadDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetDownloadsFlow() = runTest {
        val download = DownloadEntity(
            url = "https://example.com/test.pdf",
            fileName = "test.pdf",
            filePath = "/storage/emulated/0/Download/test.pdf",
            mimeType = "application/pdf",
            contentLength = 1024L,
            timestamp = 1000L,
            downloadId = 42L
        )
        val id = downloadDao.insertDownload(download)
        assertThat(id).isGreaterThan(0L)

        val downloads = downloadDao.getAllDownloadsFlow().first()
        assertThat(downloads).hasSize(1)
        assertThat(downloads[0].fileName).isEqualTo("test.pdf")
        assertThat(downloads[0].downloadId).isEqualTo(42L)
        assertThat(downloads[0].contentLength).isEqualTo(1024L)
    }

    @Test
    fun deleteDownloadById() = runTest {
        val id = downloadDao.insertDownload(
            DownloadEntity(
                url = "https://example.com/test.zip",
                fileName = "test.zip",
                filePath = "/storage/emulated/0/Download/test.zip",
                mimeType = "application/zip",
                contentLength = 2048L,
                timestamp = 2000L
            )
        )

        val before = downloadDao.getAllDownloadsFlow().first()
        assertThat(before).hasSize(1)

        downloadDao.deleteDownloadById(id)

        val after = downloadDao.getAllDownloadsFlow().first()
        assertThat(after).isEmpty()
    }

    @Test
    fun clearAllDownloads() = runTest {
        downloadDao.insertDownload(
            DownloadEntity(
                url = "https://example.com/file1.png",
                fileName = "file1.png",
                filePath = "/path/file1.png",
                mimeType = "image/png",
                contentLength = 500L,
                timestamp = 100L
            )
        )
        downloadDao.insertDownload(
            DownloadEntity(
                url = "https://example.com/file2.png",
                fileName = "file2.png",
                filePath = "/path/file2.png",
                mimeType = "image/png",
                contentLength = 600L,
                timestamp = 200L
            )
        )

        val before = downloadDao.getAllDownloadsFlow().first()
        assertThat(before).hasSize(2)

        downloadDao.clearAllDownloads()

        val after = downloadDao.getAllDownloadsFlow().first()
        assertThat(after).isEmpty()
    }
}
