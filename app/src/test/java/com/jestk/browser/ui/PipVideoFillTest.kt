package com.jestk.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PipVideoFillTest {
    @Test
    fun parseResult_readsOriginalStyleAndVideoSize() {
        val result = PipVideoFill.parseResult("[[null,\"width: 50%\"],1920,1080]")
        assertEquals(PipVideoFill.Result(listOf(null, "width: 50%"), 1920, 1080), result)
    }

    @Test
    fun parseResult_noVideo_isNull() {
        assertNull(PipVideoFill.parseResult("null"))
        assertNull(PipVideoFill.parseResult(""))
        assertNull(PipVideoFill.parseResult("{}"))
    }

    @Test
    fun applyScript_resumesOnlyWhenAsked() {
        assertTrue(PipVideoFill.applyScript(resume = true).contains("if (true && pick.paused)"))
        assertTrue(PipVideoFill.applyScript(resume = false).contains("if (false && pick.paused)"))
    }

    @Test
    fun scripts_leaveNoGlobals() {
        val scripts = PipVideoFill.applyScript(resume = true) + PipVideoFill.restoreScript(listOf("a"))
        assertFalse(scripts.contains("window."))
    }

    @Test
    fun restoreScript_quotesTheSavedStyle() {
        val script = PipVideoFill.restoreScript(listOf(null, "content: \"x\"; color: red"))
        assertTrue(script.contains("[null,\"content: \\\"x\\\"; color: red\"]"))
        assertTrue(script.contains(PipVideoFill.MARKER_Z_INDEX))
    }
}
