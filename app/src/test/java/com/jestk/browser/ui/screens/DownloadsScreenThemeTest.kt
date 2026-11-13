package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PRO-18: the Downloads screen follows the resolved app theme, not the raw setting. With the
 * default System theme mode on a dark phone, `themeMode == ThemeMode.DARK` was false and the
 * screen stayed light.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsScreenThemeTest {
    @Test
    fun darkAppThemeWithSystemSettingRendersDarkBackground() {
        assertThat(backgroundPixel(appTheme = ThemeMode.DARK)).isEqualTo(DarkBackground.toArgb())
    }

    @Test
    fun lightAppThemeRendersLightBackground() {
        assertThat(backgroundPixel(appTheme = ThemeMode.LIGHT)).isEqualTo(ProstoCanvasFloor.toArgb())
    }

    private fun backgroundPixel(appTheme: ThemeMode): Int {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // The setting stays at its default (System); only the resolved app theme changes.
        val state = BrowserUiState(activeScreen = ScreenType.DOWNLOADS)
        assertThat(state.settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        val view =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = appTheme) {
                        DownloadsScreen(uiState = state, onEvent = {})
                    }
                }
            }
        activity.setContentView(view)
        val size = 400
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(2, size - 2)
    }
}
