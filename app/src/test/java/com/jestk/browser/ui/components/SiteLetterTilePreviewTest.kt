package com.jestk.browser.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SiteLetterTilePreviewTest {
    private val sites =
        listOf(
            "https://www.wikipedia.org",
            "https://github.com",
            "https://news.ycombinator.com",
            "https://kagi.com",
            "http://example.org:8080",
            "https://yandex.ru",
            "http://192.168.1.1",
            "https://www.reddit.com",
        )

    @Composable
    private fun Section(dark: Boolean, background: Color, textColor: Color) {
        JestkBrowserTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Column(
                modifier = Modifier.fillMaxWidth().background(background).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sites.forEach { SiteLetterTile(site = it, size = 36.dp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sites.forEach { SiteLetterTile(site = it, size = 20.dp, shape = CircleShape) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteLetterTile(site = "https://www.wikipedia.org", size = 22.dp, shape = CircleShape)
                    Text("wikipedia.org", color = textColor)
                }
            }
        }
    }

    private fun render(fileName: String, fontScale: Float) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        Column {
                            Section(dark = false, background = Color.White, textColor = Color(0xFF0F172A))
                            Section(dark = true, background = Color(0xFF1E293B), textColor = Color.White)
                        }
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 480
        val height = 300
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputDir = File("build/outputs/roborazzi").also { it.mkdirs() }
        val outputFile = File(outputDir, fileName)
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assert(outputFile.exists() && outputFile.length() > 0)
    }

    @Test
    fun renderLetterTiles() = render("site_letter_tiles.png", fontScale = 1f)

    @Test
    fun renderLetterTilesAtLargeFontScale() = render("site_letter_tiles_font_1_3.png", fontScale = 1.3f)
}
