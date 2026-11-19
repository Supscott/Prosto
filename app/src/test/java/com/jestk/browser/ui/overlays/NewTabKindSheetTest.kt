package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.navigation.AddTabDockButton
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.OpenIncognitoTab
import org.junit.After
import org.junit.Before
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
class NewTabKindSheetTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BrowserUiEvent>()
    private val originalSupport = isIncognitoSupported

    @Before
    fun setup() {
        events.clear()
        isIncognitoSupported = { true }
    }

    @After
    fun teardown() {
        isIncognitoSupported = originalSupport
    }

    private fun setPlusButton() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                NewTabKindHost(isDark = false, onEvent = { events += it }) { onLongClick ->
                    AddTabDockButton(
                        isDark = false,
                        onClick = { events += BrowserUiEvent.OpenNewTab("") },
                        onLongClick = onLongClick,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun plusButton() = composeTestRule.onNodeWithContentDescription("New Tab")

    @Test
    fun longPressOnPlus_opensTheSheetWithBothChoices() {
        setPlusButton()

        plusButton().performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_tab_kind_incognito").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_tab_kind_regular").assertIsDisplayed()
        assertThat(events).isEmpty()
    }

    @Test
    fun tapOnPlus_opensARegularTabWithoutTheSheet() {
        setPlusButton()

        plusButton().performClick()
        composeTestRule.waitForIdle()

        assertThat(events).containsExactly(BrowserUiEvent.OpenNewTab(""))
        composeTestRule.onAllNodesWithTagCount("new_tab_kind_sheet").let { assertThat(it).isEqualTo(0) }
    }

    @Test
    fun incognitoChoice_dispatchesOpenIncognitoTabAndClosesTheSheet() {
        setPlusButton()
        plusButton().performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_tab_kind_incognito").performClick()
        composeTestRule.waitForIdle()

        assertThat(events).containsExactly(OpenIncognitoTab)
        composeTestRule.onAllNodesWithTagCount("new_tab_kind_incognito").let { assertThat(it).isEqualTo(0) }
    }

    @Test
    fun regularChoice_dispatchesOpenNewTab() {
        setPlusButton()
        plusButton().performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_tab_kind_regular").performClick()
        composeTestRule.waitForIdle()

        assertThat(events).containsExactly(BrowserUiEvent.OpenNewTab(""))
    }

    @Test
    fun longPress_doesNothingWhenIncognitoIsUnsupported() {
        isIncognitoSupported = { false }
        setPlusButton()

        plusButton().performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // No sheet; the release still counts as a plain tap, as for any button.
        composeTestRule.onAllNodesWithTagCount("new_tab_kind_incognito").let { assertThat(it).isEqualTo(0) }
        assertThat(events.filter { it == OpenIncognitoTab }).isEmpty()
    }

    @Test
    fun renderSheetContentPreviews() {
        renderContent("new_tab_kind_sheet_light_preview.png", isDark = false, fontScale = 1.0f)
        renderContent("new_tab_kind_sheet_dark_large_font_preview.png", isDark = true, fontScale = 1.3f)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun renderContent(fileName: String, isDark: Boolean, fontScale: Float) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    JestkBrowserTheme(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        Box(modifier = Modifier.width(360.dp).background(if (isDark) DarkSurface else Color.White)) {
                            NewTabKindSheetContent(isDark = isDark, onIncognito = {}, onRegular = {})
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
        val width = 1080
        val height = 640
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
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
