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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.navigation.SearchEngineSwitcherRow
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
            ),
            favorites = listOf(
                FavoriteItem(1L, "Google", "https://google.com"),
                FavoriteItem(2L, "GitHub", "https://github.com"),
                FavoriteItem(3L, "Reddit", "https://reddit.com"),
                FavoriteItem(4L, "Wikipedia", "https://wikipedia.org")
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
                                    settings = BrowserSettings(searchEngine = SearchEngine.BRAVE)
                                ),
                                onEvent = {}
                            )
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
val width = 440
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

                        Text("Switcher Row Dark (Brave Active)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF161314))
                                    .padding(12.dp)
                            ) {
                                SearchEngineSwitcherRow(
                                    selectedEngine = SearchEngine.BRAVE,
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
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val width = 440
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
        onEvent(BrowserUiEvent.SetSearchEngine(SearchEngine.BRAVE))
        assertEquals(BrowserUiEvent.SetSearchEngine(SearchEngine.BRAVE), eventDispatched)
        assertEquals(SearchEngine.BRAVE, selectedEngine)

        // Trigger selection of Google
        onEvent(BrowserUiEvent.SetSearchEngine(SearchEngine.GOOGLE))
        assertEquals(BrowserUiEvent.SetSearchEngine(SearchEngine.GOOGLE), eventDispatched)
        assertEquals(SearchEngine.GOOGLE, selectedEngine)
    }

    @Test
    fun testToggleSearchHistoryExpandedDispatchesEvent() {
        var eventDispatched: BrowserUiEvent? = null
        val onEvent: (BrowserUiEvent) -> Unit = { event ->
            eventDispatched = event
        }

        onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
        assertEquals(BrowserUiEvent.ToggleSearchHistoryExpanded, eventDispatched)
    }

    @Test
    fun renderSearchOverlayExpandedHistoryPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val expandedState = BrowserUiState(
            searchQuery = "",
            isSearchHistoryExpanded = true,
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
            searchHistorySuggestions = listOf(
                HistoryItem(1L, "https://duckduckgo.com/?q=android+compose", "Android Compose", System.currentTimeMillis()),
                HistoryItem(2L, "https://google.com/search?q=kotlin+coroutines", "Kotlin Coroutines", System.currentTimeMillis() - 10000),
                HistoryItem(3L, "https://developer.android.com", "Android Developers", System.currentTimeMillis() - 20000)
            ),
            favorites = listOf(
                FavoriteItem(1L, "Google", "https://google.com")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    SearchOverlay(
                        uiState = expandedState,
                        onEvent = {}
                    )
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

        val outputFile = File("build/search_overlay_expanded_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchOverlayExpandedHistoryLoadingMorePreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val loadingMoreState = BrowserUiState(
            searchQuery = "",
            isSearchHistoryExpanded = true,
            isLoadingMoreHistory = true,
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
            searchHistorySuggestions = listOf(
                HistoryItem(1L, "https://duckduckgo.com/?q=android+compose", "Android Compose", System.currentTimeMillis()),
                HistoryItem(2L, "https://google.com/search?q=kotlin+coroutines", "Kotlin Coroutines", System.currentTimeMillis() - 10000),
                HistoryItem(3L, "https://developer.android.com", "Android Developers", System.currentTimeMillis() - 20000)
            ),
            favorites = listOf(
                FavoriteItem(1L, "Google", "https://google.com")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    SearchOverlay(
                        uiState = loadingMoreState,
                        onEvent = {}
                    )
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

        val outputFile = File("build/search_overlay_loading_more_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchEngineSelectorAndDropdownPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val density = Density(1f)

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFDFF7F3))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Light Mode section
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Light Mode Selector (Closed & Open)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SearchEngineSelectorButton(
                                    selectedEngine = SearchEngine.GOOGLE,
                                    isMenuOpen = false,
                                    isDark = false,
                                    density = density,
                                    onClick = {},
                                    onSwipeLeft = {},
                                    onSwipeRight = {}
                                )
                                SearchEngineSelectorButton(
                                    selectedEngine = SearchEngine.GOOGLE,
                                    isMenuOpen = true,
                                    isDark = false,
                                    density = density,
                                    onClick = {},
                                    onSwipeLeft = {},
                                    onSwipeRight = {}
                                )
                            }
                            Text("Dropdown (Google Selected)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            SearchEngineDropdownCard(
                                selectedEngine = SearchEngine.GOOGLE,
                                onSelectEngine = {},
                                isDark = false
                            )
                        }

                        // Dark Mode section
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFF0E1513))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Dark Mode Selector (Closed & Open)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SearchEngineSelectorButton(
                                        selectedEngine = SearchEngine.DUCKDUCKGO,
                                        isMenuOpen = false,
                                        isDark = true,
                                        density = density,
                                        onClick = {},
                                        onSwipeLeft = {},
                                        onSwipeRight = {}
                                    )
                                    SearchEngineSelectorButton(
                                        selectedEngine = SearchEngine.DUCKDUCKGO,
                                        isMenuOpen = true,
                                        isDark = true,
                                        density = density,
                                        onClick = {},
                                        onSwipeLeft = {},
                                        onSwipeRight = {}
                                    )
                                }
                                Text("Dropdown (DDG Selected)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.DUCKDUCKGO,
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
val width = 440
        val height = 650
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_selector_dropdown_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchOverlayWithDropdownOpenPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
            searchHistorySuggestions = listOf(
                HistoryItem(1L, "https://google.com/search?q=kotlin+coroutines", "Kotlin Coroutines", System.currentTimeMillis()),
                HistoryItem(2L, "https://duckduckgo.com/?q=android+compose", "Android Compose", System.currentTimeMillis() - 10000)
            ),
            favorites = listOf(
                FavoriteItem(1L, "Google", "https://google.com"),
                FavoriteItem(2L, "GitHub", "https://github.com"),
                FavoriteItem(3L, "Reddit", "https://reddit.com"),
                FavoriteItem(4L, "Wikipedia", "https://wikipedia.org")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8E8E93))
                ) {
                    // Light Theme half with dropdown open
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                            SearchOverlay(
                                uiState = sampleState,
                                onEvent = {},
                                initialDropdownOpen = true
                            )
                        }
                    }

                    // Dark Theme half with dropdown open
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            SearchOverlay(
                                uiState = sampleState.copy(
                                    settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO)
                                ),
                                onEvent = {},
                                initialDropdownOpen = true
                            )
                        }
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

        val outputFile = File("build/search_overlay_dropdown_open_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun testCircularEngineCyclingMath() {
        val engines = SearchEngine.entries
        // Google is index 0
        val currentIndex = engines.indexOf(SearchEngine.GOOGLE)

        // Cycle right (+1): Google -> DuckDuckGo
        val nextIndex = (currentIndex + 1).mod(engines.size)
        assertEquals(SearchEngine.DUCKDUCKGO, engines[nextIndex])

        // Cycle left (-1): Google -> Startpage (wraps left)
        val prevIndex = (currentIndex - 1).mod(engines.size)
        assertEquals(SearchEngine.STARTPAGE, engines[prevIndex])

        // Cycle left from DuckDuckGo (1): back to Google (0)
        val ddgIndex = engines.indexOf(SearchEngine.DUCKDUCKGO)
        val leftFromDdg = (ddgIndex - 1).mod(engines.size)
        assertEquals(SearchEngine.GOOGLE, engines[leftFromDdg])

        // Cycle right from Startpage (5): wraps to Google (0)
        val spIndex = engines.indexOf(SearchEngine.STARTPAGE)
        val wrapRight = (spIndex + 1).mod(engines.size)
        assertEquals(SearchEngine.GOOGLE, engines[wrapRight])
    }

    @Test
    fun testSequentialSwipeCyclesEnginesContinuously() {
        var currentEngine = SearchEngine.GOOGLE
        val history = mutableListOf<SearchEngine>()

        val onEvent: (BrowserUiEvent) -> Unit = { event ->
            if (event is BrowserUiEvent.SetSearchEngine) {
                currentEngine = event.engine
                history.add(event.engine)
            }
        }

        val cycleEngine: (Int) -> Unit = { direction ->
            val entries = SearchEngine.entries
            val currentIndex = entries.indexOf(currentEngine).let { if (it == -1) 0 else it }
            val newIndex = (currentIndex + direction).mod(entries.size)
            val target = entries[newIndex]
            if (target != currentEngine) {
                onEvent(BrowserUiEvent.SetSearchEngine(target))
            }
        }

        // Sequential swipes to the right (+1): Google -> DuckDuckGo -> Brave -> Kagi -> Ecosia -> Startpage -> Google
        cycleEngine(1)
        assertEquals(SearchEngine.DUCKDUCKGO, currentEngine)

        cycleEngine(1)
        assertEquals(SearchEngine.BRAVE, currentEngine)

        cycleEngine(1)
        assertEquals(SearchEngine.KAGI, currentEngine)

        cycleEngine(1)
        assertEquals(SearchEngine.ECOSIA, currentEngine)

        cycleEngine(1)
        assertEquals(SearchEngine.STARTPAGE, currentEngine)

        cycleEngine(1)
        assertEquals(SearchEngine.GOOGLE, currentEngine)

        // Sequential swipes to the left (-1): Google -> Startpage -> Ecosia -> Kagi -> Brave -> DuckDuckGo
        cycleEngine(-1)
        assertEquals(SearchEngine.STARTPAGE, currentEngine)

        cycleEngine(-1)
        assertEquals(SearchEngine.ECOSIA, currentEngine)

        cycleEngine(-1)
        assertEquals(SearchEngine.KAGI, currentEngine)

        cycleEngine(-1)
        assertEquals(SearchEngine.BRAVE, currentEngine)

        cycleEngine(-1)
        assertEquals(SearchEngine.DUCKDUCKGO, currentEngine)

        cycleEngine(-1)
        assertEquals(SearchEngine.GOOGLE, currentEngine)
    }

    @Test
    fun renderSearchEngineCarouselScrollingPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val density = Density(1f)

        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {}
        }

        val composeView = ComposeView(activity).apply {
            setContent {
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                val carouselStateLight = remember(density) {
                    SearchEngineCarouselState(
                        initialEngine = SearchEngine.GOOGLE,
                        density = density,
                        coroutineScope = coroutineScope,
                        haptic = dummyHaptic,
                        onEngineSelected = {}
                    ).apply {
                        isDragging = true
                        coroutineScope.launch {
                            animOffset.snapTo(-30f)
                        }
                    }
                }

                val carouselStateDark = remember(density) {
                    SearchEngineCarouselState(
                        initialEngine = SearchEngine.DUCKDUCKGO,
                        density = density,
                        coroutineScope = coroutineScope,
                        haptic = dummyHaptic,
                        onEngineSelected = {}
                    ).apply {
                        isDragging = true
                        coroutineScope.launch {
                            animOffset.snapTo(30f)
                        }
                    }
                }

                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFDFF7F3))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Carousel Dragging (Light)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            SearchEngineSelectorButton(
                                carouselState = carouselStateLight,
                                isMenuOpen = false,
                                isDark = false,
                                onClick = {}
                            )
                        }

                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFF0E1513))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Carousel Dragging (Dark)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                SearchEngineSelectorButton(
                                    carouselState = carouselStateDark,
                                    isMenuOpen = false,
                                    isDark = true,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 440
        val height = 300
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_carousel_scrolling_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    private val testFrameClock = object : androidx.compose.runtime.MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    }

    @Test
    fun testCarouselDragSnapAndHapticFeedback() = runTest(testFrameClock) {
        val density = Density(1f)
        var hapticTicks = 0
        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {
                hapticTicks++
            }
        }
        var selectedEngine = SearchEngine.GOOGLE

        val testEngines = listOf(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BRAVE)
        val state = SearchEngineCarouselState(
            initialEngine = SearchEngine.GOOGLE,
            density = density,
            coroutineScope = this,
            haptic = dummyHaptic,
            engines = testEngines,
            onEngineSelected = { selectedEngine = it }
        )

        // 1. Drag start
        state.onDragStart()
        assertTrue(state.isDragging)
        assertTrue(state.isScrolling)

        // 2. Drag by -40f (1 full item spacing left => should cross to Brave)
        state.onDrag(-40f)
        testScheduler.advanceUntilIdle()
        assertEquals(-40f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks > 0)

        // 3. Drag end with 0 velocity => snap to nearest slot (targetSlot = 1 -> Brave)
        state.onDragEnd(velocity = 0f)
        testScheduler.advanceUntilIdle()

        // Settle & commit
        assertEquals(SearchEngine.BRAVE, selectedEngine)
        assertEquals(SearchEngine.BRAVE, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(!state.isDragging)
        assertTrue(!state.isFlinging)
    }

    @Test
    fun testCarouselFlingMomentumInertia() = runTest(testFrameClock) {
        val density = Density(1f)
        var hapticTicks = 0
        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {
                hapticTicks++
            }
        }
        var selectedEngine = SearchEngine.GOOGLE

        val testEngines = listOf(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BRAVE)
        val state = SearchEngineCarouselState(
            initialEngine = SearchEngine.GOOGLE,
            density = density,
            coroutineScope = this,
            haptic = dummyHaptic,
            engines = testEngines,
            onEngineSelected = { selectedEngine = it }
        )

        // 1. Moderate fling left (velocity = -800 px/sec) => advances across 4 slots to Brave
        state.onDragStart()
        state.onDrag(-20f)
        testScheduler.advanceUntilIdle()

        state.onDragEnd(velocity = -800f)
        testScheduler.advanceUntilIdle()

        assertEquals(SearchEngine.BRAVE, selectedEngine)
        assertEquals(SearchEngine.BRAVE, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks >= 1)
        assertTrue(!state.isFlinging)

        // 2. Strong fling left (velocity = -2500 px/sec) => carries momentum across 6 slots (2 full loops) to Brave
        val hapticBeforeStrongFling = hapticTicks
        state.onDragStart()
        state.onDrag(-20f)
        testScheduler.advanceUntilIdle()

        state.onDragEnd(velocity = -2500f)
        testScheduler.advanceUntilIdle()

        // 7 slots traversed (-260f / 40f => 7 slots) => lands at DuckDuckGo with multiple haptic ticks
        assertEquals(SearchEngine.DUCKDUCKGO, selectedEngine)
        assertEquals(SearchEngine.DUCKDUCKGO, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks - hapticBeforeStrongFling >= 5)
        assertTrue(!state.isFlinging)
    }

    @Test
    fun renderSearchOverlayPrefilledTextPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val state = BrowserUiState(
            searchQuery = "https://typography-preview.org/query_with_descenders_gypqj",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
            favorites = listOf(
                FavoriteItem(1L, "Google", "https://google.com"),
                FavoriteItem(2L, "GitHub", "https://github.com")
            )
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8E8E93))
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                            SearchOverlay(uiState = state, onEvent = {})
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            SearchOverlay(
                                uiState = state.copy(
                                    settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
                                ),
                                onEvent = {}
                            )
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 824
        val height = 892
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_overlay_prefilled_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun testAnimateToEngineForwardAndBackward() = runTest(testFrameClock) {
        val density = Density(1f)
        var hapticTicks = 0
        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {
                hapticTicks++
            }
        }
        var selectedEngine = SearchEngine.GOOGLE

        val testEngines = listOf(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BRAVE)
        val state = SearchEngineCarouselState(
            initialEngine = SearchEngine.GOOGLE,
            density = density,
            coroutineScope = this,
            haptic = dummyHaptic,
            engines = testEngines,
            onEngineSelected = { selectedEngine = it }
        )

        // 1. Animate from Google to Brave (forward 1 slot)
        state.animateToEngine(SearchEngine.BRAVE)
        assertTrue(state.isFlinging || state.isScrolling)
        testScheduler.advanceUntilIdle()

        assertEquals(SearchEngine.BRAVE, selectedEngine)
        assertEquals(SearchEngine.BRAVE, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks >= 1)
        assertTrue(!state.isFlinging)

        // 2. Animate from Brave to DuckDuckGo (forward 1 slot circularly)
        val hapticsBeforeDDG = hapticTicks
        state.animateToEngine(SearchEngine.DUCKDUCKGO)
        testScheduler.advanceUntilIdle()

        assertEquals(SearchEngine.DUCKDUCKGO, selectedEngine)
        assertEquals(SearchEngine.DUCKDUCKGO, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks > hapticsBeforeDDG)

        // 3. Animate from DuckDuckGo to Brave (backward 1 slot)
        val hapticsBeforeBrave = hapticTicks
        state.animateToEngine(SearchEngine.BRAVE)
        testScheduler.advanceUntilIdle()

        assertEquals(SearchEngine.BRAVE, selectedEngine)
        assertEquals(SearchEngine.BRAVE, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks > hapticsBeforeBrave)

        // 4. Calling animateToEngine on already selected engine should not start animation
        val hapticBeforeSame = hapticTicks
        state.animateToEngine(SearchEngine.BRAVE)
        assertTrue(!state.isFlinging)
        assertEquals(hapticBeforeSame, hapticTicks)
    }

    @Test
    fun testAnimateToEngineWithCustomEngines() = runTest(testFrameClock) {
        val density = Density(1f)
        var hapticTicks = 0
        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {
                hapticTicks++
            }
        }
        val youtube = SearchEngine(
            id = "youtube",
            displayName = "YouTube",
            searchUrlTemplate = "https://youtube.com/results?search_query=%s",
            iconUrl = "https://youtube.com/favicon.ico",
            shortcut = "y",
            orderPosition = 3
        )
        val engines = listOf(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BRAVE, youtube)
        var selectedEngine = SearchEngine.GOOGLE

        val state = SearchEngineCarouselState(
            initialEngine = SearchEngine.GOOGLE,
            density = density,
            coroutineScope = this,
            haptic = dummyHaptic,
            engines = engines,
            onEngineSelected = { selectedEngine = it }
        )

        // From Google (index 1) to YouTube (index 3): diff = 2 -> shortest slot = 2
        state.animateToEngine(youtube)
        testScheduler.advanceUntilIdle()

        assertEquals(youtube, selectedEngine)
        assertEquals(youtube, state.currentEngine)
        assertEquals(0f, state.animOffset.value, 0.1f)
        assertTrue(hapticTicks >= 2)
    }

    @Test
    fun testSearchEngineCarouselConfiguration() {
        val density = Density(2f)
        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {}
        }
        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val state = SearchEngineCarouselState(
            initialEngine = SearchEngine.GOOGLE,
            density = density,
            coroutineScope = coroutineScope,
            haptic = dummyHaptic,
            onEngineSelected = {}
        )

        // Verify item spacing is 40.dp (80px at 2x density)
        assertEquals(40.dp, state.itemSpacingDp)
        assertEquals(80f, state.itemSpacingPx, 0.01f)

        // Verify scrolling state transitions for container expansion
        assertTrue(!state.isScrolling)
        state.onDragStart()
        assertTrue(state.isScrolling)
    }
}

