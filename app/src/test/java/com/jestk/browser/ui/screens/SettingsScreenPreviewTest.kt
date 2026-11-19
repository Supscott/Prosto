package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.ThemeMode
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
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state() = BrowserUiState(activeScreen = ScreenType.SETTINGS, isDefaultBrowser = false)

    private fun show(
        category: SettingsCategory?,
        onEvent: (BrowserUiEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(uiState = state(), onEvent = onEvent, initialCategory = category)
            }
        }
    }

    @Test
    fun settingsRoot_listsAllCategories() {
        show(null)

        composeRule.onNodeWithText("General").assertExists()
        composeRule.onNodeWithText("Search").assertExists()
        composeRule.onNodeWithText("Protection").assertExists()
        composeRule.onNodeWithText("About Prosto").assertExists()
        composeRule.onNodeWithText("Ad blocking, cookies, tracking").assertExists()
    }

    @Test
    fun settingsRoot_opensCategoryOnClick() {
        show(null)

        composeRule.onNodeWithText("General").performClick()
        composeRule.onNodeWithText("Vibration").assertExists()
        composeRule.onNodeWithText("Appearance").assertExists()
    }

    @Test
    fun settingsRoot_quickTilesEmitToggleEvents() {
        val events = mutableListOf<BrowserUiEvent>()
        show(null) { events += it }

        composeRule.onNodeWithText("Ad Blocker").performClick()
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Clean YouTube Player").performClick()

        assertThat(events).contains(BrowserUiEvent.SetAdBlockerEnabled(!state().settings.isAdBlockerEnabled))
        assertThat(events).contains(BrowserUiEvent.SetThemeMode(state().settings.themeMode.next()))
        assertThat(events).contains(BrowserUiEvent.SetCleanYouTubePlayerEnabled(!state().settings.isCleanYouTubePlayerEnabled))
    }

    @Test
    fun settingsRoot_feedbackButtonOpensFeedbackSheet() {
        var capturedEvent: BrowserUiEvent? = null
        show(null) { capturedEvent = it }

        composeRule.onNodeWithText("Send feedback").performScrollTo().performClick()
        assertThat(capturedEvent).isEqualTo(BrowserUiEvent.OpenFeedbackSheet)
    }

    @Test
    fun settingsRoot_backDismissesOverlay() {
        var capturedEvent: BrowserUiEvent? = null
        show(null) { capturedEvent = it }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertThat(capturedEvent).isEqualTo(BrowserUiEvent.DismissOverlay)
    }

    @Test
    fun settingsCategory_backReturnsToRoot() {
        var capturedEvent: BrowserUiEvent? = null
        show(SettingsCategory.GENERAL) { capturedEvent = it }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Ad blocking, cookies, tracking").assertExists()
        assertThat(capturedEvent).isNull()
    }

    @Test
    fun generalCategory_showsVibrationAndGrayTabsToggles() {
        show(SettingsCategory.GENERAL)

        composeRule.onNodeWithText("Vibration").assertExists()
        composeRule.onNodeWithText("Gray unloaded tabs").assertExists()
        composeRule.onNodeWithText("Show inactive tabs not loaded in RAM in gray").assertExists()
    }

    @Test
    fun protectionCategory_showsTrackingAndAutoConsentToggles() {
        show(SettingsCategory.PROTECTION)

        composeRule.onNodeWithText("Tracking Protection").assertExists()
        composeRule.onNodeWithText("Auto-dismiss cookie banners").assertExists()
        composeRule.onNodeWithText("Automatically reject tracking cookies and dismiss consent popups").assertExists()
    }

    @Test
    fun aboutCategory_showsFeedbackOptionAndTriggersEvent() {
        var capturedEvent: BrowserUiEvent? = null
        show(SettingsCategory.ABOUT) { capturedEvent = it }

        composeRule.onNodeWithText("Telegram chat and GitHub bug reports").assertExists()
        composeRule.onNodeWithText("Feedback & Community").performScrollTo().performClick()
        assertThat(capturedEvent).isEqualTo(BrowserUiEvent.OpenFeedbackSheet)
    }

    @Test
    fun renderSettingsScreenLightPreview() = render("settings_screen_light_preview", ThemeMode.LIGHT, null, false)

    @Test
    fun renderSettingsScreenDarkPreview() = render("settings_screen_dark_preview", ThemeMode.DARK, null, true)

    @Test
    fun renderSettingsGeneralLightPreview() = render("settings_general_light_preview", ThemeMode.LIGHT, SettingsCategory.GENERAL, false)

    @Test
    fun renderSettingsGeneralDarkPreview() = render("settings_general_dark_preview", ThemeMode.DARK, SettingsCategory.GENERAL, true)

    @Test
    fun renderSettingsSearchLightPreview() = render("settings_search_light_preview", ThemeMode.LIGHT, SettingsCategory.SEARCH, false)

    @Test
    fun renderSettingsSearchDarkPreview() = render("settings_search_dark_preview", ThemeMode.DARK, SettingsCategory.SEARCH, true)

    @Test
    fun renderSettingsProtectionLightPreview() =
        render("settings_protection_light_preview", ThemeMode.LIGHT, SettingsCategory.PROTECTION, false)

    @Test
    fun renderSettingsProtectionDarkPreview() =
        render("settings_protection_dark_preview", ThemeMode.DARK, SettingsCategory.PROTECTION, true)

    @Test
    fun renderSettingsAboutLightPreview() = render("settings_about_light_preview", ThemeMode.LIGHT, SettingsCategory.ABOUT, false)

    @Test
    fun renderSettingsAboutDarkPreview() = render("settings_about_dark_preview", ThemeMode.DARK, SettingsCategory.ABOUT, true)

    private fun render(
        name: String,
        mode: ThemeMode,
        category: SettingsCategory?,
        isDefaultBrowser: Boolean,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val uiState = BrowserUiState(activeScreen = ScreenType.SETTINGS, isDefaultBrowser = isDefaultBrowser)

        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = mode) {
                        SettingsScreen(uiState = uiState, onEvent = {}, initialCategory = category)
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

        val outputFile = File("build/$name.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
