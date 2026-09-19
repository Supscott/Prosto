package com.jestk.browser.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guards the frosted-glass fallback used when a [HazeState] has no registered source,
 * which is the case for the WebView viewport (Haze must not capture a live WebView into
 * its per-frame GraphicsLayer, see JestkApp). The tile must stay semi-transparent so the
 * content behind it shows through, instead of Haze's opaque scrim boost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrostedGlassFallbackTest {
    @Composable
    private fun TileOverBackground(backgroundColor: Color) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(120.dp)
                        .liquidFrostedGlass(
                            isDark = false,
                            hazeState = HazeState(), // state provided, but no source registered
                        ),
            )
        }
    }

    private fun renderTileOver(backgroundColor: Color): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val size = 200
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        TileOverBackground(backgroundColor)
                    }
                }
            }
        activity.setContentView(composeView)
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, size, size)

        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            composeView.draw(android.graphics.Canvas(bitmap))
        }
    }

    @Test
    fun tileStaysTranslucentWhenHazeHasNoSource() {
        val overWhite = renderTileOver(Color.White).getPixel(100, 100)
        val overBlack = renderTileOver(Color.Black).getPixel(100, 100)

        assertTrue(
            "Frosted glass without a haze source must let the background show through, " +
                "but rendered ${Integer.toHexString(overWhite)} over white and " +
                "${Integer.toHexString(overBlack)} over black",
            overWhite != overBlack,
        )
    }
}
