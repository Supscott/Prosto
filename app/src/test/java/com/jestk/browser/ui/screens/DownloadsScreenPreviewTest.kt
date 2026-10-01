package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
}
