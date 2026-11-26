package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** End-to-end drag zones in the tabs overview: the ghost decides, not where the finger holds it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabOverviewDragZonesTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val tabs =
        listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = null),
        )

    @Test
    fun dragSecondOntoFirst_fingerHeldAtCorner_groupsWhenGhostCentreIsInNearHalf() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridTopLeft = gridNode.fetchSemanticsNode().boundsInRoot.topLeft
        val t1 = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft)
        val t2 = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft)

        // Grab tab 2 by its top-left corner; the ghost centre is then half a card from the finger.
        val grab = t2.topLeft + Offset(6f, 6f)
        // Ghost centre at 30% of tab 1's width from its right edge: inside the near half.
        val ghostCenter = Offset(t1.right - 0.3f * t1.width, t1.center.y)
        val finger = ghostCenter - (t2.center - grab)

        gridNode.performTouchInput { down(grab) }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput { moveTo(grab + Offset(-12f, 0f)) }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput { moveTo(finger) }
        composeTestRule.mainClock.advanceTimeBy(200)
        gridNode.performTouchInput { up() }
        composeTestRule.waitForIdle()

        val create = events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>().firstOrNull()
        assertThat(create).isNotNull()
        assertThat(create?.tabId1).isEqualTo("t2")
        assertThat(create?.tabId2).isEqualTo("t1")
        assertThat(events.filterIsInstance<BrowserUiEvent.ReorderTabs>()).isEmpty()
    }

    @Test
    fun dragSecondOntoFirst_ghostCentrePastCentre_movesInsteadOfGrouping() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridTopLeft = gridNode.fetchSemanticsNode().boundsInRoot.topLeft
        val t1 = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft)
        val t2Center = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft).center

        val target = Offset(t1.left + 0.2f * t1.width, t1.center.y)
        gridNode.performTouchInput { down(t2Center) }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput { moveTo(t2Center + Offset(-12f, 0f)) }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput { moveTo(target) }
        composeTestRule.mainClock.advanceTimeBy(200)
        gridNode.performTouchInput { up() }
        composeTestRule.waitForIdle()

        assertThat(events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>()).isEmpty()
        val reorder = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorder?.orderedTabs?.map { it.id }).containsExactly("t2", "t1").inOrder()
    }

    /**
     * PRO-199. Zone rules: the half the ghost enters a card from (along the drag direction) is the
     * grouping half, the far half reorders. After 2 -> 1 swapped the cards, tab 1 sits in slot 2 and
     * the ghost comes back from the left: its left half is the near half. Landing in the right half
     * (a fast move, no event in between) reorders back, so the original order is restored.
     */
    @Test
    fun dragSecondOntoFirst_swapThenBackIntoFarHalfOfSameCard_restoresOrder() {
        val events = mutableListOf<BrowserUiEvent>()
        val ctx = setUpTwoTabs(events)

        ctx.dragTo(ctx.t1.left + 0.2f * ctx.t1.width)
        // Tab 1 now sits in slot 2; tab 2's placeholder is in slot 1.
        ctx.dragTo(ctx.t2.left + 0.8f * ctx.t2.width, release = true)

        assertThat(events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>()).isEmpty()
        assertThat(events.filterIsInstance<BrowserUiEvent.ReorderTabs>()).isEmpty()
    }

    /** PRO-199, near-half variant: coming back onto the swapped card from the left offers grouping. */
    @Test
    fun dragSecondOntoFirst_swapThenBackIntoNearHalfOfSameCard_groups() {
        val events = mutableListOf<BrowserUiEvent>()
        val ctx = setUpTwoTabs(events)

        ctx.dragTo(ctx.t1.left + 0.2f * ctx.t1.width)
        ctx.dragTo(ctx.t2.left + 0.2f * ctx.t2.width, release = true)

        val create = events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>().firstOrNull()
        assertThat(create?.tabId1).isEqualTo("t2")
        assertThat(create?.tabId2).isEqualTo("t1")
    }

    private class TwoTabDrag(
        val rule: ComposeTestRule,
        val t1: Rect,
        val t2: Rect,
    ) {
        private var started = false

        /** Moves the finger (grabbed at the centre of tab 2, so the ghost centre follows it) to [x]. */
        fun dragTo(
            x: Float,
            release: Boolean = false,
        ) {
            val grid = rule.onNodeWithTag("overview_grid")
            if (!started) {
                started = true
                grid.performTouchInput { down(t2.center) }
                rule.mainClock.advanceTimeBy(700)
                grid.performTouchInput { moveTo(t2.center + Offset(-12f, 0f)) }
                rule.mainClock.advanceTimeBy(32)
            }
            grid.performTouchInput { moveTo(Offset(x, t1.center.y)) }
            rule.mainClock.advanceTimeBy(200)
            if (release) {
                grid.performTouchInput { up() }
                rule.waitForIdle()
            }
        }
    }

    private fun setUpTwoTabs(events: MutableList<BrowserUiEvent>): TwoTabDrag {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()
        val gridTopLeft = composeTestRule.onNodeWithTag("overview_grid").fetchSemanticsNode().boundsInRoot.topLeft
        val t1 = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft)
        val t2 = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridTopLeft)
        return TwoTabDrag(composeTestRule, t1, t2)
    }
}
