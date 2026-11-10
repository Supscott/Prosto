package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.overlays.WorkspaceSwitcherState
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dock swipe-up drives the workspace switcher: the screen follows the finger and the release
 * either settles into the carousel or springs back (closing the overview again).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BottomBarSwipeUpTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private lateinit var switcher: WorkspaceSwitcherState

    private val sampleTabs = listOf(
        Tab(id = "tab1", url = "https://example.com", title = "Example", isActive = true),
        Tab(id = "tab2", url = "https://duckduckgo.com", title = "DDG", isActive = false),
    )

    @Before
    fun setUp() {
        events.clear()
        // Root of a 411x891dp xxhdpi screen; a full drag is 32% of its height (~285dp).
        // Fling threshold: 300dp/s at 3x density.
        switcher = WorkspaceSwitcherState(flingVelocityPxPerSec = 900f).apply { rootSize = IntSize(1233, 2673) }
    }

    private fun setupBottomBar() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    UnifiedBottomBar(
                        uiState = BrowserUiState(
                            tabs = sampleTabs,
                            activeTabId = "tab1",
                            currentUrl = "https://example.com",
                            pageTitle = "Example",
                        ),
                        onEvent = { events += it },
                        workspaceSwitcher = switcher,
                    )
                }
            }
        }
    }

    private fun opened() = events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()
    private fun closed() = events.filterIsInstance<BrowserUiEvent.CloseWorkspacesOverview>()

    @Test
    fun fastFlickUp_opensWorkspacesCarousel() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            swipeUp(startY = bottom, endY = bottom - 150.dp.toPx(), durationMillis = 100)
        }
        composeRule.waitForIdle()

        assertThat(opened()).hasSize(1)
        assertThat(closed()).isEmpty()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).isEmpty()
        assertThat(switcher.progress.value).isEqualTo(1f)
    }

    @Test
    fun dragFollowsFinger_beforeRelease() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -100.dp.toPx()), delayMillis = 200)
        }
        composeRule.waitForIdle()

        assertThat(opened()).hasSize(1)
        assertThat(switcher.isDragging).isTrue()
        assertThat(switcher.progress.value).isWithin(0.08f).of(100f / 285f)

        composeRule.onNodeWithTag("dock_capsule").performTouchInput { up() }
        composeRule.waitForIdle()
        assertThat(switcher.isDragging).isFalse()
    }

    @Test
    fun slowDragPastThreshold_settlesOpen() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(0f, -20.dp.toPx()), delayMillis = 100) }
            advanceEventTime(200)
            up()
        }
        composeRule.waitForIdle()

        assertThat(opened()).hasSize(1)
        assertThat(closed()).isEmpty()
        assertThat(switcher.progress.value).isEqualTo(1f)
    }

    @Test
    fun shortSlowDrag_springsBackAndClosesOverview() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -30.dp.toPx()), delayMillis = 300)
            advanceEventTime(200)
            up()
        }
        composeRule.waitForIdle()

        assertThat(opened()).hasSize(1)
        assertThat(closed()).hasSize(1)
        assertThat(switcher.progress.value).isEqualTo(0f)
    }

    @Test
    fun dragUpThenBackDown_springsBackAndClosesOverview() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -120.dp.toPx()), delayMillis = 250)
            moveBy(Offset(0f, 120.dp.toPx()), delayMillis = 150)
            up()
        }
        composeRule.waitForIdle()

        assertThat(closed()).hasSize(1)
        assertThat(switcher.progress.value).isEqualTo(0f)
    }

    @Test
    fun downwardDrag_doesNotTouchWorkspaces() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, 40.dp.toPx()), delayMillis = 200)
            up()
        }
        composeRule.waitForIdle()

        assertThat(opened()).isEmpty()
        assertThat(closed()).isEmpty()
        assertThat(switcher.progress.value).isEqualTo(0f)
    }

    @Test
    fun searchButton_opensSearchInNewTab() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_search_button").performClick()

        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        assertThat(searchEvents).hasSize(1)
        assertThat(searchEvents.first().openInNewTab).isTrue()
        assertThat(opened()).isEmpty()
    }

    @Test
    fun menuButton_togglesMenu() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_menu_button").performClick()

        assertThat(events.filterIsInstance<BrowserUiEvent.ToggleMenu>()).hasSize(1)
    }

    @Test
    fun dockSwipeUpGesture_reportsUpwardTravelAndRelease() {
        var lastTravel = 0f
        var started = 0
        var endedVelocity: Float? = null
        var endedCancelled: Boolean? = null

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("custom_capsule")
                    .dockSwipeUpGesture(
                        onDragStart = { started++ },
                        onDrag = { lastTravel = it },
                        onDragEnd = { velocity, cancelled ->
                            endedVelocity = velocity
                            endedCancelled = cancelled
                        },
                    )
            )
        }

        composeRule.onNodeWithTag("custom_capsule").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, -10.dp.toPx()), delayMillis = 16) }
        }
        assertThat(started).isEqualTo(1)
        assertThat(lastTravel).isGreaterThan(0f)
        assertThat(endedVelocity).isNull()

        composeRule.onNodeWithTag("custom_capsule").performTouchInput { up() }
        composeRule.waitForIdle()

        assertThat(endedCancelled).isFalse()
        assertThat(endedVelocity!!).isGreaterThan(0f)
    }

    @Test
    fun sequentialGestures_resetCleanlyBetweenInteractions() {
        setupBottomBar()

        // 1. Short drag springs back
        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -30.dp.toPx()), delayMillis = 300)
            advanceEventTime(200)
            up()
        }
        composeRule.waitForIdle()
        assertThat(switcher.progress.value).isEqualTo(0f)
        assertThat(closed()).hasSize(1)

        // 2. Fast flick opens the carousel
        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            swipeUp(startY = bottom, endY = bottom - 150.dp.toPx(), durationMillis = 100)
        }
        composeRule.waitForIdle()
        assertThat(switcher.progress.value).isEqualTo(1f)
        assertThat(opened()).hasSize(2)
        assertThat(closed()).hasSize(1)
    }
}
