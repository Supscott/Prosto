package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.testing.CoilPreviewRule
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Draws the link and image context menus through the real [FloatingContextMenuContainer] (scrim,
 * anchor positioning, card) with the enter animation off (PRO-177). Without `animateIn = false` the
 * container starts at alpha 0 and a one-frame capture shows only the scrim.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContextMenuContainerPreviewTest {
    @get:Rule
    val coil = CoilPreviewRule()

    private val frameWidth = 360
    private val frameHeight = 380
    private val anchor = Offset(180f, 40f)

    @Composable
    private fun Frame(
        mode: ThemeMode,
        menu: @Composable () -> Unit,
    ) {
        val dark = mode == ThemeMode.DARK
        Box(modifier = Modifier.width(frameWidth.dp).height(frameHeight.dp)) {
            JestkBrowserTheme(themeMode = mode) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (dark) Color(0xFF0E1513) else Color(0xFFE9F6F2)),
                ) {
                    menu()
                }
            }
        }
    }

    @Composable
    private fun LinkMenu() {
        LinkContextMenuModal(
            url = "https://example.com/articles/prosto-canvas-design",
            onEvent = {},
            anchor = anchor,
            animateIn = false,
        )
    }

    @Composable
    private fun ImageMenu() {
        ImageContextMenuModal(
            imageUrl = "https://images.example.com/photo-1579783902614.jpg?auto=format&w=800",
            onEvent = {},
            anchor = anchor,
            animateIn = false,
        )
    }

    @Test
    fun renderLinkAndImageMenusThroughContainer() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Row {
                            Frame(ThemeMode.LIGHT) { LinkMenu() }
                            Frame(ThemeMode.DARK) { LinkMenu() }
                        }
                        Row {
                            Frame(ThemeMode.LIGHT) { ImageMenu() }
                            Frame(ThemeMode.DARK) { ImageMenu() }
                        }
                    }
                }
            }
        activity.setContentView(composeView)

        val width = frameWidth * 2 + 8
        val height = frameHeight * 2 + 8
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        coil.awaitIdle(composeView, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        // The light link card starts 14 dp under the anchor (y 54) with 12 dp of white padding: a
        // scrim-only frame would be grey here.
        val pixel = bitmap.getPixel(4 + 180, 4 + 58)
        val red = android.graphics.Color.red(pixel)
        assertTrue("link card is not drawn at its anchor, pixel=#${Integer.toHexString(pixel)}", red >= 0xF0)

        val outputFile = File("build/context_menu_container_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
