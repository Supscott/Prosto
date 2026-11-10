package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the workspace switcher mid-transition and at rest with a stand-in for the live app,
 * and checks that at rest the live content sits exactly on the active card slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceSwitcherTransitionPreviewTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tabs = listOf(
        Tab(id = "p1", url = "https://personal.com", title = "Personal", workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true),
        Tab(id = "w1", url = "https://work.com", title = "Work", workspaceId = Workspace.DEFAULT_WORK_ID, isActive = true),
    )

    @Test
    fun liveContentShrinksOntoActiveCardSlot() {
        // A held drag keeps the overview from auto-animating, so frames can be set by hand.
        val switcher = WorkspaceSwitcherState(flingVelocityPxPerSec = 900f).apply { startDrag() }
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().onSizeChanged { switcher.rootSize = it }) {
                    Box(Modifier.fillMaxSize().background(ProstoCanvasFloor))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .workspaceSwitcherLiveContent(switcher, cornerRadiusPx = 72f, shadowElevationPx = 24f)
                            .background(Brush.verticalGradient(listOf(Color(0xFF2E7DD7), Color(0xFF6CC7B0)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("LIVE PAGE", color = Color.White, fontSize = 40.sp)
                    }
                    WorkspacesOverviewScreen(
                        uiState = BrowserUiState(
                            workspaces = listOf(Workspace.DEFAULT_PERSONAL, Workspace.DEFAULT_WORK),
                            activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                            tabs = tabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                            allTabs = tabs,
                            activeTabId = "p1",
                        ),
                        onEvent = {},
                        switcher = switcher,
                    )
                }
            }
        }

        for (progress in listOf(0.5f, 1f)) {
            runBlocking { switcher.progress.snapTo(progress) }
            composeRule.waitForIdle()
            save("workspace_switcher_${(progress * 100).toInt()}_preview.png")
        }

        val rest = switcher.restCardRect
        assertThat(rest).isNotNull()
        assertRectNear(switcher.liveRect, rest!!)
    }

    private fun assertRectNear(actual: Rect, expected: Rect) {
        assertThat(actual.left).isWithin(1f).of(expected.left)
        assertThat(actual.top).isWithin(1f).of(expected.top)
        assertThat(actual.width).isWithin(1f).of(expected.width)
        assertThat(actual.height).isWithin(2f).of(expected.height)
    }

    private fun save(name: String) {
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/$name")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
