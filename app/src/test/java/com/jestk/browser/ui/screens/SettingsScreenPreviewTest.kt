package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
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
class SettingsScreenPreviewTest {

    @Test
    fun renderSettingsScreenLightPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state = BrowserUiState(
            activeScreen = ScreenType.SETTINGS,
            isDefaultBrowser = false
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    SettingsScreen(
                        uiState = state,
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

        val outputFile = File("build/settings_screen_light_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderSettingsScreenDarkPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state = BrowserUiState(
            activeScreen = ScreenType.SETTINGS,
            isDefaultBrowser = true
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    SettingsScreen(
                        uiState = state,
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

        val outputFile = File("build/settings_screen_dark_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
