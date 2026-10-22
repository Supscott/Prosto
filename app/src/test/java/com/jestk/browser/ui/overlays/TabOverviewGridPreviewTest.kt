package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
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
class TabOverviewGridPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tabs(count: Int): List<Tab> =
        (1..count).map { index ->
            Tab(
                id = "$index",
                url = "https://example.com/$index",
                title = "Tab $index",
                isActive = index == 1,
            )
        }

    private fun setGridContent(tabCount: Int) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState =
                        BrowserUiState(
                            tabs = tabs(tabCount),
                            activeTabId = "1",
                        ),
                    onEvent = {},
                )
            }
        }
    }

    /**
     * The grid is a 3-column layout: the first three tabs share one row and the fourth wraps
     * onto the next one. A 2-column grid would already have moved "Tab 3" down a row.
     */
    @Test
    fun grid_placesThreeTabsPerRow() {
        setGridContent(tabCount = 4)

        val tab1 = composeRule.onAllNodesWithText("Tab 1")[0].getBoundsInRoot()
        val tab2 = composeRule.onAllNodesWithText("Tab 2")[0].getBoundsInRoot()
        val tab3 = composeRule.onAllNodesWithText("Tab 3")[0].getBoundsInRoot()
        val tab4 = composeRule.onAllNodesWithText("Tab 4")[0].getBoundsInRoot()

        // First three tabs are on the same row.
        assertThat(tab1.top).isEqualTo(tab2.top)
        assertThat(tab2.top).isEqualTo(tab3.top)
        // ...and each one sits in its own column.
        assertThat(tab1.left).isLessThan(tab2.left)
        assertThat(tab2.left).isLessThan(tab3.left)
        // The fourth tab wraps onto the next row.
        assertThat(tab4.top).isGreaterThan(tab3.top)
    }

    @Test
    fun grid_rendersEveryTab() {
        setGridContent(tabCount = 3)

        composeRule.onNodeWithText("Tab 1").assertExists()
        composeRule.onNodeWithText("Tab 2").assertExists()
        composeRule.onNodeWithText("Tab 3").assertExists()
    }

    @Test
    fun renderTabOverviewGridLightPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView =
            ComposeView(activity).apply {
                setContent {
                    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                        TabOverviewGrid(
                            uiState =
                                BrowserUiState(
                                    tabs = tabs(4),
                                    activeTabId = "1",
                                ),
                            onEvent = {},
                        )
                    }
                }
            }
        activity.setContentView(composeView)

        val width = 412
        val height = 800
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/tab_overview_grid_light_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
