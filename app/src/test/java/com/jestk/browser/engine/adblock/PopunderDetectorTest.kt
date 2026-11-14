package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PopunderDetectorTest {
    private var now = 10_000L
    private val logs = mutableListOf<String>()
    private val detector = PopunderDetector(clock = { now }, log = { logs += it })

    private fun jump(tab: String = "t1", crossSite: Boolean = true, gesture: Boolean = false) =
        detector.isPopunder(tab, crossSite, gesture)

    @Test
    fun crossSiteJumpInsideThresholdIsPopunder() {
        detector.onPopupOpened("t1")
        now += PopunderDetector.DEFAULT_THRESHOLD_MS - 1
        assertThat(jump()).isTrue()
    }

    @Test
    fun jumpAtOrAfterThresholdIsAllowed() {
        detector.onPopupOpened("t1")
        now += PopunderDetector.DEFAULT_THRESHOLD_MS
        assertThat(jump()).isFalse()
    }

    @Test
    fun gestureNavigationIsAllowed() {
        detector.onPopupOpened("t1")
        now += 100
        assertThat(jump(gesture = true)).isFalse()
    }

    @Test
    fun sameSiteNavigationIsAllowed() {
        detector.onPopupOpened("t1")
        now += 100
        assertThat(jump(crossSite = false)).isFalse()
    }

    @Test
    fun tabWithoutPopupIsAllowed() {
        assertThat(jump()).isFalse()
        assertThat(logs).isEmpty()
    }

    @Test
    fun tabsAreIndependent() {
        detector.onPopupOpened("t1")
        now += 100
        assertThat(jump("t2")).isFalse()
        assertThat(jump("t1")).isTrue()
    }

    @Test
    fun laterPopupRestartsTheWindow() {
        detector.onPopupOpened("t1")
        now += 900
        detector.onPopupOpened("t1")
        now += 900
        assertThat(jump()).isTrue()
    }

    @Test
    fun chainedHopsInsideTheWindowAreAllBlocked() {
        detector.onPopupOpened("t1")
        now += 200
        assertThat(jump()).isTrue()
        now += 200
        assertThat(jump()).isTrue()
    }

    @Test
    fun forgetDropsTheTab() {
        detector.onPopupOpened("t1")
        detector.forget("t1")
        assertThat(jump()).isFalse()
    }

    @Test
    fun everyDecisionIsLoggedWithItsDelta() {
        detector.onPopupOpened("t1")
        now += 250
        jump()
        now += 2000
        jump()
        assertThat(logs).hasSize(2)
        assertThat(logs[0]).contains("delta=250ms")
        assertThat(logs[0]).contains("BLOCK")
        assertThat(logs[1]).contains("delta=2250ms")
        assertThat(logs[1]).contains("allow")
    }
}
