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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
                                onRedirect = {},
                                onDismiss = {},
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
    fun availableState_clickingActions_triggersCallbacks() {
        var redirectClicked = false
        var dismissClicked = false
        var skipClicked = false

        val state =
            AppUpdateUiState.Available(
                AppUpdate(
                    versionName = "0.5.0",
                    releaseNotes = "Fixes",
                    releaseUrl = "https://example.com/release",
                ),
            )

        composeRule.setContent {
            JestkBrowserTheme {
                UpdateNotificationCard(
                    state = state,
                    onRedirect = { redirectClicked = true },
                    onDismiss = { dismissClicked = true },
                    onSkip = { skipClicked = true },
                    isDark = false,
                )
            }
        }

        composeRule.onNodeWithText("Update").assertIsDisplayed().performClick()
        assertThat(redirectClicked).isTrue()

        composeRule.onNodeWithText("Later").assertIsDisplayed().performClick()
        assertThat(dismissClicked).isTrue()

        composeRule.onNodeWithText("Skip").assertIsDisplayed().performClick()
        assertThat(skipClicked).isTrue()
    }
}
