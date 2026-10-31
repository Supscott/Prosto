package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the drag-reorder gesture.
 *
 * The gesture used to live inside the tab items. Reordering recreates the dragged item's
 * `LayoutNode` when its slot switches between the active pill and an inactive circle, which
 * synthesizes a pointer cancel and aborts the drag. These tests perform real long-press drags
 * across the active tab (the exact paths that used to fail) and assert that the reorder is
 * dispatched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TabStripRowDragTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private var vibrationTicks = 0
    private var vibrationConfirms = 0
    private val testVibration = object : VibrationFeedback {
        override fun tick() { vibrationTicks++ }
        override fun confirm() { vibrationConfirms++ }
    }

    private val testUiState = mutableStateOf<BrowserUiState?>(null)

    private fun render(
        tabs: List<Tab>,
        activeTabId: String,
        dockWidth: Dp = 360.dp,
    ) {
        events.clear()
        vibrationTicks = 0
        vibrationConfirms = 0
        testUiState.value =
            BrowserUiState(
                tabs = tabs,
                activeTabId = activeTabId,
                currentUrl = tabs.first { it.id == activeTabId }.url,
                pageTitle = tabs.first { it.id == activeTabId }.title,
            )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val state = testUiState.value ?: return@setContent
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier =
                            Modifier
                                .width(dockWidth)
                                .height(54.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = { events += it },
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun dragInactiveTabAcrossActiveTab_reordersToFirstSlot() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")

        dragTabToLeftEdge(tag = "tab_tab4")

        assertThat(lastReorderIds()).containsExactly("tab4", "tab1", "tab2", "tab3").inOrder()
    }

    @Test
    fun dragActiveTabAcrossInactiveTabs_reordersToFirstSlot() {
        render(tabs = fourTabs(activeIndex = 2), activeTabId = "tab3")

        dragTabToLeftEdge(tag = "tab_tab3")

        assertThat(lastReorderIds()).containsExactly("tab3", "tab1", "tab2", "tab4").inOrder()
    }

    @Test
    fun dragActiveTabAcrossInactiveTabAtSlotZero_staysVisibleAtLeftEdge() {
        val tabs = (1..8).map { index ->
            Tab(
                id = "tab$index",
                url = "https://$index.example",
                title = "Tab $index",
                isActive = index == 2,
            )
        }
        render(tabs = tabs, activeTabId = "tab2", dockWidth = 346.dp)

        val tag = "tab_tab2"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val dockLeft = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.left
        val visibleSamples = mutableListOf<Boolean>()
        val draggedBoundsSamples = mutableListOf<String>()
        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        repeat(24) { step ->
            val fraction = (step + 1f) / 24f
            val x = start.x + (-40f - start.x) * fraction
            composeRule.onRoot().performTouchInput { moveTo(Offset(x, start.y)) }
            composeRule.mainClock.advanceTimeBy(16)
            val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            visibleSamples += bounds.right > dockLeft
            draggedBoundsSamples += bounds.toString()
        }

        val draggedBounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Active tab left the dock during drag: $visibleSamples $draggedBoundsSamples", visibleSamples.all { it })
        assertThat(draggedBounds.right).isGreaterThan(dockLeft)

        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertThat(lastReorderIds()).containsExactly(
            "tab2", "tab1", "tab3", "tab4", "tab5", "tab6", "tab7", "tab8",
        ).inOrder()
    }

    @Test
    fun dragTabPastLeftDockEdge_doesNotJumpBackAndKeepsReorder() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")

        val tag = "tab_tab4"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val positions = mutableListOf<Float>()

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Cross into slot zero, reach the dock edge, then keep moving beyond it.
        for (x in listOf(175f, 145f, 115f, 85f, 55f, 25f, 5f, -15f, -35f)) {
            composeRule.onRoot().performTouchInput { moveTo(Offset(x, start.y)) }
            composeRule.mainClock.advanceTimeBy(32)
            positions += composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
        }

        // Once the item reaches the left boundary, further leftward pointer motion must not
        // translate it back into the dock. Allow a few pixels for placement animation/layout.
        assertTrue("Dragged tab moved right during leftward motion: $positions", positions.zipWithNext().all { (previous, next) -> next <= previous + 12f })

        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertThat(lastReorderIds()).containsExactly("tab4", "tab1", "tab2", "tab3").inOrder()
    }

    @Test
    fun dragActiveTabPastLeftEdge_whenStripStartsScrolled_doesNotJumpBack() {
        val tabs = (1..8).map { index ->
            Tab(
                id = "tab$index",
                url = "https://$index.example",
                title = "Tab $index",
                isActive = index == 7,
            )
        }
        render(tabs = tabs, activeTabId = "tab7", dockWidth = 260.dp)

        val tag = "tab_tab7"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val positions = mutableListOf<Float>()
        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        val steps = 24
        repeat(steps) { step ->
            val fraction = (step + 1f) / steps
            val x = start.x + (4f - start.x) * fraction
            composeRule.onRoot().performTouchInput { moveTo(Offset(x, start.y)) }
            composeRule.mainClock.advanceTimeBy(16)
            positions += composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
        }
        composeRule.mainClock.advanceTimeBy(700)

        val dockLeft = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.left
        val afterEdgeHold = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertThat(afterEdgeHold.right).isGreaterThan(dockLeft)
        composeRule.onRoot().performTouchInput { moveTo(Offset(-20f, start.y)) }
        composeRule.mainClock.advanceTimeBy(32)
        val afterMovingPastEdge = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertThat(afterMovingPastEdge.right).isGreaterThan(dockLeft)
        composeRule.onRoot().performTouchInput { moveTo(Offset(-40f, start.y)) }
        composeRule.mainClock.advanceTimeBy(32)
        val fartherPastEdge = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertThat(fartherPastEdge.right).isGreaterThan(dockLeft)

        assertThat(afterMovingPastEdge.left).isAtMost(afterEdgeHold.left + 12f)
        assertThat(fartherPastEdge.left).isAtMost(afterMovingPastEdge.left + 12f)
        assertTrue(
            "Dragged tab oscillated at the left edge: $positions",
            positions.takeLast(8).zipWithNext().all { (previous, next) -> next <= previous + 20f },
        )

        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertThat(lastReorderIds()).containsExactly(
            "tab7", "tab1", "tab2", "tab3", "tab4", "tab5", "tab6", "tab8",
        ).inOrder()
    }

    @Test
    fun tapInactiveTab_stillSelectsTab() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")

        composeRule.onNodeWithTag("tab_tab2", useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(100)

        val selected = events.filterIsInstance<BrowserUiEvent.SelectTab>().lastOrNull()
        assertThat(selected?.tabId).isEqualTo("tab2")
    }

    @Test
    fun longPressTab_opensContextMenu() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val start = composeRule.onNodeWithTag("tab_tab2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(300)

        composeRule.onNodeWithText("Close tab").assertExists()
    }

    @Test
    fun dragInactiveTabAboveDock_removesTabWithAnimationAndVibration() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val tag = "tab_tab2"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Drag upwards into space above dock (at xxhdpi, 36.dp is 108px; drag -180px)
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(start.x, start.y - 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }

        // While hovered in remove zone, close icon should appear
        composeRule.onNodeWithContentDescription("Remove Tab", useUnmergedTree = true).assertExists()

        // Release to confirm removal
        composeRule.onRoot().performTouchInput { up() }

        // Confirm vibration triggered upon release in remove zone
        assertThat(vibrationConfirms).isAtLeast(2)

        // Advance through removal animation
        composeRule.mainClock.advanceTimeBy(500)
        val closed = events.filterIsInstance<BrowserUiEvent.CloseTab>()
        assertThat(closed).hasSize(1)
        assertThat(closed.first().tabId).isEqualTo("tab2")
    }

    @Test
    fun dragActiveTabAboveDock_removesTabWithAnimationAndVibration() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val tag = "tab_tab1"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Drag active tab upwards into space above dock
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(start.x, start.y - 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }

        composeRule.onNodeWithContentDescription("Remove Tab", useUnmergedTree = true).assertExists()

        composeRule.onRoot().performTouchInput { up() }
        assertThat(vibrationConfirms).isAtLeast(2)

        composeRule.mainClock.advanceTimeBy(500)
        val closed = events.filterIsInstance<BrowserUiEvent.CloseTab>()
        assertThat(closed).hasSize(1)
        assertThat(closed.first().tabId).isEqualTo("tab1")
    }

    @Test
    fun dragTabAboveDock_andDragBackDown_doesNotRemoveTab() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val tag = "tab_tab2"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Drag upwards into remove zone
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(start.x, start.y - 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }
        composeRule.onNodeWithContentDescription("Remove Tab", useUnmergedTree = true).assertExists()

        // Drag back down into the dock
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(start.x, (start.y - 180f) + 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }
        composeRule.onNodeWithContentDescription("Remove Tab", useUnmergedTree = true).assertDoesNotExist()

        // Release inside dock
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500)

        // No tab closed
        assertThat(events.filterIsInstance<BrowserUiEvent.CloseTab>()).isEmpty()
    }

    @Test
    fun dragTabAboveDock_whileReordered_preservesReorderOnRemoval() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val tag = "tab_tab4"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Move horizontally past left neighbor to trigger swap
        for (x in listOf(175f, 145f, 115f)) {
            composeRule.onRoot().performTouchInput { moveTo(Offset(x, start.y)) }
            composeRule.mainClock.advanceTimeBy(32)
        }

        // Drag up into remove zone
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(115f, start.y - 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }

        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500)

        val closed = events.filterIsInstance<BrowserUiEvent.CloseTab>()
        assertThat(closed).hasSize(1)
        assertThat(closed.first().tabId).isEqualTo("tab4")

        // Reorder event excluding the removed tab was dispatched
        val reordered = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().lastOrNull()
        assertThat(reordered?.orderedTabs?.map { it.id }).doesNotContain("tab4")
    }

    @Test
    fun dragTabAboveDock_removesTabImmediatelyFromDock_noGhostIconRemains() {
        render(tabs = fourTabs(activeIndex = 0), activeTabId = "tab1")
        val tag = "tab_tab2"
        val start = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Drag upwards into remove zone
        repeat(12) { step ->
            val fraction = (step + 1f) / 12f
            composeRule.onRoot().performTouchInput { moveTo(Offset(start.x, start.y - 180f * fraction)) }
            composeRule.mainClock.advanceTimeBy(16)
        }

        // Release to confirm removal
        composeRule.onRoot().performTouchInput { up() }

        // Immediately after release, the tab must not linger in the dock items
        composeRule.onNodeWithTag("tab_tab2").assertDoesNotExist()

        // After removal animation completes, event is dispatched and no ghost icon appears
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("tab_tab2").assertDoesNotExist()

        val closed = events.filterIsInstance<BrowserUiEvent.CloseTab>()
        assertThat(closed).hasSize(1)
        assertThat(closed.first().tabId).isEqualTo("tab2")
    }

    @Test
    fun dragTab_whenStripScrolledAwayFromActiveTab_doesNotAutoScrollBackToActiveTab() {
        val tabs = (1..8).map { index ->
            Tab(
                id = "tab$index",
                url = "https://$index.example",
                title = "Tab $index",
                isActive = index == 1,
            )
        }
        render(tabs = tabs, activeTabId = "tab1", dockWidth = 260.dp)

        // Initially tab1 is active and visible
        val initialTab1Bounds = composeRule.onNodeWithTag("tab_tab1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertThat(initialTab1Bounds.right).isGreaterThan(0f)

        // Scroll strip forward so tab1 scrolls out of the viewport
        repeat(3) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.mainClock.advanceTimeBy(300)
        }

        // Tab1 should now be scrolled off-screen to the left
        val scrolledTab1Node = findNode("tab_tab1")
        assertTrue(
            "Expected tab1 to be scrolled off-screen: ${scrolledTab1Node?.boundsInRoot}",
            scrolledTab1Node == null || scrolledTab1Node.boundsInRoot.right <= 0f,
        )

        // Find a visible tab near the end (e.g. tab7 or tab6)
        val visibleTag = if (findNode("tab_tab7") != null) {
            "tab_tab7"
        } else {
            "tab_tab6"
        }
        val start = composeRule.onNodeWithTag(visibleTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center

        // Long press to start dragging the inactive tab
        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        // Drag slightly to trigger reorder/drag state
        composeRule.onRoot().performTouchInput { moveTo(Offset(start.x - 40f, start.y)) }
        composeRule.mainClock.advanceTimeBy(100)

        // Release the dragged tab and let it settle
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)

        // Settling must NOT trigger scroll back to center tab1
        val afterSettleTab1Node = findNode("tab_tab1")
        assertTrue(
            "Tab1 auto-scrolled back into view after drag settle: ${afterSettleTab1Node?.boundsInRoot}",
            afterSettleTab1Node == null || afterSettleTab1Node.boundsInRoot.right <= 0f,
        )
    }

    @Test
    fun selectInactiveTab_smoothlyScrollsToCenterActiveTab() {
        val tabs = (1..8).map { index ->
            Tab(
                id = "tab$index",
                url = "https://$index.example",
                title = "Tab $index",
                isActive = index == 1,
            )
        }
        render(tabs = tabs, activeTabId = "tab1", dockWidth = 260.dp)

        // Tab8 is far to the right and off-screen initially
        val initialTab8Node = findNode("tab_tab8")
        val dockRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        assertTrue(
            "Tab8 should not be visible initially: ${initialTab8Node?.boundsInRoot}",
            initialTab8Node == null || initialTab8Node.boundsInRoot.left >= dockRight,
        )

        // Select tab8 by updating UI state (simulating tab switch)
        composeRule.mainClock.autoAdvance = true
        testUiState.value = BrowserUiState(
            tabs = tabs.map { it.copy(isActive = it.id == "tab8") },
            activeTabId = "tab8",
            currentUrl = "https://8.example",
            pageTitle = "Tab 8",
        )
        composeRule.waitForIdle()

        // Tab8 should now be centered and visible in the dock
        val centeredTab8Node = composeRule.onNodeWithTag("tab_tab8", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertThat(centeredTab8Node.boundsInRoot.left).isGreaterThan(0f)
        assertThat(centeredTab8Node.boundsInRoot.right).isAtMost(dockRight)
    }

    @Test
    fun reselectActiveTabFromOverviewGrid_smoothlyScrollsToCenterActiveTab() {
        val tabs = (1..8).map {
            Tab(id = "tab$it", url = "https://$it.example", title = "Tab $it", isActive = it == 8)
        }
        render(tabs = tabs, activeTabId = "tab8", dockWidth = 260.dp)

        // Advance initial render clock so tab8 is initially centered
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        val dockRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right

        // User manually scrolls the tab strip so tab8 moves out of view to the right
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performTouchInput {
            swipeRight(startX = 20f, endX = 240f)
        }
        composeRule.mainClock.advanceTimeBy(300)

        // Overview grid opens (activeOverlay = TAB_GRID)
        testUiState.value = testUiState.value?.copy(activeOverlay = OverlayType.TAB_GRID)
        composeRule.mainClock.advanceTimeBy(100)

        // User re-selects the already-active tab8 from overview grid (activeOverlay becomes NONE)
        composeRule.mainClock.autoAdvance = true
        testUiState.value = testUiState.value?.copy(activeOverlay = OverlayType.NONE)
        composeRule.waitForIdle()

        // Tab8 should now be centered and visible in the dock
        val centeredTab8Node = composeRule.onNodeWithTag("tab_tab8", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertThat(centeredTab8Node.boundsInRoot.left).isGreaterThan(0f)
        assertThat(centeredTab8Node.boundsInRoot.right).isAtMost(dockRight)
    }

    private fun findNode(tag: String): androidx.compose.ui.semantics.SemanticsNode? =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()

    private fun dragTabToLeftEdge(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val start = bounds.center

        composeRule.onRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(700)

        val targetX = 4f
        val steps = 24
        repeat(steps) { step ->
            val fraction = (step + 1f) / steps
            val x = start.x + (targetX - start.x) * fraction
            composeRule.onRoot().performTouchInput { moveTo(Offset(x, start.y)) }
            composeRule.mainClock.advanceTimeBy(16)
        }

        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    private fun lastReorderIds(): List<String> =
        events.filterIsInstance<BrowserUiEvent.ReorderTabs>()
            .lastOrNull()
            ?.orderedTabs
            ?.map { it.id }
            ?: emptyList()

    private fun fourTabs(activeIndex: Int): List<Tab> =
        listOf(
            Tab(id = "tab1", url = "https://a.example", title = "Alpha", isActive = activeIndex == 0),
            Tab(id = "tab2", url = "https://b.example", title = "Beta", isActive = activeIndex == 1),
            Tab(id = "tab3", url = "https://c.example", title = "Gamma", isActive = activeIndex == 2),
            Tab(id = "tab4", url = "https://d.example", title = "Delta", isActive = activeIndex == 3),
        )
}
