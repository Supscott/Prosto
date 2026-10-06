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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import kotlinx.coroutines.launch
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
class SearchEngineCarouselTest {

    @Test
    fun testInterpolationAtExactCenterSlot() {
        val centerSlot = 0f
        val progress = 1f

        // Slot 0 (active center)
        val centerScale = SearchEngineCarouselInterpolation.calculateScale(0, centerSlot, progress)
        val centerAlpha = SearchEngineCarouselInterpolation.calculateAlpha(0, centerSlot, progress)
        val centerIndicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, centerSlot, progress)

        assertEquals(1.15f, centerScale, 0.001f)
        assertEquals(1.0f, centerAlpha, 0.001f)
        assertEquals(1.0f, centerIndicator, 0.001f)
    }

    @Test
    fun testAdjacentProvidersDimmedDuringScroll() {
        val centerSlot = 0f
        val progress = 1f

        // Adjacent slots (k = 1 and k = -1) at distance 1.0
        listOf(1, -1).forEach { k ->
            val scale = SearchEngineCarouselInterpolation.calculateScale(k, centerSlot, progress)
            val alpha = SearchEngineCarouselInterpolation.calculateAlpha(k, centerSlot, progress)
            val indicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(k, centerSlot, progress)

            assertEquals(1.0f, scale, 0.001f)
            assertEquals(0.45f, alpha, 0.001f)
            assertEquals(0.0f, indicator, 0.001f)
        }

        // Further slots (k = 2 and k = -2) at distance 2.0
        listOf(2, -2).forEach { k ->
            val scale = SearchEngineCarouselInterpolation.calculateScale(k, centerSlot, progress)
            val alpha = SearchEngineCarouselInterpolation.calculateAlpha(k, centerSlot, progress)
            val indicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(k, centerSlot, progress)

            assertEquals(1.0f, scale, 0.001f)
            assertEquals(0.45f, alpha, 0.001f)
            assertEquals(0.0f, indicator, 0.001f)
        }
    }

    @Test
    fun testSmoothDynamicInterpolationAsCarouselScrolls() {
        val progress = 1f

        // Dragged halfway between slot 0 and slot 1: centerSlotFloat = 0.5f
        val halfwaySlot = 0.5f

        val scale0 = SearchEngineCarouselInterpolation.calculateScale(0, halfwaySlot, progress)
        val scale1 = SearchEngineCarouselInterpolation.calculateScale(1, halfwaySlot, progress)
        val alpha0 = SearchEngineCarouselInterpolation.calculateAlpha(0, halfwaySlot, progress)
        val alpha1 = SearchEngineCarouselInterpolation.calculateAlpha(1, halfwaySlot, progress)

        // At distance 0.5: scale = 1.0 + 0.15 * 0.5 = 1.075f
        assertEquals(1.075f, scale0, 0.001f)
        assertEquals(1.075f, scale1, 0.001f)

        // At distance 0.5: alpha = 0.45 + 0.55 * 0.5 = 0.725f
        assertEquals(0.725f, alpha0, 0.001f)
        assertEquals(0.725f, alpha1, 0.001f)

        // Dragged a quarter towards slot 1: centerSlotFloat = 0.25f
        val quarterSlot = 0.25f

        val scaleQuarter0 = SearchEngineCarouselInterpolation.calculateScale(0, quarterSlot, progress)
        val scaleQuarter1 = SearchEngineCarouselInterpolation.calculateScale(1, quarterSlot, progress)
        val alphaQuarter0 = SearchEngineCarouselInterpolation.calculateAlpha(0, quarterSlot, progress)
        val alphaQuarter1 = SearchEngineCarouselInterpolation.calculateAlpha(1, quarterSlot, progress)
        val indicatorQuarter0 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, quarterSlot, progress)
        val indicatorQuarter1 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(1, quarterSlot, progress)

        // dist for 0 is 0.25 -> focus = 0.75 -> scale = 1.0 + 0.15 * 0.75 = 1.1125f
        assertEquals(1.1125f, scaleQuarter0, 0.001f)
        // dist for 1 is 0.75 -> focus = 0.25 -> scale = 1.0 + 0.15 * 0.25 = 1.0375f
        assertEquals(1.0375f, scaleQuarter1, 0.001f)

        // alpha for 0 is 0.45 + 0.55 * 0.75 = 0.8625f
        assertEquals(0.8625f, alphaQuarter0, 0.001f)
        // alpha for 1 is 0.45 + 0.55 * 0.25 = 0.5875f
        assertEquals(0.5875f, alphaQuarter1, 0.001f)

        // indicator for 0: dist 0.25 < 0.5 -> focus = 1 - 0.25*2 = 0.5f
        assertEquals(0.5f, indicatorQuarter0, 0.001f)
        // indicator for 1: dist 0.75 >= 0.5 -> 0f
        assertEquals(0.0f, indicatorQuarter1, 0.001f)
    }

    @Test
    fun testActiveSlotSwitchesWhenPassingMidpoint() {
        val progress = 1f
        // Scrolled fully to slot 1: centerSlotFloat = 1.0f
        val slot1AtCenter = 1.0f

        val scale1 = SearchEngineCarouselInterpolation.calculateScale(1, slot1AtCenter, progress)
        val alpha1 = SearchEngineCarouselInterpolation.calculateAlpha(1, slot1AtCenter, progress)
        val indicator1 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(1, slot1AtCenter, progress)

        assertEquals(1.15f, scale1, 0.001f)
        assertEquals(1.0f, alpha1, 0.001f)
        assertEquals(1.0f, indicator1, 0.001f)

        // Slot 0 and Slot 2 are now adjacent and dimmed
        listOf(0, 2).forEach { k ->
            val scale = SearchEngineCarouselInterpolation.calculateScale(k, slot1AtCenter, progress)
            val alpha = SearchEngineCarouselInterpolation.calculateAlpha(k, slot1AtCenter, progress)
            val indicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(k, slot1AtCenter, progress)

            assertEquals(1.0f, scale, 0.001f)
            assertEquals(0.45f, alpha, 0.001f)
            assertEquals(0.0f, indicator, 0.001f)
        }
    }

    @Test
    fun testRestingStateCollapse() {
        val centerSlot = 0f
        val restingProgress = 0f

        // When resting (not scrolling), active slot 0 settles at scale 1.0f, alpha 1.0f
        val centerScale = SearchEngineCarouselInterpolation.calculateScale(0, centerSlot, restingProgress)
        val centerAlpha = SearchEngineCarouselInterpolation.calculateAlpha(0, centerSlot, restingProgress)
        val centerIndicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, centerSlot, restingProgress)

        assertEquals(1.0f, centerScale, 0.001f)
        assertEquals(1.0f, centerAlpha, 0.001f)
        assertEquals(0.0f, centerIndicator, 0.001f)

        // Non-center slots fade completely to alpha 0f
        listOf(-1, 1, 2).forEach { k ->
            val scale = SearchEngineCarouselInterpolation.calculateScale(k, centerSlot, restingProgress)
            val alpha = SearchEngineCarouselInterpolation.calculateAlpha(k, centerSlot, restingProgress)
            val indicator = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(k, centerSlot, restingProgress)

            assertEquals(1.0f, scale, 0.001f)
            assertEquals(0.0f, alpha, 0.001f)
            assertEquals(0.0f, indicator, 0.001f)
        }
    }

    @Test
    fun testIndicatorVisibilityThreshold() {
        val centerSlot = 0f
        val progress = 1f

        // Exactly at center: fully active
        assertEquals(1.0f, SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, centerSlot, progress), 0.001f)

        // Close to boundary: dist = 0.4f
        val indicator04 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, 0.4f, progress)
        assertEquals(0.2f, indicator04, 0.001f)

        // At boundary dist = 0.5f: completely 0f
        val indicator05 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, 0.5f, progress)
        assertEquals(0.0f, indicator05, 0.001f)

        // Beyond boundary: dist = 0.6f
        val indicator06 = SearchEngineCarouselInterpolation.calculateIndicatorAlpha(0, 0.6f, progress)
        assertEquals(0.0f, indicator06, 0.001f)
    }

    @Test
    fun renderSearchEngineSelectorActiveAndDimmedVisualPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val density = Density(1f)

        val dummyHaptic = object : HapticFeedback {
            override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {}
        }

        val composeView = ComposeView(activity).apply {
            setContent {
                val coroutineScope = rememberCoroutineScope()

                // State scrolled with center item (Google) active, Bing and DuckDuckGo adjacent
                val carouselStateScrollingLight = remember(density) {
                    SearchEngineCarouselState(
                        initialEngine = SearchEngine.GOOGLE,
                        density = density,
                        coroutineScope = coroutineScope,
                        haptic = dummyHaptic,
                        onEngineSelected = {}
                    ).apply {
                        isDragging = true
                        coroutineScope.launch {
                            animOffset.snapTo(0f)
                        }
                    }
                }

                val carouselStateScrollingDark = remember(density) {
                    SearchEngineCarouselState(
                        initialEngine = SearchEngine.BING,
                        density = density,
                        coroutineScope = coroutineScope,
                        haptic = dummyHaptic,
                        onEngineSelected = {}
                    ).apply {
                        isDragging = true
                        coroutineScope.launch {
                            animOffset.snapTo(0f)
                        }
                    }
                }

                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEAF1FF))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Scrolling Light (Google Active 1.15x)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            SearchEngineSelectorButton(
                                carouselState = carouselStateScrollingLight,
                                isMenuOpen = false,
                                isDark = false,
                                onClick = {}
                            )
                        }

                        JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFF0F172A))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Scrolling Dark (Bing Active 1.15x)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                SearchEngineSelectorButton(
                                    carouselState = carouselStateScrollingDark,
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
        Thread.sleep(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val width = 480
        val height = 240
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_selector_active_dimmed_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }
}
