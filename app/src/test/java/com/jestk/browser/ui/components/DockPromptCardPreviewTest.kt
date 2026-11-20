package com.jestk.browser.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import androidx.compose.ui.unit.Dp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.ui.overlays.AddEditSearchEngineDialog
import com.jestk.browser.ui.overlays.AddFavoriteDialog
import com.jestk.browser.ui.overlays.DuplicateDownloadPrompt
import com.jestk.browser.ui.overlays.InsecureHttpWarningDialog
import com.jestk.browser.ui.theme.JestkBrowserTheme
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
class DockPromptCardPreviewTest {
    @Test
    fun renderPromptPreviews() {
        render("dock_prompt_duplicate_light.png", isDark = false, fontScale = 1.0f) { dark ->
            DuplicateDownloadPrompt("quarterly-financial-report-final-version-2026-11.pdf", dark, {}, {}, {})
        }
        render("dock_prompt_duplicate_dark.png", isDark = true, fontScale = 1.0f) { dark ->
            DuplicateDownloadPrompt("quarterly-financial-report-final-version-2026-11.pdf", dark, {}, {}, {})
        }
        render("dock_prompt_insecure_dark.png", isDark = true, fontScale = 1.0f) { dark ->
            InsecureHttpWarningDialog(dark, "http://example.com/login", {}, {})
        }
        render("dock_prompt_duplicate_large_font.png", isDark = false, fontScale = 1.3f) { dark ->
            DuplicateDownloadPrompt("quarterly-financial-report-final-version-2026-11.pdf", dark, {}, {}, {})
        }
    }

    @Test
    fun renderFormPreviews() {
        val engines = SearchEngine.DEFAULT_ENGINES
        for (dark in listOf(false, true)) {
            val suffix = if (dark) "dark" else "light"
            render("dock_prompt_favorite_$suffix.png", dark, 1.0f) { d ->
                AddFavoriteDialog(d, {}, { _, _ -> }, initialTitle = "GitHub", initialUrl = "https://github.com")
            }
            render("dock_prompt_favorite_empty_$suffix.png", dark, 1.0f) { d -> AddFavoriteDialog(d, {}, { _, _ -> }) }
            render("dock_prompt_favorite_large_font_$suffix.png", dark, 1.3f) { d ->
                AddFavoriteDialog(d, {}, { _, _ -> }, initialTitle = "GitHub", initialUrl = "https://github.com")
            }
            render("dock_prompt_engine_$suffix.png", dark, 1.0f) { d ->
                AddEditSearchEngineDialog(d, engines, null, {}, { _, _, _, _ -> })
            }
            render("dock_prompt_engine_edit_large_font_$suffix.png", dark, 1.3f) { d ->
                AddEditSearchEngineDialog(d, engines, engines.first(), {}, { _, _, _, _ -> })
            }
            render("dock_prompt_engine_ime_$suffix.png", dark, 1.15f, imeBottom = 340.dp) { d ->
                AddEditSearchEngineDialog(d, engines, engines.first(), {}, { _, _, _, _ -> })
            }
            render("dock_prompt_favorite_ime_$suffix.png", dark, 1.15f, imeBottom = 340.dp) { d ->
                AddFavoriteDialog(d, {}, { _, _ -> }, initialTitle = "GitHub", initialUrl = "https://github.com")
            }
        }
    }

    private fun render(
        fileName: String,
        isDark: Boolean,
        fontScale: Float,
        imeBottom: Dp? = null,
        prompt: @Composable (Boolean) -> Unit,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides Density(3f, fontScale),
                    LocalDockPromptAnimateEnter provides false,
                    LocalDockPromptImeBottom provides imeBottom,
                ) {
                    JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0F172A) else Color(0xFFE9F6F2))) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(if (isDark) Color(0xFF1E293B) else Color.White, RoundedCornerShape(25.dp)),
                            )
                            prompt(isDark)
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
        val width = 1080
        val height = 1900
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))
        val outputFile = File("build/$fileName")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
