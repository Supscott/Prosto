package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
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
class TabOverviewGridReorderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ==========================================
    // 1. Pure Algorithm Unit Tests
    // ==========================================

    @Test
    fun reorder_standaloneTabs_swapsPositions() {
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two"),
            Tab(id = "3", url = "https://three.com", title = "Three"),
        )
        val folders = emptyList<TabFolder>()

        val (reorderedTabs, _) = reorderOverviewGrid(
            tabs = tabs,
            folders = folders,
            draggedKey = "tab_1",
            targetKey = "tab_3",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("2", "3", "1").inOrder()
    }

    @Test
    fun reorder_collapsedFolder_movesEntireFolderGroup() {
        val folder = TabFolder(id = "f1", title = "Folder", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two", folderId = "f1"),
            Tab(id = "3", url = "https://three.com", title = "Three", folderId = "f1"),
            Tab(id = "4", url = "https://four.com", title = "Four"),
        )

        // Drag folder_f1 before tab_1
        val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "folder_f1",
            targetKey = "tab_1",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("2", "3", "1", "4").inOrder()
        assertThat(reorderedFolders.first().id).isEqualTo("f1")
    }

    @Test
    fun reorder_expandedFolderHeader_movesEntireFolderGroup() {
        val folder = TabFolder(id = "f1", title = "Folder", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two", folderId = "f1"),
            Tab(id = "3", url = "https://three.com", title = "Three", folderId = "f1"),
            Tab(id = "4", url = "https://four.com", title = "Four"),
        )

        // Drag folder_header_f1 after tab_4
        val (reorderedTabs, _) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "folder_header_f1",
            targetKey = "tab_4",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("1", "4", "2", "3").inOrder()
    }

    @Test
    fun reorder_insideFolder_reordersMemberTabsOnly() {
        val folder = TabFolder(id = "f1", title = "Folder", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two", folderId = "f1"),
            Tab(id = "3", url = "https://three.com", title = "Three", folderId = "f1"),
            Tab(id = "4", url = "https://four.com", title = "Four", folderId = "f1"),
            Tab(id = "5", url = "https://five.com", title = "Five"),
        )

        // Drag tab_2 after tab_4 inside folder f1
        val (reorderedTabs, _) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "tab_2",
            targetKey = "tab_4",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("1", "3", "4", "2", "5").inOrder()
        assertThat(reorderedTabs.filter { it.folderId == "f1" }.map { it.id }).containsExactly("3", "4", "2")
    }

    @Test
    fun reorder_dragTabOutOfFolder_ungroupsToTargetPosition() {
        val folder = TabFolder(id = "f1", title = "Folder", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two", folderId = "f1"),
            Tab(id = "3", url = "https://three.com", title = "Three", folderId = "f1"),
            Tab(id = "4", url = "https://four.com", title = "Four"),
        )

        // Drag tab_2 out of folder before tab_1
        val (reorderedTabs, _) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "tab_2",
            targetKey = "tab_1",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("2", "1", "3", "4").inOrder()
        assertThat(reorderedTabs.first { it.id == "2" }.folderId).isNull()
        assertThat(reorderedTabs.first { it.id == "3" }.folderId).isEqualTo("f1")
    }

    @Test
    fun reorder_dragStandaloneTabIntoFolder_addsTabToFolder() {
        val folder = TabFolder(id = "f1", title = "Folder", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One"),
            Tab(id = "2", url = "https://two.com", title = "Two", folderId = "f1"),
            Tab(id = "3", url = "https://three.com", title = "Three", folderId = "f1"),
        )

        // Drag standalone tab_1 into folder f1 before tab_3
        val (reorderedTabs, _) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "tab_1",
            targetKey = "tab_3",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("2", "1", "3").inOrder()
        assertThat(reorderedTabs.all { it.folderId == "f1" }).isTrue()
    }

    @Test
    fun reorder_dragLastTabOutOfFolder_prunesEmptyFolder() {
        val folder = TabFolder(id = "f1", title = "Solo", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One", folderId = "f1"),
            Tab(id = "2", url = "https://two.com", title = "Two"),
        )

        // Drag sole member tab_1 out after tab_2
        val (reorderedTabs, reorderedFolders) = reorderOverviewGrid(
            tabs = tabs,
            folders = listOf(folder),
            draggedKey = "tab_1",
            targetKey = "tab_2",
        )

        assertThat(reorderedTabs.map { it.id }).containsExactly("2", "1").inOrder()
        assertThat(reorderedTabs.first { it.id == "1" }.folderId).isNull()
        assertThat(reorderedFolders).isEmpty()
    }

    // ==========================================
    // 2. Compose Robolectric Interaction Tests
    // ==========================================

    @Test
    fun folderCard_stationaryLongPress_opensEditFolderSheet() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val folderCenter = composeTestRule.onNodeWithTag("folder_card_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        // Long press without exceeding touch slop
        gridNode.performTouchInput {
            down(folderCenter)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val editEvent = events.filterIsInstance<BrowserUiEvent.OpenEditFolderSheet>().firstOrNull()
        assertThat(editEvent).isNotNull()
        assertThat(editEvent?.folderId).isEqualTo("f1")
    }

    @Test
    fun folderHeader_stationaryLongPress_opensEditFolderSheet() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val headerCenter = composeTestRule.onNodeWithTag("folder_in_grid_header_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        gridNode.performTouchInput {
            down(headerCenter)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val editEvent = events.filterIsInstance<BrowserUiEvent.OpenEditFolderSheet>().firstOrNull()
        assertThat(editEvent).isNotNull()
        assertThat(editEvent?.folderId).isEqualTo("f1")
    }

    @Test
    fun folderHeader_pencilEditButton_opensEditFolderSheet() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "1", url = "https://one.com", title = "One", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        // Click pencil icon
        composeTestRule.onNodeWithTag("folder_header_edit_f1", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        val editEvent = events.filterIsInstance<BrowserUiEvent.OpenEditFolderSheet>().firstOrNull()
        assertThat(editEvent).isNotNull()
        assertThat(editEvent?.folderId).isEqualTo("f1")
    }

    @Test
    fun dragTabPastTargetFarEdge_reordersInsteadOfGrouping() {
        val events = mutableListOf<BrowserUiEvent>()
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = null),
        )

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
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val t1Node = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true).fetchSemanticsNode()
        val t2Node = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true).fetchSemanticsNode()
        val t1Center = t1Node.boundsInRoot.center - gridBounds.topLeft
        // Push past t2's far edge: that is what turns grouping into a move
        val pastT2FarEdge = (t2Node.boundsInRoot.center - gridBounds.topLeft) + Offset(20f, 0f)

        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)

        gridNode.performTouchInput {
            moveTo(t1Center + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(pastT2FarEdge)
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        // Verify ReorderTabs was emitted with new order and NO folder creation occurred
        val createFolderEvent = events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>().firstOrNull()
        assertThat(createFolderEvent).isNull()

        val reorderEvent = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorderEvent).isNotNull()
        assertThat(reorderEvent?.orderedTabs?.map { it.id }).containsExactly("t2", "t1").inOrder()
    }

    @Test
    fun dragTabOntoTabNearZone_createsFolder() {
        val events = mutableListOf<BrowserUiEvent>()
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = null),
        )

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
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val t1Center = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t2Center = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)

        // Settle inside t2's near zone, short of its far edge
        gridNode.performTouchInput {
            moveTo(t1Center + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(t2Center - Offset(20f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(200)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        // Verify folder creation triggered
        val createFolderEvent = events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>().firstOrNull()
        assertThat(createFolderEvent).isNotNull()
        assertThat(createFolderEvent?.tabId1).isEqualTo("t1")
        assertThat(createFolderEvent?.tabId2).isEqualTo("t2")
    }

    @Test
    fun dragFolderCard_exceedingTouchSlop_reordersFolder() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val folderCenter = composeTestRule.onNodeWithTag("folder_card_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t1Bounds = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridBounds.topLeft)

        // Drag folder_f1 past t1's far (left) edge so the gesture is a move
        gridNode.performTouchInput {
            down(folderCenter)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            moveTo(folderCenter + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(Offset(t1Bounds.left + 10f, t1Bounds.center.y))
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val reorderEvent = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorderEvent).isNotNull()
        assertThat(reorderEvent?.orderedTabs?.map { it.id }).containsExactly("t2", "t1").inOrder()
    }

    @Test
    fun dragFolderHeader_reordersFolder() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val headerCenter = composeTestRule.onNodeWithTag("folder_in_grid_header_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t1Bounds = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.translate(-gridBounds.topLeft)

        // Drag the folder header past t1's far (top) edge so the gesture is a move
        gridNode.performTouchInput {
            down(headerCenter)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            moveTo(headerCenter + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(Offset(t1Bounds.center.x, t1Bounds.top + 10f))
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val reorderEvent = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorderEvent).isNotNull()
        assertThat(reorderEvent?.orderedTabs?.map { it.id }).containsExactly("t2", "t1").inOrder()
    }

    @Test
    fun dragTabOutOfFolder_reordersAndUngroups() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
            Tab(id = "t3", url = "https://three.com", title = "Tab Three", folderId = null),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val t1Center = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t3Center = composeTestRule.onNodeWithTag("tab_card_t3", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        // Drag t1 out onto t3, past t3's far (bottom) edge
        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            moveTo(t1Center + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(t3Center + Offset(0f, 12f))
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val removeEvent = events.filterIsInstance<BrowserUiEvent.RemoveTabFromFolder>().firstOrNull()
        assertThat(removeEvent).isNotNull()
        assertThat(removeEvent?.tabId).isEqualTo("t1")

        val reorderEvent = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorderEvent).isNotNull()
        val reorderedT1 = reorderEvent?.orderedTabs?.find { it.id == "t1" }
        assertThat(reorderedT1?.folderId).isNull()
    }

    @Test
    fun dragStandaloneTabIntoFolder_addsToFolder() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(folder), activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val t1Center = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t2Center = composeTestRule.onNodeWithTag("tab_card_t2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        // Drag standalone t1 into the open folder, keeping it inside t2's near zone
        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            moveTo(t1Center + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(t2Center - Offset(0f, 12f))
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val addEvent = events.filterIsInstance<BrowserUiEvent.AddTabToFolder>().firstOrNull()
        assertThat(addEvent).isNotNull()
        assertThat(addEvent?.tabId).isEqualTo("t1")
        assertThat(addEvent?.folderId).isEqualTo("f1")
    }

    @Test
    fun dragFolderOntoFolderNearZone_mergesFolders() {
        val events = mutableListOf<BrowserUiEvent>()
        val warm = TabFolder(id = "f1", title = "Warm", color = 0xFF2196F3.toInt())
        val work = TabFolder(id = "f2", title = "Work", color = 0xFFE91E63.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f2"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(tabs = tabs, folders = listOf(warm, work), activeTabId = "t1"),
                    onEvent = { events.add(it) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val f1Center = composeTestRule.onNodeWithTag("folder_card_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val f2Center = composeTestRule.onNodeWithTag("folder_card_f2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        // Settle inside f2's near zone: that means "merge", not "move"
        gridNode.performTouchInput {
            down(f1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        gridNode.performTouchInput {
            moveTo(f1Center + Offset(12f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            moveTo(f2Center - Offset(20f, 0f))
        }
        composeTestRule.mainClock.advanceTimeBy(32)
        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val reorderEvent = events.filterIsInstance<BrowserUiEvent.ReorderTabs>().firstOrNull()
        assertThat(reorderEvent).isNotNull()
        assertThat(reorderEvent?.orderedTabs?.map { it.id }).containsExactly("t2", "t1").inOrder()
        assertThat(reorderEvent?.orderedTabs?.map { it.folderId }).containsExactly("f2", "f2").inOrder()
        assertThat(reorderEvent?.orderedFolders?.map { it.id }).containsExactly("f2")
    }
}
