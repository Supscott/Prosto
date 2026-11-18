package com.jestk.browser.ui.screens

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ProtectionLevel
import com.jestk.browser.domain.model.SiteAllowlistEntry
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.engine.adblock.ContentBlockingStatus
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.RemoveSiteAllowlistEntry
import com.jestk.browser.ui.viewmodel.SetProtectionLevel
import com.jestk.browser.ui.viewmodel.UpdateFilterListsNow
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
class SettingsContentBlockingPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val workspace = Workspace(id = "w-work", name = "Work", color = 0)
    private val unnamed = Workspace.DEFAULT_WORK

    private val sampleState = BrowserUiState(
        workspaces = listOf(Workspace.DEFAULT_PERSONAL, workspace, unnamed),
        contentBlocking = ContentBlockingStatus(
            level = ProtectionLevel.STRICT,
            lastUpdatedAt = 1_760_000_000_000L,
            ruleCount = 187_342,
            isUpdating = true,
            cosmeticSupported = false,
        ),
        siteAllowlist = listOf(
            SiteAllowlistEntry("example.com", null, 1L),
            SiteAllowlistEntry("news.example.org", workspace.id, 2L),
            SiteAllowlistEntry("shop.example.net", unnamed.id, 3L),
        ),
    )

    private fun show(state: BrowserUiState, onEvent: (BrowserUiEvent) -> Unit = {}) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.padding(16.dp)) {
                    SettingsContentBlockingSection(uiState = state, onEvent = onEvent, isDark = false)
                }
            }
        }
    }

    @Test
    fun section_showsStrictDownloadNoticeNetworkOnlyNoticeAndAllowlistScopes() {
        show(sampleState)

        composeRule.onNodeWithText("Content blocking").assertExists()
        composeRule.onNodeWithText("Strict lists are downloading. Standard protection is active until they finish.").assertExists()
        composeRule.onNodeWithText("Your WebView supports network blocking only.", substring = true).assertExists()
        composeRule.onNodeWithText("Rules: 187,342", substring = true).assertExists()
        composeRule.onNodeWithText("example.com").assertExists()
        composeRule.onNodeWithText("All workspaces").assertExists()
        composeRule.onNodeWithText("Work").assertExists()
        composeRule.onNodeWithText("Workspace 3").assertExists()
    }

    @Test
    fun section_hidesNoticesOnStandardWithCosmeticSupport() {
        show(BrowserUiState())

        composeRule.onNodeWithText("Standard").assertExists()
        composeRule.onNodeWithText("Strict lists are downloading.", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Your WebView supports network blocking only.", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Blocking is on everywhere.", substring = true).assertExists()
    }

    @Test
    fun failedStrictDownloadOffersRetry() {
        var event: BrowserUiEvent? = null
        show(
            BrowserUiState(contentBlocking = ContentBlockingStatus(level = ProtectionLevel.STRICT, hasError = true)),
        ) { event = it }

        composeRule.onNodeWithText("Could not download the Strict lists.", substring = true).assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertThat(event).isEqualTo(UpdateFilterListsNow)
    }

    @Test
    fun levelSelectorAndRemoveAndUpdateNowEmitEvents() {
        val events = mutableListOf<BrowserUiEvent>()
        show(sampleState.copy(contentBlocking = ContentBlockingStatus(lastUpdatedAt = 1L))) { events += it }

        composeRule.onNodeWithText("Strict").performClick()
        composeRule.onNodeWithText("Update filter lists now").performClick()
        composeRule.onNodeWithContentDescription("Turn blocking back on for news.example.org").performClick()

        assertThat(events).containsExactly(
            SetProtectionLevel(ProtectionLevel.STRICT),
            UpdateFilterListsNow,
            RemoveSiteAllowlistEntry("news.example.org", workspace.id),
        ).inOrder()
    }

    @Test
    fun licencesRowExpandsToTheAttributions() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Column { LicensesRows(isDark = false, context = androidx.compose.ui.platform.LocalContext.current) }
            }
        }

        composeRule.onNodeWithText("MPL-2.0").assertDoesNotExist()
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.onNodeWithText("adblock-rust 0.13.5").assertExists()
        composeRule.onNodeWithText("MPL-2.0").assertExists()
        composeRule.onNodeWithText("uBlock Origin resources").assertExists()
    }

    @Test
    fun renderContentBlockingSectionLightPreview() = render(ThemeMode.LIGHT, "settings_content_blocking_light_preview.png")

    @Test
    fun renderContentBlockingSectionDarkPreview() = render(ThemeMode.DARK, "settings_content_blocking_dark_preview.png")

    private fun render(mode: ThemeMode, fileName: String) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val isDark = mode == ThemeMode.DARK
        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = mode) {
                    Column(
                        Modifier
                            .background(if (isDark) DarkBackground else ProstoCanvasFloor)
                            .padding(20.dp),
                    ) {
                        SettingsContentBlockingSection(uiState = sampleState, onEvent = {}, isDark = isDark)
                    }
                }
            }
        }
        activity.setContentView(composeView)
        val width = 440
        val height = 1000
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))
        val outputFile = File("build/$fileName")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
