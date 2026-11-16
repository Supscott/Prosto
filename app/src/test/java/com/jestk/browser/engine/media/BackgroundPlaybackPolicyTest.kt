package com.jestk.browser.engine.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPlaybackPolicyTest {
    private fun keep(
        enabled: Boolean = true,
        pip: Boolean = false,
        config: Boolean = false,
        webView: Boolean = true,
        state: MediaPageState = MediaPageState.PLAYING,
    ) = shouldKeepPlayingInBackground(enabled, pip, config, webView, state)

    @Test
    fun keepsPlayingWhenEnabledAndPlaying() = assertTrue(keep())

    @Test
    fun offSettingNeverKeeps() = assertFalse(keep(enabled = false))

    @Test
    fun pictureInPictureAndConfigChangeDoNotKeep() {
        assertFalse(keep(pip = true))
        assertFalse(keep(config = true))
    }

    @Test
    fun needsWebViewAndPlayingMedia() {
        assertFalse(keep(webView = false))
        assertFalse(keep(state = MediaPageState.PAUSED))
        assertFalse(keep(state = MediaPageState.NONE))
    }

    @Test
    fun parsesJavascriptResult() {
        assertEquals(MediaPageState.PLAYING, MediaPageState.parse("\"playing\""))
        assertEquals(MediaPageState.PAUSED, MediaPageState.parse("\"paused\""))
        assertEquals(MediaPageState.NONE, MediaPageState.parse("\"none\""))
        assertEquals(MediaPageState.NONE, MediaPageState.parse(null))
        assertEquals(MediaPageState.NONE, MediaPageState.parse("null"))
    }

    @Test
    fun sessionStopsWhenMediaIsGoneOrPausedTooLong() {
        assertFalse(shouldStopBackgroundSession(MediaPageState.PLAYING, 0))
        assertFalse(shouldStopBackgroundSession(MediaPageState.PAUSED, PAUSED_SESSION_TIMEOUT_MS - 1))
        assertTrue(shouldStopBackgroundSession(MediaPageState.PAUSED, PAUSED_SESSION_TIMEOUT_MS))
        assertTrue(shouldStopBackgroundSession(MediaPageState.NONE, 0))
    }
}
