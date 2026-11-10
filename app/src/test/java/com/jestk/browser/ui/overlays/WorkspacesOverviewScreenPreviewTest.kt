package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspacesOverviewScreenPreviewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleTabs = listOf(
        Tab(id = "p1", url = "https://personal.com", title = "Personal Tab 1", workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true),
        Tab(id = "p2", url = "https://news.com", title = "Personal Tab 2", workspaceId = Workspace.DEFAULT_PERSONAL_ID),
        Tab(id = "w1", url = "https://work.com", title = "Work Tab 1", workspaceId = Workspace.DEFAULT_WORK_ID, isActive = true),
    )

    private val sampleWorkspaces = listOf(
        Workspace.DEFAULT_PERSONAL,
        Workspace.DEFAULT_WORK,
    )

    @Test
    fun overview_rendersWorkspaceCardsAndAddButton() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = listOf(Workspace.DEFAULT_PERSONAL),
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        // Must display screen container and close button
        composeTestRule.onNodeWithTag("workspaces_overview_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspaces_overview_close_button").assertIsDisplayed()

        // Must display Personal workspace card
        composeTestRule.onNodeWithTag("workspace_card_${Workspace.DEFAULT_PERSONAL_ID}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Personal").assertIsDisplayed()

        // With 1 workspace, Add workspace card is page 1, immediately adjacent
        composeTestRule.onNodeWithTag("add_workspace_card").assertExists()
    }

    @Test
    fun overview_tappingCard_dispatchesSwitchWorkspaceAndCloseEvents() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        // Click on the Personal card (already active)
        composeTestRule.onNodeWithTag("workspace_card_${Workspace.DEFAULT_PERSONAL_ID}").performClick()
        composeTestRule.waitForIdle()
        assertThat(events).contains(BrowserUiEvent.CloseWorkspacesOverview)
    }

    @Test
    fun overview_clickingCloseButton_dispatchesCloseWorkspacesOverview() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("workspaces_overview_close_button").performClick()
        // Close is dispatched once the live app has expanded back to full screen.
        composeTestRule.waitForIdle()
        assertThat(events).containsExactly(BrowserUiEvent.CloseWorkspacesOverview)
    }

    @Test
    fun overview_swipeDown_closesOverviewAndSelectsCenteredWorkspace() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("workspaces_carousel_box").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f), delayMillis = 100)
            up()
        }
        composeTestRule.waitForIdle()
        assertThat(events).contains(BrowserUiEvent.CloseWorkspacesOverview)
    }

    @Test
    fun overview_tappingOtherWorkspaceCard_switchesAfterExpanding() {
        val events = mutableListOf<BrowserUiEvent>()
        val switcher = WorkspaceSwitcherState(flingVelocityPxPerSec = 900f)
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = { events.add(it) },
                    switcher = switcher,
                )
            }
        }
        composeTestRule.waitForIdle()
        assertThat(switcher.progress.value).isEqualTo(1f)

        // The neighbour card peeks in from the right edge; tap its visible left part.
        composeTestRule.onNodeWithTag("workspace_card_${Workspace.DEFAULT_WORK_ID}").performTouchInput {
            click(Offset(3f, centerY))
        }
        composeTestRule.waitForIdle()

        assertThat(events).contains(BrowserUiEvent.SwitchWorkspace(Workspace.DEFAULT_WORK_ID))
        // The live app is back at full screen and opaque underneath the expanded snapshot.
        assertThat(switcher.progress.value).isEqualTo(0f)
        assertThat(switcher.liveAlphaOverride).isNull()
    }

    @Test
    fun overview_smallSwipeDown_doesNotCloseOverview() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("workspaces_carousel_box").performTouchInput {
            down(center)
            moveBy(Offset(0f, 20f), delayMillis = 200)
            up()
        }

        assertThat(events).doesNotContain(BrowserUiEvent.CloseWorkspacesOverview)
    }

    @Test
    fun overview_clickingAddCard_opensNewWorkspaceSheet() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = listOf(Workspace.DEFAULT_PERSONAL),
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("add_workspace_card").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("workspace_name_input").assertExists()
        composeTestRule.onNodeWithTag("workspace_save_button").assertExists()
    }

    @Test
    fun overview_clickingCardOptions_opensEditWorkspaceSheet() {
        val events = mutableListOf<BrowserUiEvent>()
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.onNodeWithTag("workspace_options_${Workspace.DEFAULT_PERSONAL_ID}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("workspace_name_input").assertExists()
        composeTestRule.onNodeWithTag("workspace_save_button").assertExists()
        composeTestRule.onNodeWithTag("workspace_delete_button").assertExists()
    }

    @Test
    fun overview_rendersLightTheme() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs,
                        allTabs = sampleTabs,
                    ),
                    onEvent = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("workspaces_overview_screen").assertIsDisplayed()
    }

    @Test
    fun overview_rendersDarkTheme() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = sampleWorkspaces,
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs,
                        allTabs = sampleTabs,
                    ),
                    onEvent = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("workspaces_overview_screen").assertIsDisplayed()
    }

    @Test
    fun overview_displaysActiveWebTabTitleAndIndexCounter() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = listOf(Workspace.DEFAULT_PERSONAL),
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = sampleTabs.filter { it.workspaceId == Workspace.DEFAULT_PERSONAL_ID },
                        allTabs = sampleTabs,
                    ),
                    onEvent = {},
                )
            }
        }

        // Active tab is p1 ("Personal Tab 1")
        composeTestRule.onNodeWithText("Personal Tab 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 1 of 2").assertIsDisplayed()
    }

    @Test
    fun overview_displaysHomeTab_whenActiveTabIsHome() {
        val homeTabs = listOf(
            Tab(id = "h1", url = "", title = "Home", workspaceId = Workspace.DEFAULT_PERSONAL_ID, isActive = true)
        )
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = listOf(Workspace.DEFAULT_PERSONAL),
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = homeTabs,
                        allTabs = homeTabs,
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 1 of 1").assertIsDisplayed()
    }

    @Test
    fun overview_displaysNoTabs_whenWorkspaceHasNoTabs() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                WorkspacesOverviewScreen(
                    uiState = BrowserUiState(
                        workspaces = listOf(Workspace.DEFAULT_PERSONAL),
                        activeWorkspaceId = Workspace.DEFAULT_PERSONAL_ID,
                        tabs = emptyList(),
                        allTabs = emptyList(),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No open tabs").assertIsDisplayed()
    }
}
