package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.screens.OpenInAppExcludedSitesList
import com.jestk.browser.ui.theme.JestkBrowserTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpenInAppExclusionPreviewTest {
    @Test
    fun renderOpenInAppExclusionPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val sites = setOf("reddit.com", "very-long-site-name-example.co.uk", "x.com")
        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE2E8F0)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        OpenInAppExclusionPill("reddit.com", false, "k", null, {}, {})
                        Column(Modifier.background(Color.White).padding(16.dp)) {
                            OpenInAppExcludedSitesList(sites, isDark = false, onRemove = {})
                        }
                    }
                    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                        OpenInAppExclusionPill("very-long-site-name-example.co.uk", true, "k", null, {}, {})
                        Column(Modifier.background(Color(0xFF1E293B)).padding(16.dp)) {
                            OpenInAppExcludedSitesList(sites, isDark = true, onRemove = {})
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)

        val width = 420
        val height = 560
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputDir = File("build/outputs/roborazzi").also { it.mkdirs() }
        val outputFile = File(outputDir, "open_in_app_exclusion.png")
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assert(outputFile.exists() && outputFile.length() > 0)
    }
}
