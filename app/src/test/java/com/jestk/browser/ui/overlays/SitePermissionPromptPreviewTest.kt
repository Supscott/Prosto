package com.jestk.browser.ui.overlays

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
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.components.LocalDockPromptAnimateEnter
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

private val BOTH = setOf(SiteResource.CAMERA, SiteResource.MICROPHONE)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SitePermissionPromptPreviewTest {
    @Test
    fun renderLightAndDark() {
        render("site_permission_camera_microphone_light.png", isDark = false) { d ->
            SitePermissionConfirmPrompt("example.com", BOTH, canRemember = true, isDark = d) {}
        }
        render("site_permission_camera_dark.png", isDark = true) { d ->
            SitePermissionConfirmPrompt("meet.example.org", setOf(SiteResource.CAMERA), canRemember = true, isDark = d) {}
        }
        render("site_permission_microphone_dark.png", isDark = true) { d ->
            SitePermissionConfirmPrompt("voice.example.org", setOf(SiteResource.MICROPHONE), canRemember = true, isDark = d) {}
        }
    }

    @Test
    fun renderIncognitoTwoButtons() {
        render("site_permission_incognito_light.png", isDark = false) { d ->
            SitePermissionConfirmPrompt("example.com", BOTH, canRemember = false, isDark = d) {}
        }
        render("site_permission_incognito_dark.png", isDark = true) { d ->
            SitePermissionConfirmPrompt("example.com", BOTH, canRemember = false, isDark = d) {}
        }
    }

    @Test
    @Config(qualifiers = "ru")
    fun renderRussianNarrowLargeFont() {
        render("site_permission_ru_narrow_large_font_light.png", isDark = false, fontScale = 1.3f, widthDp = 320) { d ->
            SitePermissionConfirmPrompt("video-conference.example.com", BOTH, canRemember = true, isDark = d) {}
        }
        render("site_permission_ru_narrow_large_font_dark.png", isDark = true, fontScale = 1.3f, widthDp = 320) { d ->
            SitePermissionConfirmPrompt("video-conference.example.com", BOTH, canRemember = true, isDark = d) {}
        }
    }

    @Test
    fun renderEnglishLargeFont() {
        render("site_permission_en_large_font_light.png", isDark = false, fontScale = 1.3f) { d ->
            SitePermissionConfirmPrompt("video-conference.example.com", BOTH, canRemember = true, isDark = d) {}
        }
    }

    private fun render(
        fileName: String,
        isDark: Boolean,
        fontScale: Float = 1.0f,
        widthDp: Int = 360,
        prompt: @Composable (Boolean) -> Unit,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides Density(3f, fontScale),
                    LocalDockPromptAnimateEnter provides false,
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
        val width = widthDp * 3
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
