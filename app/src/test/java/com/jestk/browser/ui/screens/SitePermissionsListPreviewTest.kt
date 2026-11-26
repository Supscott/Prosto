package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SitePermissionsListPreviewTest {
    private val both = setOf(SiteResource.CAMERA, SiteResource.MICROPHONE)
    private val grants = mapOf(
        "https://meet.example.com" to both,
        "https://photos.example.org" to setOf(SiteResource.CAMERA),
        "http://127.0.0.1:8765" to setOf(SiteResource.MICROPHONE),
        "https://a-very-long-subdomain-name.conference-platform.example.co.uk" to both,
    )

    @Test
    fun siteOriginLabel_dropsTheSchemeAndKeepsANonDefaultPort() {
        assertThat(siteOriginLabel("https://example.com")).isEqualTo("example.com")
        assertThat(siteOriginLabel("http://127.0.0.1:8765")).isEqualTo("127.0.0.1:8765")
    }

    @Test
    fun renderEnglish() {
        render("site_permissions_en_light.png", isDark = false, fontScale = 1f)
        render("site_permissions_en_dark.png", isDark = true, fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "ru")
    fun renderRussianLargeFont() {
        render("site_permissions_ru_1_3_light.png", isDark = false, fontScale = 1.3f)
        render("site_permissions_ru_1_3_dark.png", isDark = true, fontScale = 1.3f)
    }

    private fun render(fileName: String, isDark: Boolean, fontScale: Float) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                    JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        // A dialog's content width (a 280dp dialog minus its 24dp padding) plus a margin.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val card = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White).padding(24.dp)
                            Column(card) {
                                SitePermissionsList(grants, isDark = isDark, onRemove = {}, onRemoveAll = {})
                            }
                            Column(card) {
                                SitePermissionsList(emptyMap(), isDark = isDark, onRemove = {}, onRemoveAll = {})
                            }
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
        val width = 320 * 3
        val height = 2400
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
