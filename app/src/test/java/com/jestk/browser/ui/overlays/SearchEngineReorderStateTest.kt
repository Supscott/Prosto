package com.jestk.browser.ui.overlays

import androidx.compose.runtime.MonotonicFrameClock
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.util.VibrationFeedback
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

    private val dummyVibration = object : VibrationFeedback {
        var tickCount = 0
        override fun tick() {
            tickCount++
        }
        override fun confirm() {}
    }

    @Test
    fun initialState_isIdle() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
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
            vibration = dummyVibration,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

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
            vibration = dummyVibration,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

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
            vibration = dummyVibration,
            onReorder = {}
        )

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

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
            vibration = dummyVibration,
            onReorder = { persistedList = it }
        )

        val initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
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
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE, SearchEngine.GOOGLE), currentList)
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE, SearchEngine.GOOGLE), persistedList)

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
            vibration = dummyVibration,
            onReorder = { persistedList = it }
        )

        val initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

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

    @Test
    fun dragByEngineId_reordersCorrectlyAcrossMultipleRapidDrags() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            onReorder = { persistedList = it }
        )

        var list = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

        // 1st drag: Move Google (index 0) to slot 2
        state.onDragStart(SearchEngine.GOOGLE.id, list)
        assertEquals(SearchEngine.GOOGLE.id, state.draggingEngineId)
        assertEquals(0, state.draggingIndex)

        state.onDrag(100f, list)
        assertEquals(2, state.targetIndex)

        state.onDragEnd(list) { updated -> list = updated }
        testScheduler.advanceUntilIdle()

        // List is now: [DUCKDUCKGO, BING, GOOGLE]
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE, SearchEngine.GOOGLE), list)
        assertEquals(list, persistedList)

        // 2nd rapid drag: Move GOOGLE (now at index 2!) up to slot 0
        state.onDragStart(SearchEngine.GOOGLE.id, list)
        assertEquals(SearchEngine.GOOGLE.id, state.draggingEngineId)
        assertEquals(2, state.draggingIndex) // Dynamically resolved to 2, NOT stale 0!

        state.onDrag(-100f, list)
        assertEquals(0, state.targetIndex)

        state.onDragEnd(list) { updated -> list = updated }
        testScheduler.advanceUntilIdle()

        // List is back to: [GOOGLE, DUCKDUCKGO, BING]
        assertEquals(listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE), list)
        assertEquals(list, persistedList)

        // 3rd rapid drag: Move BING (index 2) to slot 1
        state.onDragStart(SearchEngine.BRAVE.id, list)
        assertEquals(2, state.draggingIndex)
        state.onDrag(-48f, list)
        assertEquals(1, state.targetIndex)

        state.onDragEnd(list) { updated -> list = updated }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(SearchEngine.GOOGLE, SearchEngine.BRAVE, SearchEngine.DUCKDUCKGO), list)
    }

    @Test
    fun dragReleaseInSameSlot_settlesWithoutUpdatingListOrDB() = runTest(testFrameClock) {
        var onReorderCalled = false
        var onListUpdatedCalled = false

        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            onReorder = { onReorderCalled = true }
        )

        val list = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

        // Start drag on DuckDuckGo (index 1)
        state.onDragStart(SearchEngine.DUCKDUCKGO.id, list)
        assertEquals(1, state.draggingIndex)

        // Small nudge that doesn't cross slot threshold (stays in slot 1)
        state.onDrag(10f, list)
        assertEquals(1, state.targetIndex)

        state.onDragEnd(list) {
            onListUpdatedCalled = true
        }
        testScheduler.advanceUntilIdle()

        // Must NOT update list or call onReorder
        assertFalse(onListUpdatedCalled)
        assertFalse(onReorderCalled)
        assertNull(state.draggingEngineId)
        assertFalse(state.isDragging)
        assertFalse(state.isDropping)
    }

    @Test
    fun getSiblingTargetOffset_byEngineId_accuratelyDisplacesSiblings() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            onReorder = {}
        )

        val list = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)

        state.onDragStart(SearchEngine.GOOGLE.id, list)
        state.onDrag(50f, list) // target slot 1

        assertEquals(1, state.targetIndex)
        // DuckDuckGo should move UP into slot 0
        assertEquals(-48f, state.getSiblingTargetOffset(SearchEngine.DUCKDUCKGO.id, list), 0.001f)
        // Bing should stay at 0f
        assertEquals(0f, state.getSiblingTargetOffset(SearchEngine.BRAVE.id, list), 0.001f)
        // Dragged item itself has 0 sibling offset
        assertEquals(0f, state.getSiblingTargetOffset(SearchEngine.GOOGLE.id, list), 0.001f)
    }

    @Test
    fun dragInterruption_newDragDuringDropBounce_instantlyCommitsPendingDropAndStartsNewDrag() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE),
            onReorder = { persistedList = it }
        )

        // 1st drag: Move Google (index 0) to slot 2
        state.onDragStart(SearchEngine.GOOGLE.id)
        state.onDrag(100f)
        assertEquals(2, state.targetIndex)

        // Release to start drop animation (dropAnim starts bouncing)
        state.onDragEnd()
        assertTrue(state.isDropping)

        // Immediately start 2nd drag before drop animation completes!
        state.onDragStart(SearchEngine.DUCKDUCKGO.id)

        // Previous drop must have been committed instantly!
        assertFalse(state.isDropping)
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE, SearchEngine.GOOGLE), state.currentList)
        assertEquals(state.currentList, persistedList)

        // And new drag must be active on DUCKDUCKGO at index 0
        assertEquals(SearchEngine.DUCKDUCKGO.id, state.draggingEngineId)
        assertEquals(0, state.draggingIndex)

        // Drag DuckDuckGo down to slot 1
        state.onDrag(50f)
        assertEquals(1, state.targetIndex)
        state.onDragEnd()

        testScheduler.advanceUntilIdle()

        // Final list: [BING, DUCKDUCKGO, GOOGLE]
        assertEquals(listOf(SearchEngine.BRAVE, SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE), state.currentList)
        assertEquals(state.currentList, persistedList)
    }

    @Test
    fun multipleConsecutiveDragsWithoutPassingList_updatesAuthoritativeCurrentList() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE),
            onReorder = { persistedList = it }
        )

        // 1. Move Google to slot 2
        state.onDragStart(SearchEngine.GOOGLE.id)
        state.onDrag(100f)
        state.onDragEnd()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE, SearchEngine.GOOGLE), state.currentList)

        // 2. Move Google back to slot 0
        state.onDragStart(SearchEngine.GOOGLE.id)
        assertEquals(2, state.draggingIndex) // dynamic lookup in authoritative list!
        state.onDrag(-100f)
        assertEquals(0, state.targetIndex)
        state.onDragEnd()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE), state.currentList)

        // 3. Move Bing to slot 1
        state.onDragStart(SearchEngine.BRAVE.id)
        assertEquals(2, state.draggingIndex)
        state.onDrag(-48f)
        assertEquals(1, state.targetIndex)
        state.onDragEnd()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(SearchEngine.GOOGLE, SearchEngine.BRAVE, SearchEngine.DUCKDUCKGO), state.currentList)
        assertEquals(state.currentList, persistedList)
    }

    @Test
    fun strictClamping_preventsDraggingBeyondBoxBoundaries() = runTest(testFrameClock) {
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE),
            onReorder = {}
        )

        // Index 0: can only drag down, never up
        state.onDragStart(SearchEngine.GOOGLE.id)
        state.onDrag(-200f)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        state.onDrag(500f)
        assertEquals(96f, state.dragOffsetY, 0.001f) // clamped to maxOffset = 2 * 48 = 96
        state.onDragCancel()
        testScheduler.advanceUntilIdle()

        // Index 2: can only drag up, never down
        state.onDragStart(SearchEngine.BRAVE.id)
        state.onDrag(200f)
        assertEquals(0f, state.dragOffsetY, 0.001f)
        state.onDrag(-500f)
        assertEquals(-96f, state.dragOffsetY, 0.001f) // clamped to minOffset = -2 * 48 = -96
        state.onDragCancel()
        testScheduler.advanceUntilIdle()

        // Index 1 (middle): can drag ±48px
        state.onDragStart(SearchEngine.DUCKDUCKGO.id)
        state.onDrag(-200f)
        assertEquals(-48f, state.dragOffsetY, 0.001f)
        state.onDrag(500f)
        assertEquals(48f, state.dragOffsetY, 0.001f)
        state.onDragCancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun dragEnd_isDraggingBecomesFalseImmediatelyAndDropSettlesPromptly() = runTest(testFrameClock) {
        var persistedList: List<SearchEngine>? = null
        val state = SearchEngineReorderState(
            slotHeightPx = 48f,
            coroutineScope = this,
            vibration = dummyVibration,
            initialList = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE),
            onReorder = { persistedList = it }
        )

        state.onDragStart(SearchEngine.GOOGLE.id)
        assertTrue(state.isDragging)
        assertFalse(state.isDropping)

        state.onDrag(50f)
        assertEquals(1, state.targetIndex)

        // Release finger
        state.onDragEnd()

        // isDragging MUST become false immediately so scale/elevation begin landing
        assertFalse(state.isDragging)
        assertTrue(state.isDropping)

        // Snappy spring settles in <= 200ms, does not hang for 500ms
        testScheduler.advanceTimeBy(250)
        assertFalse(state.isDropping)
        assertNull(state.draggingEngineId)
        assertEquals(listOf(SearchEngine.DUCKDUCKGO, SearchEngine.GOOGLE, SearchEngine.BRAVE), state.currentList)
        assertEquals(state.currentList, persistedList)
    }
}

