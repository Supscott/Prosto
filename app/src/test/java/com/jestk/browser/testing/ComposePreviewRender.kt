package com.jestk.browser.testing

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/**
 * Renders [content] in a fresh Robolectric activity at [width] x [height] px and writes
 * `build/<fileName>` (relative to the module, which is where `tools/preview.sh` lists PNGs).
 * Needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)` on the test class.
 *
 * - [coil]: pass the class's [CoilPreviewRule] when the content loads images (engine badges, favicons),
 *   so the draw waits for the Coil decodes.
 * - [fontScale]: wraps the content in a [Density] with this font scale (the screen density is kept).
 * - [settleMainLooper]: sleeps 200 ms and drains the main looper before measuring.
 *
 * Returns the written file.
 */
fun renderComposePreview(
    fileName: String,
    width: Int,
    height: Int,
    coil: CoilPreviewRule? = null,
    fontScale: Float? = null,
    settleMainLooper: Boolean = false,
    content: @Composable () -> Unit,
): File {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val composeView = ComposeView(activity).apply {
        setContent {
            if (fontScale == null) {
                content()
            } else {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) { content() }
            }
        }
    }
    activity.setContentView(composeView)
    if (settleMainLooper) {
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()
    }
    composeView.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    composeView.layout(0, 0, width, height)

    coil?.awaitIdle(composeView, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    composeView.draw(android.graphics.Canvas(bitmap))

    val outputFile = File("build/$fileName")
    outputFile.parentFile?.mkdirs()
    FileOutputStream(outputFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    assertTrue("$fileName was not written", outputFile.length() > 0L)
    return outputFile
}
