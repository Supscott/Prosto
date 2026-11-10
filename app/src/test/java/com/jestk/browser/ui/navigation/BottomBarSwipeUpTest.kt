package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BottomBarSwipeUpTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private var vibrationConfirmCalls = 0
    private val testVibration = object : VibrationFeedback {
        override fun tick() {}
        override fun confirm() {
            vibrationConfirmCalls++
        }
    }

    private val sampleTabs = listOf(
        Tab(id = "tab1", url = "https://example.com", title = "Example", isActive = true),
        Tab(id = "tab2", url = "https://duckduckgo.com", title = "DDG", isActive = false),
    )

    @Before
    fun setUp() {
        events.clear()
        vibrationConfirmCalls = 0
    }

    private fun setupBottomBar() {
        composeRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
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
                        )
                    }
                }
            }
        }
    }

    @Test
    fun fastFlickUp_triggersOpenSearchEvent() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            swipeUp()
        }

        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        assertThat(searchEvents).isNotEmpty()
        assertThat(searchEvents.first().openInNewTab).isTrue()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).isEmpty()
        assertThat(vibrationConfirmCalls).isEqualTo(0)
    }

    @Test
    fun fastUpwardDragPastDistanceThreshold_triggersSearchNotWorkspaces() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80.dp.toPx()), delayMillis = 100)
            up()
        }

        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        val workspaceEvents = events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()
        assertThat(searchEvents).isNotEmpty()
        assertThat(workspaceEvents).isEmpty()
        assertThat(vibrationConfirmCalls).isEqualTo(0)
    }

    @Test
    fun upwardDragPastThreshold_triggersOpenWorkspacesOverview() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80.dp.toPx()), delayMillis = 300)
            up()
        }

        val workspaceEvents = events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()
        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        assertThat(workspaceEvents).isNotEmpty()
        assertThat(searchEvents).isEmpty()
        assertThat(vibrationConfirmCalls).isGreaterThan(0)
    }

    @Test
    fun upwardDragAndHold_triggersOpenWorkspacesOverview() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40.dp.toPx()), delayMillis = 50)
        }
        composeRule.mainClock.advanceTimeBy(250)

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            up()
        }

        val workspaceEvents = events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()
        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        assertThat(workspaceEvents).isNotEmpty()
        assertThat(searchEvents).isEmpty()
        assertThat(vibrationConfirmCalls).isGreaterThan(0)
    }

    @Test
    fun releaseBelowThreshold_triggersNeitherSearchNorWorkspaces() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -20.dp.toPx()), delayMillis = 300)
            up()
        }

        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).isEmpty()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).isEmpty()
        assertThat(vibrationConfirmCalls).isEqualTo(0)
    }

    @Test
    fun downwardDrag_triggersNeither() {
        setupBottomBar()

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, 40.dp.toPx()), delayMillis = 200)
            up()
        }

        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).isEmpty()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).isEmpty()
        assertThat(vibrationConfirmCalls).isEqualTo(0)
    }

    @Test
    fun dockLiftGestureModifier_reportsDampedDragOffsetAndSpringsBack() {
        var reportedOffset = 0f
        var workspacesTriggered = false
        var searchTriggered = false

        composeRule.setContent {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("custom_capsule")
                    .dockLiftGestureModifier(
                        density = density,
                        vibration = testVibration,
                        onDragOffset = { reportedOffset = it },
                        onSwipeUpSearch = { searchTriggered = true },
                        onLiftWorkspaces = { workspacesTriggered = true },
                    )
            )
        }

        composeRule.onNodeWithTag("custom_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40.dp.toPx()), delayMillis = 100)
        }

        assertThat(reportedOffset).isLessThan(0f)

        composeRule.onNodeWithTag("custom_capsule").performTouchInput {
            up()
        }

        composeRule.waitForIdle()
        assertThat(reportedOffset).isEqualTo(0f)
    }

    @Test
    fun sequentialGestures_resetCleanlyBetweenInteractions() {
        setupBottomBar()

        // 1. Release below threshold -> neither search nor workspaces
        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -20.dp.toPx()), delayMillis = 300)
            up()
        }
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).isEmpty()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).isEmpty()

        // 2. Fast flick up -> triggers Search
        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            swipeUp()
        }
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).hasSize(1)
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).isEmpty()

        events.clear()

        // 3. Upward drag past threshold -> triggers Workspaces
        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80.dp.toPx()), delayMillis = 300)
            up()
        }
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenWorkspacesOverview>()).hasSize(1)
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenSearch>()).isEmpty()
    }
}
