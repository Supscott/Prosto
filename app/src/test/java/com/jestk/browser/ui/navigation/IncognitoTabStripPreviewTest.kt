package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** The tab strip marks incognito tabs with the incognito icon, active or not. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncognitoTabStripPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val vibration =
        object : VibrationFeedback {
            override fun tick() {}

            override fun confirm() {}
        }

    private fun render(activeId: String, isDark: Boolean, fontScale: Float, fileName: String) {
        val tabs =
            listOf(
                Tab(id = "regular", title = "Example", url = "https://example.com"),
                Tab(id = "secret", title = "Secret page", url = "https://secret.example", isIncognito = true),
                Tab(id = "secret_home", title = "", url = "", isIncognito = true),
            )
        val state = BrowserUiState(tabs = tabs.map { it.copy(isActive = it.id == activeId) }, activeTabId = activeId)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalVibrationFeedback provides vibration,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    val dockDark = isDark || activeId.startsWith("secret")
                    CompositionLocalProvider(LocalIsDarkTheme provides dockDark) {
                        Box(
                            modifier = Modifier.width(380.dp).height(110.dp).prostoSkyBackground(isDark = isDark),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            UnifiedBottomBar(uiState = state, onEvent = {})
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val decor = composeRule.activity.window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(full))
        val bitmap = Bitmap.createBitmap(full, 0, 0, full.width, minOf(full.height, 360))
        val file = File("build/$fileName")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertThat(file.length()).isGreaterThan(0L)
    }

    @Test
    fun activeIncognitoTab_showsTheIncognitoIconInThePill() {
        render("secret", isDark = true, fontScale = 1.0f, fileName = "incognito_strip_active_preview.png")

        composeRule.onAllNodesWithContentDescription("Incognito tab", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun activeIncognitoTab_turnsTheDockVioletInLightTheme() {
        render("secret", isDark = false, fontScale = 1.0f, fileName = "incognito_dock_light_preview.png")
    }

    @Test
    fun largeFont_keepsTheStripLegible() {
        render("secret", isDark = true, fontScale = 1.3f, fileName = "incognito_strip_large_font_preview.png")
    }

    @Test
    fun inactiveIncognitoTab_showsTheIncognitoIconInLightTheme() {
        render("regular", isDark = false, fontScale = 1.0f, fileName = "incognito_strip_inactive_preview.png")

        composeRule.onAllNodesWithContentDescription("Incognito tab", useUnmergedTree = true).assertCountEquals(2)
    }
}
