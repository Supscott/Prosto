package com.jestk.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeColorExtractionTest {

    @Test
    fun parseCssColor_nullOrEmpty_returnsNull() {
        assertNull(parseCssColor(null))
        assertNull(parseCssColor(""))
        assertNull(parseCssColor("   "))
        assertNull(parseCssColor("\"\""))
        assertNull(parseCssColor("null"))
        assertNull(parseCssColor("\"null\""))
    }

    @Test
    fun parseCssColor_transparent_returnsNull() {
        assertNull(parseCssColor("transparent"))
        assertNull(parseCssColor("\"transparent\""))
        assertNull(parseCssColor("rgba(0, 0, 0, 0)"))
        assertNull(parseCssColor("\"rgba(0, 0, 0, 0)\""))
    }

    @Test
    fun parseCssColor_hexColors_parsesSuccessfully() {
        // 6-digit hex
        val white = parseCssColor("\"#ffffff\"")
        assertNotNull(white)
        assertEquals(android.graphics.Color.rgb(255, 255, 255), white)

        val black = parseCssColor("#000000")
        assertNotNull(black)
        assertEquals(android.graphics.Color.rgb(0, 0, 0), black)

        // 3-digit hex
        val shortWhite = parseCssColor("#fff")
        assertNotNull(shortWhite)
        assertEquals(android.graphics.Color.rgb(255, 255, 255), shortWhite)
    }

    @Test
    fun parseCssColor_rgbAndRgbaColors_parsesSuccessfully() {
        val rgb = parseCssColor("\"rgb(18, 18, 18)\"")
        assertNotNull(rgb)
        assertEquals(android.graphics.Color.rgb(18, 18, 18), rgb)

        val rgba = parseCssColor("rgba(255, 100, 50, 1)")
        assertNotNull(rgba)
        assertEquals(android.graphics.Color.argb(255, 255, 100, 50), rgba)

        // Apple dark theme color test
        val appleColor = parseCssColor("#161617")
        assertNotNull(appleColor)
        assertEquals(android.graphics.Color.rgb(22, 22, 23), appleColor)

        // Semi-transparent rgba should resolve to opaque rgb
        val translucentApple = parseCssColor("rgba(22, 22, 23, 0.8)")
        assertNotNull(translucentApple)
        assertEquals(android.graphics.Color.rgb(22, 22, 23), translucentApple)

        // 8-digit hex
        val hex8 = parseCssColor("#161617cc")
        assertNotNull(hex8)
        assertEquals(android.graphics.Color.rgb(22, 22, 23), hex8)
    }
}
