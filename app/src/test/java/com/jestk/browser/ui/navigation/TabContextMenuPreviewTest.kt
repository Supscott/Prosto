package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** PRO-194 / PRO-197: the tab long-tap menu with its icon row and Site / Organize / Close groups. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabContextMenuPreviewTest {
    private val folders = listOf(TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt()))
    private val workspaces = listOf(Workspace(id = "w", name = "Work", color = 0xFFF59E0B.toInt()))

    private enum class Kind { WEB, WEB_FAVORITE_IN_FOLDER, HOME }

    @Composable
    private fun Frame(
        mode: ThemeMode,
        kind: Kind,
        fontScale: Float = 1f,
    ) {
        val base = LocalDensity.current
        val dark = mode == ThemeMode.DARK
        val web = kind != Kind.HOME
        val inFolder = kind == Kind.WEB_FAVORITE_IN_FOLDER
        JestkBrowserTheme(themeMode = mode) {
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(
                    modifier =
                        Modifier
                            .width(250.dp)
                            .height(420.dp)
                            .background(if (dark) Color(0xFF0E1513) else Color(0xFFE9F6F2))
                            .padding(10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    TabContextMenuCard(
                        onDismissRequest = {},
                        isDark = dark,
                        onCloseTab = {},
                        onCopyUrl = {},
                        isFavorite = inFolder,
                        onToggleFavorite = if (web) ({}) else null,
                        isSiteAllowlisted = inFolder,
                        onToggleBlocking = if (web) ({}) else null,
                        onShareLink = {},
                        onReload = if (web) ({}) else null,
                        folderId = if (inFolder) "f1" else null,
                        availableFolders = if (inFolder) emptyList() else folders,
                        onCreateFolder = if (inFolder) null else ({}),
                        onAddToFolder = if (inFolder) null else ({}),
                        onRemoveFromFolder = if (inFolder) ({}) else null,
                        availableWorkspaces = workspaces,
                        onMoveToWorkspace = {},
                    )
                }
            }
        }
    }

    private fun render(
        fileName: String,
        frames: @Composable () -> Unit,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView =
            ComposeView(activity).apply {
                setContent { Row(modifier = Modifier.fillMaxSize()) { frames() } }
            }
        activity.setContentView(composeView)

        val width = 1500
        val height = 840
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
    }

    @Test
    fun renderTabContextMenuPreview() =
        render("tab_context_menu_preview.png") {
            Frame(ThemeMode.LIGHT, Kind.WEB)
            Frame(ThemeMode.DARK, Kind.WEB_FAVORITE_IN_FOLDER)
            Frame(ThemeMode.LIGHT, Kind.HOME)
        }

    @Test
    fun renderTabContextMenuLargeFontPreview() =
        render("tab_context_menu_large_font_preview.png") {
            Frame(ThemeMode.DARK, Kind.WEB, fontScale = 1.3f)
            Frame(ThemeMode.LIGHT, Kind.WEB_FAVORITE_IN_FOLDER, fontScale = 1.3f)
            Frame(ThemeMode.DARK, Kind.HOME, fontScale = 1.3f)
        }
}
