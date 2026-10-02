package com.jestk.browser.ui.overlays

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.jestk.browser.domain.model.SearchEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEngineReorderStateTest {

    private val testFrameClock = object : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private val dummyHaptic = object : HapticFeedback {
        var tickCount = 0
        override fun performHapticFeedback(feedbackConstant: HapticFeedbackType) {
            tickCount++
        }
    }

    @Test
    fun initialState_isIdle() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = {}
        )

        assertNull(state.draggingIndex)
        assertEquals(-1, state.targetIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertFalse(state.isDragging)
        assertFalse(state.isDropping)
    }

    @Test
    fun dragDownwards_updatesTargetIndexAndDisplacesSiblings() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BING)

        // Start drag at index 0 (Google)
        state.onDragStart(0)
        assertEquals(0, state.draggingIndex)
        assertEquals(0, state.targetIndex)
        assertTrue(state.isDragging)
        assertEquals(0f, state.getSiblingTargetOffset(1), 0.001f)
        assertEquals(0f, state.getSiblingTargetOffset(2), 0.001f)

        // Drag down by 40px (past half of 48px slot -> prospective slot 1)
        state.onDrag(40f, engines.size)
        assertEquals(1, state.targetIndex)
        // DuckDuckGo (index 1) must shift UP by 48px into slot 0
        assertEquals(-48f, state.getSiblingTargetOffset(1), 0.001f)
        // Bing (index 2) stays in slot 2
        assertEquals(0f, state.getSiblingTargetOffset(2), 0.001f)

        // Drag down further by 50px (total 90px -> prospective slot 2)
        state.onDrag(50f, engines.size)
        assertEquals(2, state.targetIndex)
        // DuckDuckGo (index 1) shifts UP into slot 0
        assertEquals(-48f, state.getSiblingTargetOffset(1), 0.001f)
        // Bing (index 2) shifts UP into slot 1
        assertEquals(-48f, state.getSiblingTargetOffset(2), 0.001f)
    }

    @Test
    fun dragUpwards_updatesTargetIndexAndDisplacesSiblings() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BING)

        // Start drag at index 2 (Bing)
        state.onDragStart(2)
        assertEquals(2, state.draggingIndex)
        assertEquals(2, state.targetIndex)

        // Drag up by -90px -> prospective slot 0
        state.onDrag(-90f, engines.size)
        assertEquals(0, state.targetIndex)
        // Google (index 0) must shift DOWN by 48px into slot 1
        assertEquals(48f, state.getSiblingTargetOffset(0), 0.001f)
        // DuckDuckGo (index 1) must shift DOWN by 48px into slot 2
        assertEquals(48f, state.getSiblingTargetOffset(1), 0.001f)
        // Bing is active
        assertEquals(0f, state.getSiblingTargetOffset(2), 0.001f)
    }

    @Test
    fun dragBeyondBounds_isClampedToSlotRange() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BING)

        // Starting at index 0, cannot drag above 0
        state.onDragStart(0)
        state.onDrag(-300f, engines.size)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertEquals(0, state.targetIndex)

        // Starting at index 2, cannot drag below 2
        state.onDragCancel()
        testScheduler.advanceUntilIdle()

        state.onDragStart(2)
        state.onDrag(300f, engines.size)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertEquals(2, state.targetIndex)
    }

    @Test
    fun dragEnd_animatesDropAndReordersList() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = { persistedList = it }
        )

        val initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BING)
        var currentList = initialList

        // Drag Google (index 0) down to slot 2
        state.onDragStart(0)
        state.onDrag(100f, currentList.size)
        assertEquals(2, state.targetIndex)

        // Release / drop
        state.onDragEnd(currentList) { updated ->
            currentList = updated
        }

        // Advance animation to completion
        testScheduler.advanceUntilIdle()

        // List must be reordered: DuckDuckGo, Bing, Google
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BING, SearchEngine.GOOGLE), currentList)
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BING, SearchEngine.GOOGLE), persistedList)

        // State must reset cleanly
        assertNull(state.draggingIndex)
        assertEquals(-1, state.targetIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertFalse(state.isDragging)
        assertFalse(state.isDropping)
    }

    @Test
    fun dragCancel_animatesBackWithoutReordering() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            haptic = dummyHaptic,
            onReorder = { persistedList = it }
        )

        val initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BING)

        state.onDragStart(0)
        state.onDrag(40f, initialList.size)
        assertEquals(1, state.targetIndex)

        state.onDragCancel()
        testScheduler.advanceUntilIdle()

        assertNull(persistedList)
        assertNull(state.draggingIndex)
        assertEquals(-1, state.targetIndex)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        assertFalse(state.isDragging)
    }
}
