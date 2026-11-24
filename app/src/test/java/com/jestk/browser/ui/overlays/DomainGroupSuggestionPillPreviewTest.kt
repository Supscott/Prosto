package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.DomainGroupSuggestion
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
class DomainGroupSuggestionPillPreviewTest {

    @Test
    fun renderDomainGroupSuggestionPillPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val suggestion = DomainGroupSuggestion(
            workspaceId = "w",
            domain = "youtube.com",
            folderTitle = "Youtube",
            tabIds = listOf("1", "2", "3", "4"),
        )
        val longSuggestion = suggestion.copy(domain = "very-long-site-name-example.co.uk")

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE2E8F0)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Light", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        DomainGroupSuggestionPill(suggestion, isDark = false, accent = null, onCreate = {}, onDismiss = {})
                    }
                    Text("Light, long domain", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        DomainGroupSuggestionPill(longSuggestion, isDark = false, accent = null, onCreate = {}, onDismiss = {})
                    }
                    Text("Dark", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                        DomainGroupSuggestionPill(suggestion, isDark = true, accent = null, onCreate = {}, onDismiss = {})
                    }
                }
            }
        }
        activity.setContentView(composeView)

        val width = 420
        val height = 330
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputDir = File("build/outputs/roborazzi").also { it.mkdirs() }
        val outputFile = File(outputDir, "domain_group_suggestion_pill.png")
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assert(outputFile.exists() && outputFile.length() > 0)
    }
}
