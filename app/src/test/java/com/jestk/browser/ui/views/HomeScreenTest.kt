package com.jestk.browser.ui.views

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.FavoriteItem
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
class HomeScreenTest {

    @Test
    fun formatStatCount_formatsNumbersCorrectly() {
        assertThat(formatStatCount(-5)).isEqualTo("0")
        assertThat(formatStatCount(0)).isEqualTo("0")
        assertThat(formatStatCount(14)).isEqualTo("14")
        assertThat(formatStatCount(999)).isEqualTo("999")
        assertThat(formatStatCount(1000)).isEqualTo("1k")
        assertThat(formatStatCount(1200)).isEqualTo("1.2k")
        assertThat(formatStatCount(1500)).isEqualTo("1.5k")
        assertThat(formatStatCount(10500)).isEqualTo("10.5k")
        assertThat(formatStatCount(1000000)).isEqualTo("1M")
        assertThat(formatStatCount(2500000)).isEqualTo("2.5M")
    }

    @Test
    fun renderHomeScreen_withProtectionCard_lightMode() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state = BrowserUiState(
            blockedAdsCount = 14L,
            blockedTrackersCount = 1200L,
            favorites = listOf(
                FavoriteItem(1, "Google", "https://google.com"),
                FavoriteItem(2, "GitHub", "https://github.com")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    HomeScreen(
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

        val outputFile = File("build/home_screen_light_protection_card.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }

    @Test
    fun renderHomeScreen_withProtectionCard_darkMode() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state = BrowserUiState(
            blockedAdsCount = 42L,
            blockedTrackersCount = 380L,
            favorites = listOf(
                FavoriteItem(1, "Google", "https://google.com"),
                FavoriteItem(2, "GitHub", "https://github.com")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    HomeScreen(
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

        val outputFile = File("build/home_screen_dark_protection_card.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
