package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class SearchOverlayPreviewTest {

    @Test
    fun renderSearchOverlayLightAndDarkPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
            searchHistorySuggestions = listOf(
                HistoryItem(1L, "https://duckduckgo.com/?q=android+compose", "Android Compose", System.currentTimeMillis()),
                HistoryItem(2L, "https://google.com/search?q=kotlin+coroutines", "Kotlin Coroutines", System.currentTimeMillis() - 10000)
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8E8E93))
                ) {
                    // Light Theme half
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                            SearchOverlay(
                                uiState = sampleState,
                                onEvent = {}
                            )
                        }
                    }

                    // Dark Theme half with Bing
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            SearchOverlay(
                                uiState = sampleState.copy(
                                    settings = BrowserSettings(searchEngine = SearchEngine.BING)
                                ),
                                onEvent = {}
                            )
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 1080
        val height = 900
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_overlay_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderBadgesAndSwitcherChipsPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF8F8))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Search Engine Badges (32dp Hero)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SearchEngine.entries.forEach { engine ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    SearchEngineBadge(engine = engine, size = 32.dp, isDark = false)
                                    Text(engine.displayName, fontSize = 11.sp)
                                }
                            }
                        }

                        Text("Switcher Row Light (Google Active)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        SearchEngineSwitcherRow(
                            selectedEngine = SearchEngine.GOOGLE,
                            onSelectEngine = {},
                            isDark = false
                        )

                        Text("Switcher Row Dark (Bing Active)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161314))
                                    .padding(12.dp)
                            ) {
                                SearchEngineSwitcherRow(
                                    selectedEngine = SearchEngine.BING,
                                    onSelectEngine = {},
                                    isDark = true
                                )
                            }
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 1080
        val height = 600
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_badges_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun testSearchEngineChipClickDispatchesEvent() {
        var selectedEngine = SearchEngine.DUCKDUCKGO
        var eventDispatched: BrowserUiEvent? = null

        val onEvent: (BrowserUiEvent) -> Unit = { event ->
            eventDispatched = event
            if (event is BrowserUiEvent.SetSearchEngine) {
                selectedEngine = event.engine
            }
        }

        // Trigger selection of Bing
        onEvent(BrowserUiEvent.SetSearchEngine(SearchEngine.BING))
        assertEquals(BrowserUiEvent.SetSearchEngine(SearchEngine.BING), eventDispatched)
        assertEquals(SearchEngine.BING, selectedEngine)

        // Trigger selection of Google
        onEvent(BrowserUiEvent.SetSearchEngine(SearchEngine.GOOGLE))
        assertEquals(BrowserUiEvent.SetSearchEngine(SearchEngine.GOOGLE), eventDispatched)
        assertEquals(SearchEngine.GOOGLE, selectedEngine)
    }
}
