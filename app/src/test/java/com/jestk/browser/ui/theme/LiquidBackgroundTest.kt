package com.jestk.browser.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiquidBackgroundTest {

    private fun renderModifier(modifier: Modifier, width: Float, height: Float) {
        val bitmap = if (width > 0 && height > 0) {
            Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        val drawScope = CanvasDrawScope()

        var executed = false
        modifier.foldIn(Unit) { _, element ->
            if (element is DrawModifier) {
                drawScope.draw(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = canvas,
                    size = Size(width, height)
                ) {
                    val contentDrawScope = object : androidx.compose.ui.graphics.drawscope.ContentDrawScope, androidx.compose.ui.graphics.drawscope.DrawScope by this {
                        override fun drawContent() {}
                    }
                    with(element) {
                        contentDrawScope.draw()
                    }
                    executed = true
                }
            } else {
                try {
                    val onDrawField = element::class.java.getDeclaredFields().firstOrNull { it.name == "onDraw" }
                    if (onDrawField != null) {
                        onDrawField.isAccessible = true
                        val onDraw = onDrawField.get(element) as? (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)
                        if (onDraw != null) {
                            drawScope.draw(
                                density = Density(1f),
                                layoutDirection = LayoutDirection.Ltr,
                                canvas = canvas,
                                size = Size(width, height)
                            ) {
                                onDraw()
                                executed = true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        assertTrue("Expected element to be found and executed", executed)
    }

    @Test
    fun liquidSunsetBackground_zeroDimension_doesNotCrash() {
        val lightModifier = Modifier.liquidSunsetBackground(isDark = false)
        val darkModifier = Modifier.liquidSunsetBackground(isDark = true)

        // Must not throw IllegalArgumentException: radius must be > 0
        renderModifier(lightModifier, 0f, 0f)
        renderModifier(darkModifier, 0f, 0f)
    }

    @Test
    fun liquidSunsetBackground_phonePortraitDimensions_rendersSuccessfully() {
        val lightModifier = Modifier.liquidSunsetBackground(isDark = false)
        val darkModifier = Modifier.liquidSunsetBackground(isDark = true)

        renderModifier(lightModifier, 1080f, 2400f)
        renderModifier(darkModifier, 1080f, 2400f)
    }

    @Test
    fun liquidSunsetBackground_tabletLandscapeDimensions_rendersSuccessfully() {
        val lightModifier = Modifier.liquidSunsetBackground(isDark = false)
        val darkModifier = Modifier.liquidSunsetBackground(isDark = true)

        renderModifier(lightModifier, 2560f, 1600f)
        renderModifier(darkModifier, 2560f, 1600f)
    }

    @Test
    fun tabSearchFilter_matchesTitleAndUrlCaseInsensitive() {
        val tab1 = com.jestk.browser.domain.model.Tab(
            id = "1",
            url = "https://apple.com",
            title = "Apple Official"
        )
        val tab2 = com.jestk.browser.domain.model.Tab(
            id = "2",
            url = "https://google.com",
            title = "Google Search"
        )
        val tabs = listOf(tab1, tab2)

        fun filterTabs(query: String) = if (query.isBlank()) tabs else tabs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.url.contains(query, ignoreCase = true)
        }

        // Substring case-insensitive match on title
        org.junit.Assert.assertEquals(listOf(tab1), filterTabs("app"))
        org.junit.Assert.assertEquals(listOf(tab1), filterTabs("APPLE"))

        // Substring case-insensitive match on url
        org.junit.Assert.assertEquals(listOf(tab2), filterTabs("google"))
        org.junit.Assert.assertEquals(listOf(tab2), filterTabs("GOOGLE.COM"))

        // Blank query returns all
        org.junit.Assert.assertEquals(tabs, filterTabs(""))
        org.junit.Assert.assertEquals(tabs, filterTabs("   "))

        // No match returns empty
        org.junit.Assert.assertTrue(filterTabs("nonexistent").isEmpty())
    }

    @Test
    fun liquidShapes_standardCornerRadii_areCorrect() {
        val testSize = Size(200f, 200f)
        val testDensity = Density(1f)
        assertEquals(18f, LiquidShapes.Card.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(22f, LiquidShapes.ButtonPill.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(24f, LiquidShapes.Capsule.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(28f, LiquidShapes.BottomSheet.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(28f, LiquidShapes.BottomSheet.topEnd.toPx(testSize, testDensity), 0.01f)
        assertEquals(0f, LiquidShapes.BottomSheet.bottomStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(29f, LiquidShapes.Dock.topStart.toPx(testSize, testDensity), 0.01f)
    }

    @Test
    fun liquidBorders_lightAndDark_produceCorrectStrokes() {
        val lightBorder = LiquidBorders.frostedBorder(isDark = false)
        val darkBorder = LiquidBorders.frostedBorder(isDark = true)
        assertEquals(1.dp, lightBorder.width)
        assertEquals(1.dp, darkBorder.width)
    }

    @Test
    fun liquidFrostedGlass_createsNonNullModifiers() {
        val lightGlass = Modifier.liquidFrostedGlass(shape = LiquidShapes.Card, isDark = false)
        val darkGlass = Modifier.liquidFrostedGlass(shape = LiquidShapes.Dock, isDark = true)
        assertNotNull(lightGlass)
        assertNotNull(darkGlass)
    }
}
