package com.jestk.browser.engine.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultTest {
    @Test fun allowHasNoFlags() {
        val r = MatchResult.ALLOW
        assertFalse(r.shouldBlock || r.isImportant || r.hasException || r.hasRedirect || r.matched)
    }

    @Test fun eachFlagDecodesIndependently() {
        assertTrue(MatchResult(1).shouldBlock)
        assertTrue(MatchResult(2).isImportant)
        assertTrue(MatchResult(4).hasException)
        assertTrue(MatchResult(8).hasRedirect)
        assertTrue(MatchResult(16).matched)
        assertFalse(MatchResult(2).shouldBlock)
    }

    @Test fun combinedFlags() {
        val r = MatchResult(1 or 8 or 16)
        assertTrue(r.shouldBlock && r.hasRedirect && r.matched)
        assertFalse(r.isImportant || r.hasException)
        assertEquals(25, r.flags)
    }

    @Test fun excepted() {
        val r = MatchResult(4 or 16)
        assertTrue(r.matched && r.hasException)
        assertFalse(r.shouldBlock)
    }
}
