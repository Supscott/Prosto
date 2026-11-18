package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumeNewTabPolicyTest {
    private val policy = ResumeNewTabPolicy()
    private val away = BackgroundStamp(atMs = 1_000L, mediaActive = false)
    private val threshold = ResumeNewTabPolicy.DEFAULT_THRESHOLD_MS

    private fun decide(
        stamp: BackgroundStamp? = away,
        nowMs: Long = away.atMs + threshold,
        hasTabs: Boolean = true,
        activeTabIsHome: Boolean = false,
        returnedWithLink: Boolean = false,
        mediaActiveNow: Boolean = false,
    ) = policy.shouldOpenNewTab(stamp, nowMs, hasTabs, activeTabIsHome, returnedWithLink, mediaActiveNow)

    @Test
    fun exactlyAtThreshold_opensNewTab() {
        assertThat(decide()).isTrue()
    }

    @Test
    fun justBelowThreshold_doesNothing() {
        assertThat(decide(nowMs = away.atMs + threshold - 1)).isFalse()
    }

    @Test
    fun clockGoingBackwards_doesNothing() {
        assertThat(decide(nowMs = away.atMs - 5)).isFalse()
    }

    @Test
    fun noStamp_firstLaunch_doesNothing() {
        assertThat(decide(stamp = null)).isFalse()
    }

    @Test
    fun noTabs_doesNothing() {
        assertThat(decide(hasTabs = false)).isFalse()
    }

    @Test
    fun activeHomeTab_doesNothing() {
        assertThat(decide(activeTabIsHome = true)).isFalse()
    }

    @Test
    fun returnWithLink_doesNothing() {
        assertThat(decide(returnedWithLink = true)).isFalse()
    }

    @Test
    fun mediaActiveWhenLeaving_doesNothing() {
        assertThat(decide(stamp = away.copy(mediaActive = true))).isFalse()
    }

    @Test
    fun backgroundPlaybackStillRunning_doesNothing() {
        assertThat(decide(mediaActiveNow = true)).isFalse()
    }
}
