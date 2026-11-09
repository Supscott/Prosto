package com.jestk.browser.ui.views

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
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
class HomeScreenWorkspaceTitleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatchedEvents = mutableListOf<BrowserUiEvent>()

    @Test
    fun homeScreen_displaysActiveWorkspaceName_whenActiveWorkspacePresent() {
        val testWorkspace = Workspace(
            id = "w_custom",
            name = "Design Studio",
            color = 0xFF9C27B0.toInt(),
            position = 0,
        )
        val state = BrowserUiState(
            workspaces = listOf(testWorkspace),
            activeWorkspaceId = "w_custom",
        )

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    uiState = state,
                    onEvent = { dispatchedEvents.add(it) },
                )
            }
        }

        composeRule.onNodeWithText("Design Studio").assertExists()
        composeRule.onNodeWithText("Where to next?").assertDoesNotExist()
    }

    @Test
    fun homeScreen_clickingWorkspaceTitle_dispatchesOpenWorkspacesOverview() {
        val testWorkspace = Workspace(
            id = "w_personal",
            name = "Personal",
            color = 0xFF2196F3.toInt(),
            position = 0,
        )
        val state = BrowserUiState(
            workspaces = listOf(testWorkspace),
            activeWorkspaceId = "w_personal",
        )

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    uiState = state,
                    onEvent = { dispatchedEvents.add(it) },
                )
            }
        }

        composeRule.onNodeWithTag("workspace_title").performClick()
        assertThat(dispatchedEvents).contains(BrowserUiEvent.OpenWorkspacesOverview)
    }

    @Test
    fun homeScreen_displaysHomeGreeting_whenActiveWorkspaceIsNull() {
        val state = BrowserUiState(
            workspaces = emptyList(),
            activeWorkspaceId = "",
        )

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    uiState = state,
                    onEvent = { dispatchedEvents.add(it) },
                )
            }
        }

        composeRule.onNodeWithText("Where to next?").assertExists()
    }

    @Test
    fun renderHomeScreen_withWorkspaceTheming_previews() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val workspace = Workspace(
            id = "work",
            name = "Work Context",
            color = 0xFFF59E0B.toInt(), // Amber
            position = 1,
        )
        val state = BrowserUiState(
            workspaces = listOf(workspace),
            activeWorkspaceId = "work",
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    HomeScreen(
                        uiState = state,
                        onEvent = {}
                    )
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/home_screen_workspace_theming_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
