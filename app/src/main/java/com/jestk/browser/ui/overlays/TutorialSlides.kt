package com.jestk.browser.ui.overlays

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jestk.browser.R

/**
 * One story of the tour: a title and one or two sentences under either an animated [scene]
 * (drawn from the story progress 0..1) or, without one, a big glyph on an accent tint.
 */
internal data class TutorialSlide(
    val icon: ImageVector,
    val accent: Color,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val durationMs: Int = 7000,
    val scene: (@Composable (progress: Float, isDark: Boolean) -> Unit)? = null,
)

internal val TutorialSlides: List<TutorialSlide> =
    listOf(
        TutorialSlide(Icons.Default.Tab, Color(0xFF14B8A6), R.string.tutorial_dock_title, R.string.tutorial_dock_body, durationMs = DOCK_SCENE_DURATION_MS) { p, dark ->
            TutorialDockScene(p, dark)
        },
        TutorialSlide(Icons.Default.Folder, Color(0xFFF59E0B), R.string.tutorial_folders_title, R.string.tutorial_folders_body, durationMs = FOLDER_SCENE_DURATION_MS) { p, dark ->
            TutorialFolderScene(p, dark)
        },
        TutorialSlide(
            Icons.Default.Workspaces,
            Color(0xFF6366F1),
            R.string.tutorial_workspaces_title,
            R.string.tutorial_workspaces_body,
            durationMs = WORKSPACE_SCENE_DURATION_MS,
        ) { p, dark ->
            TutorialWorkspaceScene(p, dark)
        },
        TutorialSlide(
            Icons.Default.TravelExplore,
            Color(0xFF0EA5E9),
            R.string.tutorial_search_title,
            R.string.tutorial_search_body,
            durationMs = SEARCH_SCENE_DURATION_MS,
        ) { p, dark ->
            TutorialSearchScene(p, dark)
        },
        TutorialSlide(
            Icons.Default.Shield,
            Color(0xFF22C55E),
            R.string.tutorial_protection_title,
            R.string.tutorial_protection_body,
            durationMs = PROTECTION_SCENE_DURATION_MS,
        ) { p, dark ->
            TutorialProtectionScene(p, dark)
        },
        TutorialSlide(
            Icons.Default.Public,
            Color(0xFFEC4899),
            R.string.tutorial_default_title,
            R.string.tutorial_default_body,
            durationMs = DEFAULT_SCENE_DURATION_MS,
        ) { p, dark ->
            TutorialDefaultBrowserScene(p, dark)
        },
    )
