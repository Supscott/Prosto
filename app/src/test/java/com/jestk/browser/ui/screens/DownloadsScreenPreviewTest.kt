package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsScreenPreviewTest {

    @Test
    fun renderEmptyDownloadsScreenPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val emptyState = BrowserUiState(
            activeScreen = ScreenType.DOWNLOADS,
            downloads = emptyList()
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    DownloadsScreen(
                        uiState = emptyState,
                        onEvent = {}
                    )
                }
            }
        }

        activity.setContentView(composeView)
val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/downloads_empty_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderPopulatedDownloadsScreenPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleDownloads = listOf(
            DownloadItem(
                id = 1L,
                fileName = "quarterly_financial_report_2026.pdf",
                filePath = "/Download/quarterly_financial_report_2026.pdf",
                mimeType = "application/pdf",
                contentLength = 4_850_000L,
                timestamp = System.currentTimeMillis() - 3600_000L
            ),
            DownloadItem(
                id = 2L,
                fileName = "sunset_beach_wallpaper.jpg",
                filePath = "/Download/sunset_beach_wallpaper.jpg",
                mimeType = "image/jpeg",
                contentLength = 2_150_000L,
                timestamp = System.currentTimeMillis() - 7200_000L
            ),
            DownloadItem(
                id = 3L,
                fileName = "project_assets_bundle.zip",
                filePath = "/Download/project_assets_bundle.zip",
                mimeType = "application/zip",
                contentLength = 15_400_000L,
                timestamp = System.currentTimeMillis() - 86400_000L
            ),
            DownloadItem(
                id = 4L,
                fileName = "browser_beta_v1.2.apk",
                filePath = "/Download/browser_beta_v1.2.apk",
                mimeType = "application/vnd.android.package-archive",
                contentLength = 28_900_000L,
                timestamp = System.currentTimeMillis() - 172800_000L
            )
        )

        val populatedState = BrowserUiState(
            activeScreen = ScreenType.DOWNLOADS,
            downloads = sampleDownloads
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    DownloadsScreen(
                        uiState = populatedState,
                        onEvent = {}
                    )
                }
            }
        }

        activity.setContentView(composeView)
val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/downloads_populated_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderActiveDownloadsScreenPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleDownloads = listOf(
            DownloadItem(
                id = 1L,
                fileName = "favorite_song.mp3",
                filePath = "/Download/favorite_song.mp3",
                mimeType = "audio/mpeg",
                contentLength = 45_000_000L,
                status = com.jestk.browser.domain.model.DownloadStatus.DOWNLOADING,
                bytesDownloaded = 14_200_000L,
                totalBytes = 45_000_000L,
                timestamp = System.currentTimeMillis()
            ),
            DownloadItem(
                id = 2L,
                fileName = "large_archive.zip",
                filePath = "/Download/large_archive.zip",
                mimeType = "application/zip",
                contentLength = 100_000_000L,
                status = com.jestk.browser.domain.model.DownloadStatus.PAUSED,
                bytesDownloaded = 50_000_000L,
                totalBytes = 100_000_000L,
                timestamp = System.currentTimeMillis() - 60_000L
            ),
            DownloadItem(
                id = 3L,
                fileName = "corrupt_file.bin",
                filePath = "/Download/corrupt_file.bin",
                mimeType = "application/octet-stream",
                contentLength = 10_000_000L,
                status = com.jestk.browser.domain.model.DownloadStatus.FAILED,
                bytesDownloaded = 2_000_000L,
                totalBytes = 10_000_000L,
                errorMessage = "Network timeout",
                timestamp = System.currentTimeMillis() - 120_000L
            ),
            DownloadItem(
                id = 4L,
                fileName = "document.pdf",
                filePath = "/Download/document.pdf",
                mimeType = "application/pdf",
                contentLength = 5_000_000L,
                status = com.jestk.browser.domain.model.DownloadStatus.COMPLETED,
                bytesDownloaded = 5_000_000L,
                totalBytes = 5_000_000L,
                timestamp = System.currentTimeMillis() - 3600_000L
            )
        )

        val progressMap = mapOf(
            1L to com.jestk.browser.engine.download.DownloadProgressInfo(
                id = 1L,
                fileName = "favorite_song.mp3",
                filePath = "/Download/favorite_song.mp3",
                mimeType = "audio/mpeg",
                status = com.jestk.browser.domain.model.DownloadStatus.DOWNLOADING,
                bytesDownloaded = 14_200_000L,
                totalBytes = 45_000_000L,
                speedBytesPerSec = 2_400_000L
            ),
            2L to com.jestk.browser.engine.download.DownloadProgressInfo(
                id = 2L,
                fileName = "large_archive.zip",
                filePath = "/Download/large_archive.zip",
                mimeType = "application/zip",
                status = com.jestk.browser.domain.model.DownloadStatus.PAUSED,
                bytesDownloaded = 50_000_000L,
                totalBytes = 100_000_000L
            )
        )

        val activeState = BrowserUiState(
            activeScreen = ScreenType.DOWNLOADS,
            downloads = sampleDownloads,
            downloadProgressMap = progressMap,
            activeDownloadIds = setOf(1L, 2L)
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    DownloadsScreen(
                        uiState = activeState,
                        onEvent = {}
                    )
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/downloads_active_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
