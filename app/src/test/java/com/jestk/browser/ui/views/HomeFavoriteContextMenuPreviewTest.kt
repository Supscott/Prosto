package com.jestk.browser.ui.views

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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoShapes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFavoriteContextMenuPreviewTest {
    private val workspaces =
        listOf(
            Workspace(id = "w1", name = "Work", color = 0xFF2F80ED.toInt()),
            Workspace(
                id = "w2",
                name = "A very long workspace name that must not push the row wider",
                color = 0xFFEB5757.toInt(),
            ),
        )

    @Composable
    private fun Frame(
        mode: ThemeMode,
        picker: Boolean,
        fontScale: Float = 1f,
    ) {
        val base = LocalDensity.current
        val dark = mode == ThemeMode.DARK
        Box(modifier = Modifier.width(400.dp).height(300.dp)) {
            JestkBrowserTheme(themeMode = mode) {
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(if (dark) Color(0xFF0E1513) else Color(0xFFE9F6F2))
                                .padding(16.dp),
                    ) {
                        // The container animates in from alpha 0, so the card is drawn directly
                        // (like the link and image menu previews); positioning is covered by
                        // FloatingContextMenuContainerTest.
                        Surface(
                            shape = ProstoShapes.Card,
                            color = if (dark) DarkSurface else Color.White,
                            border = ProstoBorders.floatingBorder(dark),
                            shadowElevation = 8.dp,
                            modifier = Modifier.width(300.dp),
                        ) {
                            HomeFavoriteContextMenuContent(
                                availableWorkspaces = workspaces,
                                canMove = true,
                                canDelete = true,
                                onCopyUrl = {},
                                onMoveToWorkspace = {},
                                onDelete = {},
                                initialWorkspacePicker = picker,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun renderFavoriteContextMenuPreview() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Row {
                            Frame(ThemeMode.LIGHT, picker = false)
                            Frame(ThemeMode.DARK, picker = false)
                        }
                        Row {
                            Frame(ThemeMode.LIGHT, picker = true)
                            Frame(ThemeMode.DARK, picker = true, fontScale = 1.3f)
                        }
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 808
        val height = 608
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/home_favorite_context_menu_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
