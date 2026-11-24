package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A 9:20 screen is taller than the 9:16 grid card, so the preview must crop the bottom, not the top. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePreviewAlignmentTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tallScreen_keepsTheHomeHeaderInsideTheSlot() {
        rule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.size(120.dp, 175.dp).testTag("slot")) {
                    HomePreview(uiState = BrowserUiState(), workspaceId = BrowserUiState().activeWorkspaceId)
                }
            }
        }
        rule.waitForIdle()

        val slot = rule.onNodeWithTag("slot").getBoundsInRoot()
        val title = rule.onNodeWithTag("workspace_title", useUnmergedTree = true).getBoundsInRoot()
        // Centred, the header sits above the slot and is clipped away entirely (empty bounds).
        assertThat(title.height.value).isGreaterThan(0f)
        assertThat(title.top.value).isAtLeast(slot.top.value)
        assertThat(title.top.value).isLessThan(slot.bottom.value)
    }
}
