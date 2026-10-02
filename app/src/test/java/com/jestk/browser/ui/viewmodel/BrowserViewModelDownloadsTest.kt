package com.jestk.browser.ui.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.DownloadRepositoryImpl
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BrowserViewModelDownloadsTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var recentTabRepo: RecentTabRepository
    private lateinit var downloadRepo: DownloadRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
        downloadRepo = DownloadRepositoryImpl(db.downloadDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel =
        BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            recentTabRepository = recentTabRepo,
            downloadRepository = downloadRepo,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun openDownloads_setsActiveOverlayToDownloads() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(BrowserUiEvent.OpenDownloads)
            assertThat(vm.uiState.value.activeScreen).isEqualTo(ScreenType.DOWNLOADS)
            assertThat(vm.uiState.value.isDownloadsVisible).isTrue()
        }

    @Test
    fun handleBackPress_dismissesDownloadsOverlay() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(BrowserUiEvent.OpenDownloads)
            assertThat(vm.uiState.value.activeScreen).isEqualTo(ScreenType.DOWNLOADS)

            vm.onEvent(BrowserUiEvent.HandleBackPress)
            assertThat(vm.uiState.value.activeScreen).isEqualTo(ScreenType.NONE)
        }

    @Test
    fun downloadRepositoryFlow_updatesUiStateDownloads() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            assertThat(vm.uiState.value.downloads).isEmpty()

            val id =
                downloadRepo.recordDownload(
                    url = "https://example.com/file.zip",
                    fileName = "file.zip",
                    filePath = "/Download/file.zip",
                    mimeType = "application/zip",
                    contentLength = 2048L,
                )
            advanceUntilIdle()

            assertThat(vm.uiState.value.downloads).hasSize(1)
            assertThat(
                vm.uiState.value.downloads[0]
                    .id,
            ).isEqualTo(id)
            assertThat(
                vm.uiState.value.downloads[0]
                    .fileName,
            ).isEqualTo("file.zip")

            vm.onEvent(BrowserUiEvent.DeleteDownload(id))
            advanceUntilIdle()

            assertThat(vm.uiState.value.downloads).isEmpty()
        }

    @Test
    fun deleteDownload_withPhysicalFile_deletesFileFromDisk() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            val tempFile = java.io.File.createTempFile("vm_test_download", ".mp3")
            tempFile.writeText("test mp3 data")
            assertThat(tempFile.exists()).isTrue()

            val id = downloadRepo.recordDownload(
                url = "https://example.com/test.mp3",
                fileName = tempFile.name,
                filePath = tempFile.absolutePath,
                mimeType = "audio/mpeg",
                contentLength = tempFile.length()
            )
            advanceUntilIdle()

            assertThat(vm.uiState.value.downloads).hasSize(1)

            vm.onEvent(BrowserUiEvent.DeleteDownload(id, deleteFile = true))
            advanceUntilIdle()

            assertThat(vm.uiState.value.downloads).isEmpty()
            assertThat(tempFile.exists()).isFalse()
        }
}
