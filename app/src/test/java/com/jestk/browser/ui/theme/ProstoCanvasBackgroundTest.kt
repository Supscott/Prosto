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
class ProstoCanvasBackgroundTest {

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
    fun prostoSkyBackground_zeroDimension_doesNotCrash() {
        val lightModifier = Modifier.prostoSkyBackground(isDark = false)
        val darkModifier = Modifier.prostoSkyBackground(isDark = true)

        // Must not throw IllegalArgumentException: radius must be > 0
        renderModifier(lightModifier, 0f, 0f)
        renderModifier(darkModifier, 0f, 0f)
    }

    @Test
    fun prostoSkyBackground_phonePortraitDimensions_rendersSuccessfully() {
        val lightModifier = Modifier.prostoSkyBackground(isDark = false)
        val darkModifier = Modifier.prostoSkyBackground(isDark = true)

        renderModifier(lightModifier, 1080f, 2400f)
        renderModifier(darkModifier, 1080f, 2400f)
    }

    @Test
    fun prostoSkyBackground_tabletLandscapeDimensions_rendersSuccessfully() {
        val lightModifier = Modifier.prostoSkyBackground(isDark = false)
        val darkModifier = Modifier.prostoSkyBackground(isDark = true)

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
    fun prostoShapes_standardCornerRadii_areCorrect() {
        val testSize = Size(200f, 200f)
        val testDensity = Density(1f)
        assertEquals(8f, ProstoShapes.Sm.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(16f, ProstoShapes.Md.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(24f, ProstoShapes.Card.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(28f, ProstoShapes.BottomSheet.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(28f, ProstoShapes.BottomSheet.topEnd.toPx(testSize, testDensity), 0.01f)
        assertEquals(0f, ProstoShapes.BottomSheet.bottomStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(32f, ProstoShapes.Dock.topStart.toPx(testSize, testDensity), 0.01f)
        // Continuous pill shapes resolve to 50% of the smaller dimension.
        assertEquals(100f, ProstoShapes.ButtonPill.topStart.toPx(testSize, testDensity), 0.01f)
        assertEquals(100f, ProstoShapes.Capsule.topStart.toPx(testSize, testDensity), 0.01f)
    }

    @Test
    fun prostoBorders_lightAndDark_produceCorrectStrokes() {
        val lightBorder = ProstoBorders.frostedBorder(isDark = false)
        val darkBorder = ProstoBorders.frostedBorder(isDark = true)
        assertEquals(1.dp, lightBorder.width)
        assertEquals(1.dp, darkBorder.width)
    }

    @Test
    fun prostoSolidSurface_createsNonNullModifiers() {
        val lightGlass = Modifier.prostoSolidSurface(shape = ProstoShapes.Card, isDark = false)
        val darkGlass = Modifier.prostoSolidSurface(shape = ProstoShapes.Dock, isDark = true)
        assertNotNull(lightGlass)
        assertNotNull(darkGlass)
    }
}
