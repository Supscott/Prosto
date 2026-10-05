package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockRefinementsTest {

    @Test
    fun renderActiveTabPillWithLoadingProgress() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .height(60.dp)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        ActiveTabPill(
                            title = "Wikipedia, the free encyclopedia",
                            domain = "wikipedia.org",
                            url = "https://wikipedia.org",
                            faviconUrl = null,
                            isHome = false,
                            backgroundColor = Color.White,
                            isDark = false,
                            isLoading = true,
                            loadingProgress = 0.65f,
                            onOpenSearch = {},
                            onClose = {},
                            onAddFavorite = { _, _ -> },
                            onShare = {},
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 600
        val height = 120
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/dock_active_tab_loading_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }

    @Test
    fun renderDockWithCompactPaddingAndCapsuleHeight() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            currentUrl = "https://example.com",
            pageTitle = "Example Domain",
            isLoading = true,
            loadingProgress = 0.45f,
            tabs = listOf(
                Tab(id = "1", url = "https://example.com", title = "Example Domain", isActive = true),
                Tab(id = "2", url = "https://github.com", title = "GitHub", isActive = false),
                Tab(id = "3", url = "https://news.ycombinator.com", title = "Hacker News", isActive = false)
            ),
            activeTabId = "1"
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        UnifiedBottomBar(
                            uiState = sampleState,
                            onEvent = {}
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 200
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/dock_compact_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }

    @Test
    fun testCenteringScrollOffsetCalculation() {
        // When active tab width is 50% of viewport width
        val viewportWidthPx = 1000f
        val activeTabWidthPx = 500f

        val centeringOffsetPx = ((viewportWidthPx - activeTabWidthPx) / 2f).coerceAtLeast(0f)
        val scrollOffset = -centeringOffsetPx.toInt()

        // Negative scroll offset scrolls list less, centering the active tab at 250px from start
        assertThat(centeringOffsetPx).isEqualTo(250f)
        assertThat(scrollOffset).isEqualTo(-250)

        // When item width equals viewport width
        val fullWidthItemPx = 1000f
        val fullCenteringOffset = ((viewportWidthPx - fullWidthItemPx) / 2f).coerceAtLeast(0f)
        assertThat(fullCenteringOffset).isEqualTo(0f)
        assertThat(-fullCenteringOffset.toInt()).isEqualTo(0)
    }

    @Test
    fun testTabReorderSwapThresholdLogic() {
        val tabs = mutableListOf(
            Tab(id = "1", url = "https://one.com", title = "One", isActive = true),
            Tab(id = "2", url = "https://two.com", title = "Two", isActive = false),
            Tab(id = "3", url = "https://three.com", title = "Three", isActive = false)
        )

        val swapThreshold = 100f
        var dragOffsetPx = 120f
        var currentIndex = 0

        // Dragging tab "1" to the right past threshold
        if (dragOffsetPx > swapThreshold && currentIndex < tabs.lastIndex) {
            val targetIndex = currentIndex + 1
            Collections.swap(tabs, currentIndex, targetIndex)
            dragOffsetPx -= swapThreshold
            currentIndex = targetIndex
        }

        assertThat(tabs[0].id).isEqualTo("2")
        assertThat(tabs[1].id).isEqualTo("1")
        assertThat(dragOffsetPx).isEqualTo(20f)

        // Dragging tab "1" back to the left past negative threshold
        dragOffsetPx = -110f
        if (dragOffsetPx < -swapThreshold && currentIndex > 0) {
            val targetIndex = currentIndex - 1
            Collections.swap(tabs, currentIndex, targetIndex)
            dragOffsetPx += swapThreshold
            currentIndex = targetIndex
        }

        assertThat(tabs[0].id).isEqualTo("1")
        assertThat(tabs[1].id).isEqualTo("2")
        assertThat(dragOffsetPx).isEqualTo(-10f)
    }
}
