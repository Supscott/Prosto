package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomBarThemingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTabs = listOf(
        Tab(id = "tab1", url = "https://example.com", title = "Example Domain", isActive = true),
        Tab(id = "tab2", url = "https://kotlinlang.org", title = "Kotlin", isActive = false),
    )

    @Test
    fun unifiedBottomBar_rendersWithWorkspaceTheming_nodesExist() {
        val workspace = Workspace(
            id = "w_amber",
            name = "Work Amber",
            color = 0xFFF59E0B.toInt(),
            position = 1,
        )
        val state = BrowserUiState(
            workspaces = listOf(workspace),
            activeWorkspaceId = "w_amber",
            tabs = sampleTabs,
            activeTabId = "tab1",
            currentUrl = "https://example.com",
            pageTitle = "Example Domain",
        )

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    UnifiedBottomBar(
                        uiState = state,
                        onEvent = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("dock_capsule").assertExists()
        composeRule.onNodeWithTag("tab_tab1").assertExists()
    }

    @Test
    fun renderBottomBar_withDifferentWorkspaceColors_previews() {
        val colorsToTest = listOf(
            "blue" to 0xFF2196F3.toInt(),
            "amber" to 0xFFF59E0B.toInt(),
            "purple" to 0xFF9C27B0.toInt(),
            "emerald" to 0xFF10B981.toInt(),
        )

        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        for ((name, colorVal) in colorsToTest) {
            val workspace = Workspace(
                id = "w_$name",
                name = "Space $name",
                color = colorVal,
            )
            val state = BrowserUiState(
                workspaces = listOf(workspace),
                activeWorkspaceId = "w_$name",
                tabs = sampleTabs,
                activeTabId = "tab1",
                currentUrl = "https://example.com",
                pageTitle = "Example Domain",
            )

            // Render Light mode
            val composeViewLight = ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .prostoSkyBackground(isDark = false),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            UnifiedBottomBar(
                                uiState = state,
                                onEvent = {}
                            )
                        }
                    }
                }
            }

            activity.setContentView(composeViewLight)
            val width = 440
            val height = 180
            composeViewLight.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            composeViewLight.layout(0, 0, width, height)

            val bitmapLight = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            composeViewLight.draw(android.graphics.Canvas(bitmapLight))

            val fileLight = File("build/bottom_bar_workspace_${name}_light.png")
            fileLight.parentFile?.mkdirs()
            FileOutputStream(fileLight).use { out ->
                bitmapLight.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            assertThat(fileLight.exists()).isTrue()
            assertThat(fileLight.length()).isGreaterThan(0L)

            // Render Dark mode
            val composeViewDark = ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .prostoSkyBackground(isDark = true),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            UnifiedBottomBar(
                                uiState = state,
                                onEvent = {}
                            )
                        }
                    }
                }
            }

            activity.setContentView(composeViewDark)
            composeViewDark.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            composeViewDark.layout(0, 0, width, height)

            val bitmapDark = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            composeViewDark.draw(android.graphics.Canvas(bitmapDark))

            val fileDark = File("build/bottom_bar_workspace_${name}_dark.png")
            FileOutputStream(fileDark).use { out ->
                bitmapDark.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            assertThat(fileDark.exists()).isTrue()
            assertThat(fileDark.length()).isGreaterThan(0L)
        }
    }
}
