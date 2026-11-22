package com.jestk.browser.ui.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TabSwitchAnimationTest {
    private val strip = setOf("a", "b", "c", "d")

    @Test
    fun normalSwitchCollapsesThenAdopts() {
        assertEquals(TabSwitchStep.CollapseThenAdopt, resolveTabSwitchStep("a", "b", strip, activeTabIsNew = false))
    }

    @Test
    fun sameTabOnlySettles() {
        assertEquals(TabSwitchStep.Settle, resolveTabSwitchStep("a", "a", strip, activeTabIsNew = false))
    }

    @Test
    fun retargetBackToPillTabSettles() {
        // Collapse of "a" was running, the user tapped "a" again.
        assertEquals(TabSwitchStep.Settle, resolveTabSwitchStep("a", "a", strip, activeTabIsNew = false))
    }

    @Test
    fun oldTabGoneAdoptsAtOnce() {
        assertEquals(TabSwitchStep.Adopt, resolveTabSwitchStep("gone", "b", strip, activeTabIsNew = false))
    }

    @Test
    fun activeTabHiddenAdoptsAtOnce() {
        assertEquals(TabSwitchStep.Adopt, resolveTabSwitchStep("a", "hidden", strip, activeTabIsNew = false))
    }

    @Test
    fun newTabAdoptsWithoutCollapse() {
        assertEquals(TabSwitchStep.Adopt, resolveTabSwitchStep("a", "e", strip + "e", activeTabIsNew = true))
    }

    @Test
    fun noPillYetAdopts() {
        assertEquals(TabSwitchStep.Adopt, resolveTabSwitchStep(null, "a", strip, activeTabIsNew = false))
        assertEquals(TabSwitchStep.Adopt, resolveTabSwitchStep("a", null, strip, activeTabIsNew = false))
    }

    @Test
    fun sequentialSwitchEndsOnNewPillAtOpenWidth() =
        runTest {
            withContext(TestMonotonicFrameClock(this)) {
                val state = TabSwitchAnimationState("a", 120.dp)
                state.switchTo(TabSwitchStep.CollapseThenAdopt, "b", 130.dp)
                assertEquals("b", state.pillTabId)
                assertEquals(130f, state.pillWidth.value, 0.5f)
                assertFalse(state.isCompact)
                assertFalse(state.isSequencing)
            }
        }

    @Test
    fun collapsePhaseKeepsOldPillAndCompactFlag() =
        runTest {
            withContext(TestMonotonicFrameClock(this)) {
                val state = TabSwitchAnimationState("a", 120.dp)
                val job = launch { state.switchTo(TabSwitchStep.CollapseThenAdopt, "b", 130.dp) }
                advanceTimeBy(48)
                assertEquals("a", state.pillTabId)
                assertTrue(state.isCompact)
                assertTrue(state.isSequencing)
                assertTrue(state.pillWidth < 120.dp)
                job.cancel()
            }
        }

    @Test
    fun retargetMidAnimationEndsOnLastTab() =
        runTest {
            withContext(TestMonotonicFrameClock(this)) {
                val state = TabSwitchAnimationState("a", 120.dp)
                var job = launch { state.switchTo(TabSwitchStep.CollapseThenAdopt, "b", 120.dp) }
                for (next in listOf("c", "d")) {
                    advanceTimeBy(32)
                    job.cancel()
                    val step = resolveTabSwitchStep(state.pillTabId, next, strip, activeTabIsNew = false)
                    job = launch { state.switchTo(step, next, 120.dp) }
                }
                job.join()
                assertEquals("d", state.pillTabId)
                assertEquals(120f, state.pillWidth.value, 0.5f)
                assertFalse(state.isSequencing)
            }
        }
}
