package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.engine.download.HoriDownloadManagerTest.FakeDownloadRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DuplicateDownloadCheckTest {
    private val url = "https://example.com/report.pdf"

    private suspend fun FakeDownloadRepository.record(
        url: String,
        path: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
    ) = recordDownload(url, "report.pdf", path, "application/pdf", 10L, status = status)

    @Test
    fun completedDownloadWithExistingFile_isFound() = runTest {
        val repo = FakeDownloadRepository()
        repo.record(url, "/d/report.pdf")

        val found = findExistingDownload(repo, url) { true }

        assertThat(found?.filePath).isEqualTo("/d/report.pdf")
    }

    @Test
    fun missingFile_isNotFound() = runTest {
        val repo = FakeDownloadRepository()
        repo.record(url, "/d/report.pdf")

        assertThat(findExistingDownload(repo, url) { false }).isNull()
    }

    @Test
    fun olderCopyStillOnDisk_isFoundWhenNewestIsGone() = runTest {
        val repo = FakeDownloadRepository()
        repo.record(url, "/d/report.pdf")
        Thread.sleep(2)
        repo.record(url, "/d/report (1).pdf")

        val found = findExistingDownload(repo, url) { it == "/d/report.pdf" }

        assertThat(found?.filePath).isEqualTo("/d/report.pdf")
    }

    @Test
    fun unfinishedDownload_isNotFound() = runTest {
        val repo = FakeDownloadRepository()
        repo.record(url, "/d/report.pdf", DownloadStatus.DOWNLOADING)
        repo.record(url, "/d/report.pdf", DownloadStatus.FAILED)

        assertThat(findExistingDownload(repo, url) { true }).isNull()
    }

    @Test
    fun otherUrl_isNotFound() = runTest {
        val repo = FakeDownloadRepository()
        repo.record("https://example.com/other.pdf", "/d/other.pdf")

        assertThat(findExistingDownload(repo, url) { true }).isNull()
    }

    @Test
    fun dataAndBlobUrls_areNeverDuplicates() = runTest {
        val repo = FakeDownloadRepository()
        repo.record("data:text/plain;base64,AAAA", "/d/a.txt")
        repo.record("blob:https://example.com/1234", "/d/b.txt")

        assertThat(findExistingDownload(repo, "data:text/plain;base64,AAAA") { true }).isNull()
        assertThat(findExistingDownload(repo, "blob:https://example.com/1234") { true }).isNull()
    }
}
