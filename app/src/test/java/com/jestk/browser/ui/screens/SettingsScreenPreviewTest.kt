package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
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
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * The GENERAL section (vibration toggle and open in apps) and all 5 categories must be reachable.
     */
    @Test
    fun settingsScreen_showsVibrationToggleAndCategories() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    uiState = BrowserUiState(activeScreen = ScreenType.SETTINGS, isDefaultBrowser = false),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Vibration").assertExists()
        composeRule.onNodeWithText("Tracking Protection").assertExists()
        composeRule.onNodeWithText("GENERAL").assertExists()
        composeRule.onNodeWithText("APPEARANCE").assertExists()
        composeRule.onNodeWithText("SEARCH").assertExists()
        composeRule.onNodeWithText("PRIVACY & PROTECTION").assertExists()
        composeRule.onNodeWithText("ABOUT & PROSTO").assertExists()
    }

    @Test
    fun settingsScreen_showsGrayUnloadedTabsToggle() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    uiState = BrowserUiState(activeScreen = ScreenType.SETTINGS, isDefaultBrowser = false),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Gray unloaded tabs").assertExists()
        composeRule.onNodeWithText("Show inactive tabs not loaded in RAM in gray").assertExists()
    }

    @Test
    fun renderSettingsScreenLightPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state =
            BrowserUiState(
                activeScreen = ScreenType.SETTINGS,
                isDefaultBrowser = false,
            )

        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        SettingsScreen(
                            uiState = state,
                            onEvent = {},
                        )
                    }
                }
            }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/settings_screen_light_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderSettingsScreenDarkPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state =
            BrowserUiState(
                activeScreen = ScreenType.SETTINGS,
                isDefaultBrowser = true,
            )

        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                        SettingsScreen(
                            uiState = state,
                            onEvent = {},
                        )
                    }
                }
            }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/settings_screen_dark_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
