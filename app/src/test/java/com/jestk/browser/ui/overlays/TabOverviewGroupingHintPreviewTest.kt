package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders the grouping hint (scrim + folder icon) on the target cards, light and dark (PRO-203). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabOverviewGroupingHintPreviewTest {
    @Test
    fun renderGroupingHintLightPreview() = render("tab_overview_grouping_hint_light.png", ThemeMode.LIGHT, 1.0f)

    @Test
    fun renderGroupingHintDarkPreview() = render("tab_overview_grouping_hint_dark.png", ThemeMode.DARK, 1.0f)

    @Test
    fun renderGroupingHintLargeFontPreview() = render("tab_overview_grouping_hint_large_font.png", ThemeMode.DARK, 1.3f)

    private fun render(
        fileName: String,
        themeMode: ThemeMode,
        fontScale: Float,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    ) {
                        JestkBrowserTheme(themeMode = themeMode) {
                            TabOverviewGroupingHintSample(isDark = themeMode == ThemeMode.DARK)
                        }
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 412
        val height = 360
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/$fileName")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
