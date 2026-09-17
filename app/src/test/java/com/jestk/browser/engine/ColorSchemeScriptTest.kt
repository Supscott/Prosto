package com.jestk.browser.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemeScriptTest {

    @Test
    fun buildColorSchemeScript_darkTrue_containsDarkTrue() {
        val script = buildColorSchemeScript(true)
        assertTrue(script.contains("var dark = true;"))
        assertTrue(script.contains("prefers-color-scheme"))
        assertTrue(script.contains("window.__jestkIsDark = dark;"))
        assertTrue(script.contains("youtube.com"))
    }

    @Test
    fun buildColorSchemeScript_darkFalse_containsDarkFalse() {
        val script = buildColorSchemeScript(false)
        assertTrue(script.contains("var dark = false;"))
        assertTrue(script.contains("prefers-color-scheme"))
        assertTrue(script.contains("window.__jestkIsDark = dark;"))
        assertTrue(script.contains("youtube.com"))
    }
}
