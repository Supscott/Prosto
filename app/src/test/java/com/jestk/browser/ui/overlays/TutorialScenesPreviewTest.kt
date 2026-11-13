package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the animated tutorial scenes as contact sheets: one scene at several story progress
 * values, top to bottom, so the whole motion can be reviewed in one PNG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TutorialScenesPreviewTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dockFrames = listOf(0f, 0.19f, 0.40f, 0.58f, 0.71f, 0.79f, 0.86f, 1f)
    private val workspaceFrames = listOf(0f, 0.18f, 0.34f, 0.47f, 0.60f, 0.70f, 0.76f, 1f)
    private val searchFrames = listOf(0f, 0.20f, 0.30f, 0.46f, 0.55f, 0.70f, 1f)
    private val protectionFrames = listOf(0f, 0.15f, 0.36f, 0.50f, 0.66f, 1f)
    private val defaultFrames = listOf(0f, 0.20f, 0.30f, 0.44f, 0.60f, 0.72f, 1f)
    private val folderFrames = listOf(0f, 0.13f, 0.20f, 0.30f, 0.43f, 0.50f, 0.68f, 1f)

    @Test
    fun dockSceneLight() = renderSheet("tutorial_dock_scene_light_preview.png", isDark = false, dockFrames) { p, dark ->
        TutorialDockScene(p, dark)
    }

    @Test
    fun dockSceneDark() = renderSheet("tutorial_dock_scene_dark_preview.png", isDark = true, dockFrames) { p, dark ->
        TutorialDockScene(p, dark)
    }

    @Test
    fun folderSceneLight() = renderSheet("tutorial_folder_scene_light_preview.png", isDark = false, folderFrames) { p, dark ->
        TutorialFolderScene(p, dark)
    }

    @Test
    fun folderSceneDark() = renderSheet("tutorial_folder_scene_dark_preview.png", isDark = true, folderFrames) { p, dark ->
        TutorialFolderScene(p, dark)
    }

    @Test
    fun workspaceSceneLight() = renderSheet("tutorial_workspace_scene_light_preview.png", isDark = false, workspaceFrames) { p, dark ->
        TutorialWorkspaceScene(p, dark)
    }

    @Test
    fun workspaceSceneDark() = renderSheet("tutorial_workspace_scene_dark_preview.png", isDark = true, workspaceFrames) { p, dark ->
        TutorialWorkspaceScene(p, dark)
    }

    @Test
    fun searchSceneLight() = renderSheet("tutorial_search_scene_light_preview.png", isDark = false, searchFrames) { p, dark ->
        TutorialSearchScene(p, dark)
    }

    @Test
    fun searchSceneDark() = renderSheet("tutorial_search_scene_dark_preview.png", isDark = true, searchFrames) { p, dark ->
        TutorialSearchScene(p, dark)
    }

    @Test
    fun protectionSceneLight() = renderSheet("tutorial_protection_scene_light_preview.png", isDark = false, protectionFrames) { p, dark ->
        TutorialProtectionScene(p, dark)
    }

    @Test
    fun protectionSceneDark() = renderSheet("tutorial_protection_scene_dark_preview.png", isDark = true, protectionFrames) { p, dark ->
        TutorialProtectionScene(p, dark)
    }

    @Test
    fun defaultSceneLight() = renderSheet("tutorial_default_scene_light_preview.png", isDark = false, defaultFrames) { p, dark ->
        TutorialDefaultBrowserScene(p, dark)
    }

    @Test
    fun defaultSceneDark() = renderSheet("tutorial_default_scene_dark_preview.png", isDark = true, defaultFrames) { p, dark ->
        TutorialDefaultBrowserScene(p, dark)
    }

    private fun renderSheet(
        name: String,
        isDark: Boolean,
        frames: List<Float>,
        scene: @Composable (Float, Boolean) -> Unit,
    ) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Column(
                    modifier = Modifier.fillMaxSize().prostoSkyBackground(isDark).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    frames.forEach { p -> scene(p, isDark) }
                }
            }
        }
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/$name")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
