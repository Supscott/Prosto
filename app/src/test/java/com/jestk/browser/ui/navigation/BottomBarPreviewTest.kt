package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.google.common.truth.Truth.assertThat
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
class BottomBarPreviewTest {

    @Composable
    private fun MockWebPageContent() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("with the official Wikipedia app.", color = Color(0xFF54595D), fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1B1C),
                    modifier = Modifier.size(width = 140.dp, height = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("GET IT ON Google Play", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1B1C),
                    modifier = Modifier.size(width = 140.dp, height = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Download on App Store", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            Text("Wikipedia is hosted by the Wikimedia Foundation.", color = Color(0xFF72777D), fontSize = 12.sp)
        }
    }

    @Test
    fun renderSimulatedScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            currentUrl = "https://wikipedia.org",
            pageTitle = "Wikipedia",
            tabs = listOf(
                Tab(id = "1", url = "https://wikipedia.org", title = "Wikipedia", isActive = true),
                Tab(id = "2", url = "https://youtube.com", title = "YouTube", isActive = false)
            ),
            activeTabId = "1"
        )

        val bottomBgDark = Color(0xFF161314)

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    // Simulated device screen in Dark Theme with webpage behind it
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1B1C))
                    ) {
                        // Webpage behind
                        MockWebPageContent()

                        // Actual UnifiedBottomBar
                        UnifiedBottomBar(
                            uiState = sampleState,
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Android system gesture bar indicator simulation at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/bottom_bar_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderLightDockPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val figmaLightState = BrowserUiState(
            currentUrl = "https://figma.com",
            pageTitle = "Figma — Palette Presentation",
            tabs = listOf(
                Tab(id = "1", url = "https://figma.com", title = "Figma — Palette Presentation", isActive = true),
                Tab(id = "2", url = "https://github.com", title = "GitHub", isActive = false)
            ),
            activeTabId = "1"
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8ECEF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 86.dp)
                        ) {
                            MockWebPageContent()
                        }

                        UnifiedBottomBar(
                            uiState = figmaLightState,
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/dock_light_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderAppleDarkPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val appleState = BrowserUiState(
            currentUrl = "https://apple.com",
            pageTitle = "Apple",
            activeThemeColor = android.graphics.Color.parseColor("#161617"),
            tabs = listOf(
                Tab(
                    id = "apple_tab",
                    url = "https://apple.com",
                    title = "Apple",
                    themeColor = android.graphics.Color.parseColor("#161617"),
                    isActive = true
                )
            ),
            activeTabId = "apple_tab"
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF161617))
                    ) {
                        // Simulated dark Apple webpage content at bottom
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF161617))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("iPhone Duo", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("The largest iPhone display ever.", color = Color(0xFFA1A1A6), fontSize = 14.sp)
                        }

                        UnifiedBottomBar(
                            uiState = appleState,
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Gesture navigation indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/apple_bottom_bar_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderManyTabsPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val manyTabsState = BrowserUiState(
            currentUrl = "https://github.com",
            pageTitle = "GitHub",
            tabs = listOf(
                Tab(id = "1", url = "https://wikipedia.org", title = "Wikipedia", isActive = false),
                Tab(id = "2", url = "https://youtube.com", title = "YouTube", isActive = false),
                Tab(id = "3", url = "https://github.com", title = "GitHub", isActive = true),
                Tab(id = "4", url = "https://reddit.com", title = "Reddit", isActive = false),
                Tab(id = "5", url = "https://twitter.com", title = "Twitter", isActive = false),
                Tab(id = "6", url = "https://news.ycombinator.com", title = "Hacker News", isActive = false)
            ),
            activeTabId = "3"
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1B1C))
                    ) {
                        MockWebPageContent()

                        UnifiedBottomBar(
                            uiState = manyTabsState,
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/many_tabs_bottom_bar_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun renderFloatingDockHiddenAndVisibleComparison() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val baseState = BrowserUiState(
            currentUrl = "https://wikipedia.org",
            pageTitle = "Wikipedia",
            tabs = listOf(
                Tab(id = "1", url = "https://wikipedia.org", title = "Wikipedia", isActive = true)
            ),
            activeTabId = "1"
        )

val width = 440
        val height = 956

        // 1. Render Visible State: bottom bar visible, webview padded by 86dp (62dp + 24dp nav)
        val visibleView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8ECEF)) // animatedSiteBg underlay
                    ) {
                        // Webview container with bottom padding
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 86.dp)
                        ) {
                            MockWebPageContent()
                        }

                        // Floating dock visible
                        UnifiedBottomBar(
                            uiState = baseState.copy(isBottomBarVisible = true),
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        activity.setContentView(visibleView)
        visibleView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        visibleView.layout(0, 0, width, height)
        val visibleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        visibleView.draw(android.graphics.Canvas(visibleBitmap))

        val visibleFile = File("build/dock_visible_preview.png")
        visibleFile.parentFile?.mkdirs()
        FileOutputStream(visibleFile).use { out ->
            visibleBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // 2. Render Hidden State: bottom bar hidden, webview fills space down to safe area bottom (24dp nav padding)
        val hiddenView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8ECEF))
                    ) {
                        // Webview container fills space with safearea bottom
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp)
                        ) {
                            MockWebPageContent()
                        }

                        // Simulated gesture navigation indicator in safe area
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        activity.setContentView(hiddenView)
        hiddenView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        hiddenView.layout(0, 0, width, height)
        val hiddenBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        hiddenView.draw(android.graphics.Canvas(hiddenBitmap))

        val hiddenFile = File("build/dock_hidden_preview.png")
        hiddenFile.parentFile?.mkdirs()
        FileOutputStream(hiddenFile).use { out ->
            hiddenBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Assert that the dock area (height - 50) differs between visible dock (dock capsule + underlay) and hidden dock (expanded webpage)
        val checkY = height - 50 // In the 62dp space above safearea bottom
        var hasPixelDiff = false
        for (x in 200..800 step 20) {
            if (visibleBitmap.getPixel(x, checkY) != hiddenBitmap.getPixel(x, checkY)) {
                hasPixelDiff = true
                break
            }
        }
        assertThat(hasPixelDiff).isTrue()
    }

    @Test
    fun renderBottomBarWithSearchEngineSwitcher() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            currentUrl = "https://www.google.com/search?q=apple",
            pageTitle = "apple - Google Search",
            tabs = listOf(
                Tab(id = "1", url = "https://www.google.com/search?q=apple", title = "apple - Google Search", isActive = true),
                Tab(id = "2", url = "https://duckduckgo.com/?q=apple", title = "apple at DuckDuckGo", isActive = false)
            ),
            activeTabId = "1"
        )

        assertThat(sampleState.searchPageMatch).isNotNull()
        assertThat(sampleState.searchPageMatch?.first).isEqualTo(com.jestk.browser.domain.model.SearchEngine.GOOGLE)
        assertThat(sampleState.searchPageMatch?.second).isEqualTo("apple")

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        MockWebPageContent()
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
        val height = 2340
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        composeView.draw(canvas)

        val outputFile = File("build/dock_search_switcher_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
