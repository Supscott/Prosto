package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.domain.model.AppUpdateUiState
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
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
class UpdateNotificationCardTest {
    @get:Rule
    val composeRule = createComposeRule()
    private fun render(
        state: AppUpdateUiState,
        isDark: Boolean,
        name: String,
    ): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) Color(0xFF0E1513) else Color(0xFFE9F6F2)),
                        ) {
                            UpdateNotificationCard(
                                state = state,
                                onInstall = {},
                                onInstallLater = {},
                                onSkip = {},
                                isDark = isDark,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }

        activity.setContentView(composeView)
        val width = 412
        val height = 220
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/update_card_$name.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return bitmap
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                if (bitmap.getPixel(x, y) != 0) return false
            }
        }
        return true
    }

    @Test
    fun rendersAvailableUpdateWithActions() {
        val state =
            AppUpdateUiState.Available(
                AppUpdate(
                    versionName = "1.2.0",
                    releaseNotes = "Bug fixes",
                    apkUrl = "https://example.com/prosto.apk",
                    releaseUrl = "https://example.com/release",
                ),
            )

        val bitmap = render(state, isDark = false, name = "available_light")

        assertThat(isBlank(bitmap)).isFalse()
        assertThat(File("build/update_card_available_light.png").length()).isGreaterThan(0L)

        val darkBitmap = render(state, isDark = true, name = "available_dark")
        assertThat(isBlank(darkBitmap)).isFalse()
        assertThat(File("build/update_card_available_dark.png").length()).isGreaterThan(0L)
    }

    @Test
    fun rendersReadyUpdateInDarkTheme() {
        val bitmap =
            render(
                AppUpdateUiState.ReadyToInstall("1.2.0"),
                isDark = true,
                name = "ready_dark",
            )

        assertThat(isBlank(bitmap)).isFalse()
        assertThat(File("build/update_card_ready_dark.png").length()).isGreaterThan(0L)

        val lightBitmap =
            render(
                AppUpdateUiState.ReadyToInstall("1.2.0"),
                isDark = false,
                name = "ready_light",
            )
        assertThat(isBlank(lightBitmap)).isFalse()
        assertThat(File("build/update_card_ready_light.png").length()).isGreaterThan(0L)
    }

    @Test
    fun rendersDownloadingProgress() {
        val bitmap =
            render(
                AppUpdateUiState.Downloading("1.2.0", 42),
                isDark = false,
                name = "downloading_light",
            )

        assertThat(isBlank(bitmap)).isFalse()
    }

    @Test
    fun availableState_clickingDownloadAndSkip_triggersCallbacks() {
        var downloadClicked = false
        var skipClicked = false

        val state = AppUpdateUiState.Available(
            AppUpdate(
                versionName = "0.5.0",
                releaseNotes = "Fixes",
                apkUrl = "https://example.com/apk",
                releaseUrl = "https://example.com/release",
            )
        )

        composeRule.setContent {
            JestkBrowserTheme {
                UpdateNotificationCard(
                    state = state,
                    onDownload = { downloadClicked = true },
                    onInstall = {},
                    onInstallLater = {},
                    onSkip = { skipClicked = true },
                    isDark = false,
                )
            }
        }

        composeRule.onNodeWithText("Download").assertIsDisplayed().performClick()
        assertThat(downloadClicked).isTrue()

        composeRule.onNodeWithText("Skip").assertIsDisplayed().performClick()
        assertThat(skipClicked).isTrue()
    }

    @Test
    fun readyToInstallState_clickingRestartInstall_triggersInstallCallback() {
        var installClicked = false

        composeRule.setContent {
            JestkBrowserTheme {
                UpdateNotificationCard(
                    state = AppUpdateUiState.ReadyToInstall("0.5.0"),
                    onInstall = { installClicked = true },
                    onInstallLater = {},
                    onSkip = {},
                    isDark = false,
                )
            }
        }

        composeRule.onNodeWithText("Restart / Install").assertIsDisplayed().performClick()
        assertThat(installClicked).isTrue()
    }
}
