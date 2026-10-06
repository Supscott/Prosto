package com.jestk.browser.ui.navigation

import androidx.compose.runtime.MonotonicFrameClock
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabStripDragSpringTest {

    private val testFrameClock = object : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            kotlinx.coroutines.delay(16)
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private val sampleTabs = listOf(
        Tab(id = "tab1", url = "https://example.com", title = "Example", isActive = true),
        Tab(id = "tab2", url = "https://google.com", title = "Google", isActive = false),
        Tab(id = "tab3", url = "https://github.com", title = "GitHub", isActive = false)
    )

    @Test
    fun initialState_isIdle() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        assertNull(state.draggingTabId)
        assertNull(state.settlingTabId)
        assertEquals(0f, state.dragOffsetPx, 0.001f)
        assertFalse(state.isDragging)
        assertFalse(state.isSettling)
        assertEquals(0f, state.settleOffsetAnim.value, 0.001f)
        assertEquals(1.0f, state.settleScaleAnim.value, 0.001f)
        assertEquals(sampleTabs, state.currentTabs)
    }

    @Test
    fun onDragStart_activatesDraggingState() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab1")

        assertEquals("tab1", state.draggingTabId)
        assertTrue(state.isDragging)
        assertFalse(state.isSettling)
        assertEquals(0f, state.dragOffsetPx, 0.001f)
    }

    @Test
    fun onDrag_movesOffsetAndSwapsTabs() = runTest(testFrameClock) {
        var hapticCount = 0
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab1")

        // Drag right by 50px (past 44px threshold)
        state.onDrag(
            tabId = "tab1",
            deltaX = 50f,
            swapThreshold = 44f,
            onSwapHaptic = { hapticCount++ }
        )

        // tab1 and tab2 should be swapped
        assertEquals(listOf("tab2", "tab1", "tab3"), state.currentTabs.map { it.id })
        // dragOffsetPx should have 44px subtracted (50 - 44 = 6)
        assertEquals(6f, state.dragOffsetPx, 0.001f)
        assertEquals(1, hapticCount)

        // Drag left by -50px (total offset = 6 - 50 = -44 -> triggers swap left)
        state.onDrag(
            tabId = "tab1",
            deltaX = -52f,
            swapThreshold = 44f,
            onSwapHaptic = { hapticCount++ }
        )

        // tab1 swaps back to index 0
        assertEquals(listOf("tab1", "tab2", "tab3"), state.currentTabs.map { it.id })
        assertEquals(2, hapticCount)
    }

    @Test
    fun onDragEnd_activeTab_animatesDropWithSpringPhysicsAndDispatchesReorder() = runTest(testFrameClock) {
        var reorderedTabs: List<Tab>? = null
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = { reorderedTabs = it }
        )

        state.onDragStart("tab1")
        state.onDrag(
            tabId = "tab1",
            deltaX = 60f,
            swapThreshold = 44f
        )
        // Order is now tab2, tab1, tab3 with residual offset = 16f
        assertEquals(16f, state.dragOffsetPx, 0.001f)

        // End drag for active tab
        state.onDragEnd(isReleasedActive = true, originalTabs = sampleTabs)

        // isDragging becomes false immediately so elevation/gesture releases
        assertFalse(state.isDragging)
        assertNull(state.draggingTabId)
        assertEquals("tab1", state.settlingTabId)
        assertTrue(state.isSettling)

        // Animatable must have snapped to starting values at release instant
        assertEquals(16f, state.settleOffsetAnim.value, 0.001f)
        assertEquals(1.05f, state.settleScaleAnim.value, 0.001f)

        // Reorder event dispatched immediately
        assertThat(reorderedTabs).isNotNull()
        assertEquals(listOf("tab2", "tab1", "tab3"), reorderedTabs!!.map { it.id })

        // Advance animation by a few frames (48ms) — values should be progressing toward target
        advanceTimeBy(48)
        assertTrue(state.isSettling)
        assertThat(state.settleOffsetAnim.value).isLessThan(16f)
        assertThat(state.settleScaleAnim.value).isLessThan(1.05f)

        // Advance to full completion
        testScheduler.advanceUntilIdle()

        // Settle state resets cleanly
        assertFalse(state.isSettling)
        assertNull(state.settlingTabId)
        assertEquals(0f, state.settleOffsetAnim.value, 0.001f)
        assertEquals(1.0f, state.settleScaleAnim.value, 0.001f)
    }

    @Test
    fun onDragEnd_inactiveTab_animatesFromScale1_08() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab2")
        state.onDrag(
            tabId = "tab2",
            deltaX = 20f,
            swapThreshold = 44f
        )

        state.onDragEnd(isReleasedActive = false, originalTabs = sampleTabs)

        assertFalse(state.isDragging)
        assertEquals("tab2", state.settlingTabId)
        assertTrue(state.isSettling)
        assertEquals(20f, state.settleOffsetAnim.value, 0.001f)
        assertEquals(1.08f, state.settleScaleAnim.value, 0.001f)

        testScheduler.advanceUntilIdle()

        assertFalse(state.isSettling)
        assertNull(state.settlingTabId)
        assertEquals(0f, state.settleOffsetAnim.value, 0.001f)
        assertEquals(1.0f, state.settleScaleAnim.value, 0.001f)
    }

    @Test
    fun onDragCancel_springsBackWithoutDispatchingReorder() = runTest(testFrameClock) {
        var reorderDispatched = false
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = { reorderDispatched = true }
        )

        state.onDragStart("tab1")
        state.onDrag(
            tabId = "tab1",
            deltaX = 25f,
            swapThreshold = 44f
        )

        state.onDragCancel(isReleasedActive = true)

        assertFalse(state.isDragging)
        assertTrue(state.isSettling)
        assertFalse(reorderDispatched)
        assertEquals(25f, state.settleOffsetAnim.value, 0.001f)

        testScheduler.advanceUntilIdle()

        assertFalse(state.isSettling)
        assertNull(state.settlingTabId)
        assertEquals(0f, state.settleOffsetAnim.value, 0.001f)
        assertFalse(reorderDispatched)
    }

    @Test
    fun newDragInterruptingSettling_cancelsPreviousSpringAndResetsImmediately() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        // Drag and drop tab1
        state.onDragStart("tab1")
        state.onDrag(tabId = "tab1", deltaX = 30f, swapThreshold = 44f)
        state.onDragEnd(isReleasedActive = true, originalTabs = sampleTabs)

        assertTrue(state.isSettling)
        assertEquals("tab1", state.settlingTabId)

        // Advance partially into animation
        advanceTimeBy(32)
        assertTrue(state.isSettling)

        // Immediately start a new drag on tab3 while tab1 is still settling!
        state.onDragStart("tab3")

        // Previous settle must be cancelled immediately
        assertFalse(state.isSettling)
        assertNull(state.settlingTabId)

        // New drag is active
        assertTrue(state.isDragging)
        assertEquals("tab3", state.draggingTabId)
        assertEquals(0f, state.dragOffsetPx, 0.001f)
    }

    @Test
    fun updateTabs_whileSettling_defersUpdateUntilSettlingFinishes() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab1")
        state.onDrag(tabId = "tab1", deltaX = 50f, swapThreshold = 44f)
        state.onDragEnd(isReleasedActive = true, originalTabs = sampleTabs)
        assertTrue(state.isSettling)

        // An external tab list update arrives while settling
        val updatedFromDB = listOf(
            Tab(id = "tab2", url = "https://google.com", title = "Google", isActive = false),
            Tab(id = "tab1", url = "https://example.com", title = "Example", isActive = true),
            Tab(id = "tab3", url = "https://github.com", title = "GitHub", isActive = false),
            Tab(id = "tab4", url = "https://new.com", title = "New", isActive = false)
        )
        state.updateTabs(updatedFromDB)

        // While settling, tabs list is preserved
        assertEquals(3, state.currentTabs.size)

        // When settling finishes, deferred tabs are applied
        testScheduler.advanceUntilIdle()
        assertFalse(state.isSettling)
        assertEquals(4, state.currentTabs.size)
        assertEquals("tab4", state.currentTabs.last().id)
    }

    @Test
    fun springSettlingCompletesPromptlyWithoutLingeringSubpixelTails() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs,
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab1")
        state.onDrag(tabId = "tab1", deltaX = 15f, swapThreshold = 44f)
        state.onDragEnd(isReleasedActive = true, originalTabs = sampleTabs)

        assertTrue(state.isSettling)

        // With StiffnessMedium and visibilityThreshold = 0.5f, the spring finishes in <= 350ms
        advanceTimeBy(350)
        assertFalse("Spring should have settled within 350ms due to 0.5f visibility threshold", state.isSettling)
        assertNull(state.settlingTabId)
    }

    @Test
    fun computeSlotDistance_calculatesCorrectDistanceForActiveAndInactive() = runTest(testFrameClock) {
        val state = TabStripDragState(
            initialTabs = sampleTabs, // tab1: active, tab2: inactive, tab3: inactive
            coroutineScope = this,
            onReorder = {}
        )

        // Active (148px) <-> Inactive (38px) with 5px spacing -> (148 + 38) / 2 + 5 = 98px
        val activeToInactive = state.computeSlotDistance(
            currentIndex = 0,
            targetIndex = 1,
            activeTabWidthPx = 148f,
            inactiveTabWidthPx = 38f,
            spacingPx = 5f
        )
        assertEquals(98f, activeToInactive, 0.001f)

        // Inactive (38px) <-> Inactive (38px) with 5px spacing -> (38 + 38) / 2 + 5 = 43px
        val inactiveToInactive = state.computeSlotDistance(
            currentIndex = 1,
            targetIndex = 2,
            activeTabWidthPx = 148f,
            inactiveTabWidthPx = 38f,
            spacingPx = 5f
        )
        assertEquals(43f, inactiveToInactive, 0.001f)
    }

    @Test
    fun onDrag_dynamicActiveAndInactiveSlotDistance_adjustsOffsetByActualDistance() = runTest(testFrameClock) {
        var hapticCount = 0
        val state = TabStripDragState(
            initialTabs = sampleTabs, // tab1: active, tab2: inactive, tab3: inactive
            coroutineScope = this,
            onReorder = {}
        )

        state.onDragStart("tab1")

        // Slot distance between tab1 (148px) and tab2 (38px) is 98px
        // Drag right by 105px (past 98px distance)
        state.onDrag(
            tabId = "tab1",
            deltaX = 105f,
            activeTabWidthPx = 148f,
            inactiveTabWidthPx = 38f,
            spacingPx = 5f,
            onSwapHaptic = { hapticCount++ }
        )

        // tab1 and tab2 should be swapped
        assertEquals(listOf("tab2", "tab1", "tab3"), state.currentTabs.map { it.id })
        // dragOffsetPx should have exact slot distance (98px) subtracted: 105 - 98 = 7px
        // Rock-solid: no visual jump under finger!
        assertEquals(7f, state.dragOffsetPx, 0.001f)
        assertEquals(1, hapticCount)

        // Drag tab1 left by -109px (total = 7 - 109 = -102px, past -98px distance)
        state.onDrag(
            tabId = "tab1",
            deltaX = -109f,
            activeTabWidthPx = 148f,
            inactiveTabWidthPx = 38f,
            spacingPx = 5f,
            onSwapHaptic = { hapticCount++ }
        )

        // tab1 swaps back to index 0
        assertEquals(listOf("tab1", "tab2", "tab3"), state.currentTabs.map { it.id })
        // dragOffsetPx should have exact slot distance (98px) added: -102 + 98 = -4px
        assertEquals(-4f, state.dragOffsetPx, 0.001f)
        assertEquals(2, hapticCount)
    }
}
