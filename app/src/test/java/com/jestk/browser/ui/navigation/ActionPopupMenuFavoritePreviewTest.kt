package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.app.FavoriteNoticePill
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionPopupMenuFavoritePreviewTest {
    private val page = "https://example.com/a"

    private fun state() =
        BrowserUiState(
            currentUrl = page,
            pageTitle = "Example",
            tabs = listOf(Tab(id = "t", url = page, title = "Example", isActive = true)),
            activeTabId = "t",
        )

    @Composable
    private fun Frame(
        mode: ThemeMode,
        fontScale: Float = 1f,
        frameWidth: Dp = 230.dp,
    ) {
        val base = LocalDensity.current
        val dark = mode == ThemeMode.DARK
        JestkBrowserTheme(themeMode = mode) {
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(
                    modifier =
                        Modifier
                            .width(frameWidth)
                            .height(300.dp)
                            .background(if (dark) Color(0xFF0E1513) else Color(0xFFE9F6F2)),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPopupMenu(expanded = true, uiState = state(), isDark = dark, onEvent = {})
                    }
                }
            }
        }
    }

    @Composable
    private fun PillFrame(
        mode: ThemeMode,
        added: Boolean,
        fontScale: Float = 1f,
    ) {
        val base = LocalDensity.current
        val dark = mode == ThemeMode.DARK
        JestkBrowserTheme(themeMode = mode) {
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(
                    modifier =
                        Modifier
                            .width(230.dp)
                            .height(64.dp)
                            .background(if (dark) Color(0xFF0E1513) else Color(0xFFE9F6F2))
                            .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FavoriteNoticePill(added = added, isDark = dark, accent = null, swipeKey = added, onDismiss = {})
                }
            }
        }
    }

    @Test
    fun renderFavoriteMenuItemPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        Row {
                            Frame(ThemeMode.LIGHT)
                            Frame(ThemeMode.DARK)
                            Frame(ThemeMode.LIGHT, fontScale = 1.3f)
                            Frame(ThemeMode.DARK, fontScale = 1.3f)
                        }
                        Row {
                            PillFrame(ThemeMode.LIGHT, added = true)
                            PillFrame(ThemeMode.LIGHT, added = false)
                            PillFrame(ThemeMode.DARK, added = true)
                            PillFrame(ThemeMode.DARK, added = false, fontScale = 1.3f)
                        }
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 928
        val height = 600
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/favorite_menu_item_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** PRO-187: the menu follows its widest label (172..240dp), so nothing ends in an ellipsis. */
    @Test
    @Config(qualifiers = "ru")
    fun renderMenuWidthRussianLargeFontPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        Frame(ThemeMode.LIGHT, fontScale = 1.3f, frameWidth = 270.dp)
                        Frame(ThemeMode.DARK, fontScale = 1.3f, frameWidth = 270.dp)
                        Frame(ThemeMode.LIGHT, fontScale = 1f, frameWidth = 270.dp)
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 810
        val height = 320
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/action_menu_width_ru_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
