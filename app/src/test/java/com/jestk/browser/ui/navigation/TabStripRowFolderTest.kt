package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabStripRowFolderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testFrameClock = object : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            kotlinx.coroutines.delay(16)
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private val events = mutableListOf<BrowserUiEvent>()
    private val testVibration = object : VibrationFeedback {
        override fun tick() {}
        override fun confirm() {}
    }

    private val testUiState = mutableStateOf<BrowserUiState?>(null)

    private fun render(
        tabs: List<Tab>,
        folders: List<TabFolder>,
        activeTabId: String,
        dockWidth: Dp = 360.dp,
    ) {
        events.clear()
        val active = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
        testUiState.value = BrowserUiState(
            tabs = tabs,
            folders = folders,
            activeTabId = activeTabId,
            currentUrl = active.url,
            pageTitle = active.title,
        )
        composeRule.setContent {
            val state = testUiState.value ?: return@setContent
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                    Box(
                        modifier = Modifier
                            .width(dockWidth)
                            .height(50.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = { events.add(it) },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    // --- 1. ComputeDockItems Unit Tests ---

    @Test
    fun computeDockItems_standaloneTabs_preservesOrder() {
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Tab 2", url = "https://google.com"),
        )
        val items = computeDockItems(tabs, emptyList())

        assertThat(items).hasSize(2)
        assertThat(items[0]).isInstanceOf(DockItem.TabItem::class.java)
        assertThat(items[0].key).isEqualTo("tab1")
        assertThat(items[1].key).isEqualTo("tab2")
    }

    @Test
    fun computeDockItems_expandedFolder_insertsIndicatorBeforeMemberTabs() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://jira.com", folderId = "f1"),
            Tab(id = "tab4", title = "Tab 4", url = "https://news.com"),
        )
        val items = computeDockItems(tabs, listOf(folder))

        assertThat(items).hasSize(5)
        assertThat(items[0].key).isEqualTo("tab1")
        assertThat(items[1].key).isEqualTo("folder_f1")
        val folderItem = items[1] as DockItem.FolderItem
        assertThat(folderItem.hasVisibleTabs).isTrue()

        assertThat(items[2].key).isEqualTo("tab2")
        val tab2Item = items[2] as DockItem.TabItem
        assertThat(tab2Item.folder?.id).isEqualTo("f1")
        assertThat(tab2Item.isLastInFolder).isFalse()

        assertThat(items[3].key).isEqualTo("tab3")
        val tab3Item = items[3] as DockItem.TabItem
        assertThat(tab3Item.folder?.id).isEqualTo("f1")
        assertThat(tab3Item.isLastInFolder).isTrue()

        assertThat(items[4].key).isEqualTo("tab4")
    }

    @Test
    fun computeDockItems_collapsedFolder_omitsMemberTabsFromDock() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://jira.com", folderId = "f1"),
            Tab(id = "tab4", title = "Tab 4", url = "https://news.com"),
        )
        val items = computeDockItems(tabs, listOf(folder))

        assertThat(items).hasSize(3)
        assertThat(items[0].key).isEqualTo("tab1")
        assertThat(items[1].key).isEqualTo("folder_f1")
        val folderItem = items[1] as DockItem.FolderItem
        assertThat(folderItem.hasVisibleTabs).isFalse()
        assertThat(items[2].key).isEqualTo("tab4")
    }

    // --- 2. FolderDockIndicator Composable Tests ---

    @Test
    fun folderDockIndicator_untitledExpanded_rendersCircleAndArrowBack() {
        val folder = TabFolder(id = "f1", title = "", color = 0xFF10B981.toInt(), isCollapsed = false)
        var clicked = false

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderDockIndicator(
                    folder = folder,
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("folder_indicator_f1").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_arrow_f1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Collapse folder").assertIsDisplayed()

        composeRule.onNodeWithTag("folder_indicator_f1").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun folderDockIndicator_untitledCollapsed_rendersCircleAndArrowForward() {
        val folder = TabFolder(id = "f1", title = "", color = 0xFF10B981.toInt(), isCollapsed = true)

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderDockIndicator(
                    folder = folder,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder_indicator_f1").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_arrow_f1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Expand folder").assertIsDisplayed()
    }

    @Test
    fun folderDockIndicator_titledExpanded_rendersChipWithTitleAndArrow() {
        val folder = TabFolder(id = "f2", title = "Research", color = 0xFF8B5CF6.toInt(), isCollapsed = false)

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderDockIndicator(
                    folder = folder,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder_indicator_f2").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_title_f2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Research").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_arrow_f2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Collapse folder").assertIsDisplayed()
    }

    @Test
    fun folderDockIndicator_whenBlinking_rendersAndAnimatesAlpha() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderDockIndicator(
                    folder = folder,
                    onClick = {},
                    isBlinking = true,
                )
            }
        }

        composeRule.onNodeWithTag("folder_indicator_f1").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
    }

    // --- 3. TabStripRow Integration Tests ---

    @Test
    fun tabStripRow_rendersFolderIndicatorAndMemberTabsWhenExpanded() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Outside", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )

        render(tabs = tabs, folders = listOf(folder), activeTabId = "tab1")

        composeRule.onNodeWithTag("tab_tab1").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_indicator_f1").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_tab2").assertIsDisplayed()
    }

    @Test
    fun tabStripRow_hidesMemberTabsWhenFolderIsCollapsed() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Outside", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )

        render(tabs = tabs, folders = listOf(folder), activeTabId = "tab1")

        composeRule.onNodeWithTag("tab_tab1").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_indicator_f1").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_tab2").assertDoesNotExist()
    }

    @Test
    fun tabStripRow_collapsedFolder_doesNotDrawBottomLine() {
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val activity = controller.get()

        val folderColorInt = 0xFFFF0055.toInt()
        val folder = TabFolder(id = "f1", title = "Work", color = folderColorInt, isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Outside", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val state = BrowserUiState(
            tabs = tabs,
            folders = listOf(folder),
            activeTabId = "tab1",
            currentUrl = "https://example.com",
            pageTitle = "Outside",
        )

        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(50.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = {},
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (50 * density).toInt()
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        // Check the bottom 6dp region (where the 2.5dp bottom line is drawn at height - 3dp)
        val bottomRegionStartY = ((50 - 6) * density).toInt()
        var folderColorPixelCount = 0
        for (y in bottomRegionStartY until height) {
            for (x in 0 until width) {
                if (bitmap.getPixel(x, y) == folderColorInt) {
                    folderColorPixelCount++
                }
            }
        }
        assertThat(folderColorPixelCount).isEqualTo(0)
    }

    @Test
    fun tabStripRow_expandedFolderWithTabs_drawsBottomLine() {
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val activity = controller.get()

        val folderColorInt = 0xFFFF0055.toInt()
        val folder = TabFolder(id = "f1", title = "Work", color = folderColorInt, isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Outside", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val state = BrowserUiState(
            tabs = tabs,
            folders = listOf(folder),
            activeTabId = "tab1",
            currentUrl = "https://example.com",
            pageTitle = "Outside",
        )

        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(50.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = {},
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (50 * density).toInt()
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        // Check the bottom 6dp region (where the 2.5dp bottom line is drawn at height - 3dp)
        val bottomRegionStartY = ((50 - 6) * density).toInt()
        var folderColorPixelCount = 0
        for (y in bottomRegionStartY until height) {
            for (x in 0 until width) {
                if (bitmap.getPixel(x, y) == folderColorInt) {
                    folderColorPixelCount++
                }
            }
        }
        assertThat(folderColorPixelCount).isGreaterThan(0)
    }

    @Test
    fun tabStripRow_tappingFolderIndicator_dispatchesToggleFolderCollapse() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Outside", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )

        render(tabs = tabs, folders = listOf(folder), activeTabId = "tab1")

        composeRule.onNodeWithTag("folder_indicator_f1").performClick()
        val toggleEvent = events.filterIsInstance<BrowserUiEvent.ToggleFolderCollapse>().firstOrNull()
        assertThat(toggleEvent).isNotNull()
        assertThat(toggleEvent?.folderId).isEqualTo("f1")
        assertThat(toggleEvent?.fromDock).isTrue()
    }

    @Test
    fun tabStripRow_expandingFolderInDock_scrollsFolderToLeftEdge() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example1.com"),
            Tab(id = "tab2", title = "Tab 2", url = "https://example2.com"),
            Tab(id = "tab3", title = "Tab 3", url = "https://example3.com"),
            Tab(id = "tab4", title = "Tab 4", url = "https://example4.com"),
            Tab(id = "tab5", title = "Tab 5", url = "https://example5.com"),
            Tab(id = "tabF1", title = "Work 1", url = "https://work1.com", folderId = "f1"),
            Tab(id = "tabF2", title = "Work 2", url = "https://work2.com", folderId = "f1"),
        ) + (1..15).map { i ->
            Tab(id = "tabAfter$i", title = "After $i", url = "https://after$i.com")
        }

        var listState: androidx.compose.foundation.lazy.LazyListState? = null
        val active = tabs.first()
        testUiState.value = BrowserUiState(
            tabs = tabs,
            folders = listOf(folder),
            activeTabId = "tab1",
            currentUrl = active.url,
            pageTitle = active.title,
        )
        composeRule.setContent {
            val state = testUiState.value ?: return@setContent
            val lState = androidx.compose.foundation.lazy.rememberLazyListState()
            listState = lState
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                    Box(
                        modifier = Modifier
                            .width(400.dp)
                            .height(50.dp),
                    ) {
                        TabStripRow(
                            uiState = state,
                            onEvent = { events.add(it) },
                            lazyListState = lState,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertThat(listState!!.firstVisibleItemIndex).isEqualTo(0)

        // Click collapsed folder in dock
        composeRule.onNodeWithTag("folder_indicator_f1").performClick()
        val toggleEvent = events.filterIsInstance<BrowserUiEvent.ToggleFolderCollapse>().firstOrNull()
        assertThat(toggleEvent).isNotNull()
        assertThat(toggleEvent?.folderId).isEqualTo("f1")
        assertThat(toggleEvent?.fromDock).isTrue()

        // Simulate ViewModel updating the folder to expanded (isCollapsed = false)
        val expandedFolder = folder.copy(isCollapsed = false)
        testUiState.value = testUiState.value!!.copy(folders = listOf(expandedFolder))
        composeRule.waitForIdle()

        // In dockItems, tabs 1..5 occupy indexes 0..4; folder f1 is at index 5.
        // It must be scrolled to the left edge (firstVisibleItemIndex == 5).
        assertThat(listState!!.firstVisibleItemIndex).isEqualTo(5)
    }

    // --- 4. FolderContextMenu Tests ---

    @Test
    fun folderContextMenu_rendersActionsAndDispatchesEvents() {
        val folder = TabFolder(id = "f1", title = "Shopping", color = 0xFFFF6F61.toInt())
        var newTabClicked = false
        var editClicked = false
        var ungroupClicked = false
        var closeClicked = false

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderContextMenu(
                    folder = folder,
                    expanded = true,
                    onDismissRequest = {},
                    isDark = false,
                    onNewTabInFolder = { newTabClicked = true },
                    onEditFolder = { editClicked = true },
                    onUngroupFolder = { ungroupClicked = true },
                    onCloseFolder = { closeClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("folder_context_menu_f1").assertIsDisplayed()
        composeRule.onNodeWithText("Shopping").assertIsDisplayed()

        composeRule.onNodeWithTag("folder_menu_new_tab").performClick()
        assertThat(newTabClicked).isTrue()

        composeRule.onNodeWithTag("folder_menu_edit").performClick()
        assertThat(editClicked).isTrue()

        composeRule.onNodeWithTag("folder_menu_ungroup").performClick()
        assertThat(ungroupClicked).isTrue()

        composeRule.onNodeWithTag("folder_menu_close").performClick()
        assertThat(closeClicked).isTrue()
    }

    // --- 5. TabContextMenu Folder Integration Tests ---

    @Test
    fun tabContextMenu_tabWithoutFolder_showsNewFolderAndAddToFolder() {
        var createFolderClicked = false

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabContextMenu(
                    expanded = true,
                    onDismissRequest = {},
                    isDark = false,
                    onCloseTab = {},
                    onCopyUrl = {},
                    onAddFavorite = {},
                    folderId = null,
                    availableFolders = listOf(
                        TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
                    ),
                    onCreateFolder = { createFolderClicked = true },
                    onAddToFolder = {},
                )
            }
        }

        composeRule.onNodeWithTag("tab_context_new_folder").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_context_add_to_folder").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_context_new_folder").performClick()
        assertThat(createFolderClicked).isTrue()
    }

    @Test
    fun tabContextMenu_tabWithFolder_showsRemoveFromFolder() {
        var removeClicked = false

        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabContextMenu(
                    expanded = true,
                    onDismissRequest = {},
                    isDark = false,
                    onCloseTab = {},
                    onCopyUrl = {},
                    onAddFavorite = {},
                    folderId = "f1",
                    onRemoveFromFolder = { removeClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("tab_context_remove_from_folder").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_context_new_folder").assertDoesNotExist()

        composeRule.onNodeWithTag("tab_context_remove_from_folder").performClick()
        assertThat(removeClicked).isTrue()
    }

    // --- 6. TabStripLayoutMetrics Dynamic Widths ---

    @Test
    fun tabStripLayoutMetrics_dynamicWidthLookup_usesItemWidthProvider() {
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 200f,
            inactiveTabWidthPx = 100f,
            spacingPx = 10f,
            itemWidthProvider = { key ->
                if (key == "folder_f1") 150f else null
            },
        )

        assertThat(metrics.getItemWidth("folder_f1", 100f)).isEqualTo(150f)
        assertThat(metrics.getItemWidth("tab1", 100f)).isEqualTo(100f)
    }

    @Test
    fun tabStripDragState_getSlotStart_accountsForFolderIndicators() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://jira.com", folderId = "f1"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 100f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { key ->
                if (key == "folder_f1") 80f else 38f
            },
            folderProvider = { folder },
        )

        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            onReorder = {},
        )

        // Slot of tab1: starts at 0
        assertThat(dragState.getSlotStart("tab1", tabs, metrics)).isEqualTo(0f)

        // Slot of tab2: preceded by tab1 (38 + 4) and folder_f1 (80 + 4) = 42 + 84 = 126
        assertThat(dragState.getSlotStart("tab2", tabs, metrics)).isEqualTo(126f)

        // Slot of tab3: preceded by tab1, folder_f1, and tab2 (38 + 4) = 126 + 42 = 168
        assertThat(dragState.getSlotStart("tab3", tabs, metrics)).isEqualTo(168f)
    }

    @Test
    fun tabStripDragState_dragTabIntoFolder_adoptsFolderId() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            folderProvider = { folder },
        )
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
        )
        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 100f, metrics = metrics)

        assertThat(dragState.currentTabs[1].id).isEqualTo("tab1")
        assertThat(dragState.currentTabs[1].folderId).isEqualTo("f1")
    }

    @Test
    fun tabStripDragState_dragTabOutOfFolder_clearsFolderId() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "tab1", title = "Standalone", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            folderProvider = { folder },
        )
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
        )
        dragState.onDragStart("tab2", initialVisualX = 126f)
        dragState.onDrag("tab2", deltaX = -120f, metrics = metrics)

        // Crossing the folder indicator is one step: it clears the membership and leaves the tab
        // on the same side of tab1, instead of jumping over both the indicator and tab1 at once.
        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab1", "tab2").inOrder()
        assertThat(dragState.currentTabs.first { it.id == "tab2" }.folderId).isNull()

        // The drag keeps working and the next crossing moves the tab past tab1.
        dragState.onDrag("tab2", deltaX = -60f, metrics = metrics)

        assertThat(dragState.currentTabs[0].id).isEqualTo("tab2")
        assertThat(dragState.currentTabs[0].folderId).isNull()
    }

    @Test
    fun tabStripDragState_dragFolder_reordersEntireFolderBlock() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "tab1", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab2", title = "Work 2", url = "https://jira.com", folderId = "f1"),
            Tab(id = "tab3", title = "Standalone", url = "https://other.com"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { if (it == "folder_f1") 38f else null },
            folderProvider = { folder },
        )
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
        )
        dragState.onDragStart("folder_f1", initialVisualX = 0f)
        dragState.onDrag("folder_f1", deltaX = 150f, metrics = metrics)

        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab3", "tab1", "tab2").inOrder()
    }

    // --- 7. Visual Preview Test ---

    @Test
    fun renderDockWithFolderIndicatorsPreview() {
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleFolder1 = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val sampleFolder2 = TabFolder(id = "f2", title = "", color = 0xFF10B981.toInt(), isCollapsed = true)

        val sampleTabs = listOf(
            Tab(id = "tab1", title = "Search", url = "https://google.com"),
            Tab(id = "tab2", title = "Jira Sprint", url = "https://jira.com", folderId = "f1"),
            Tab(id = "tab3", title = "Docs", url = "https://docs.google.com", folderId = "f1"),
            Tab(id = "tab4", title = "News", url = "https://news.ycombinator.com", folderId = "f2"),
        )

        val sampleState = BrowserUiState(
            tabs = sampleTabs,
            folders = listOf(sampleFolder1, sampleFolder2),
            activeTabId = "tab2",
            currentUrl = "https://jira.com",
            pageTitle = "Jira Sprint",
        )

        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier
                            .width(400.dp)
                            .height(60.dp),
                    ) {
                        TabStripRow(
                            uiState = sampleState,
                            onEvent = {},
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 800
        val height = 120
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = java.io.File("build/dock_folder_indicators_preview.png")
        outputFile.parentFile?.mkdirs()
        java.io.FileOutputStream(outputFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        assertThat(outputFile.exists()).isTrue()
    }

    // --- 8. Spring-Loaded Folder Dock Interaction Tests ---

    @Test
    fun tabStripDragState_dragTabOverClosedFolder_startsBlinkingAndHover() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { folder },
        )
        var hoverVibrated = false
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onHoverVibration = { hoverVibrated = true },
        )

        // Slot 0 (tab1): 0..38. Slot of folder f1: 38 + 4 = 42..80.
        dragState.onDragStart("tab1", initialVisualX = 0f)
        // Drag deltaX = 42f -> visualCenter = 0 + 42 + 19 = 61 (inside [42, 80])
        dragState.onDrag("tab1", deltaX = 42f, metrics = metrics)

        assertThat(dragState.hoveredFolderId).isEqualTo("f1")
        assertThat(hoverVibrated).isTrue()
        assertThat(dragState.springLoadedFolderId).isNull()
    }

    @Test
    fun tabStripDragState_dropTabOnClosedFolderBeforeTimeout_addsTabToEndOfFolder() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://work2.com", folderId = "f1"),
            Tab(id = "tab4", title = "Outside", url = "https://outside.com"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { folder },
        )
        var reorderedTabs: List<Tab>? = null
        var toggleCollapseCalled = false
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = { reorderedTabs = it },
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { toggleCollapseCalled = true },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 42f, metrics = metrics)
        assertThat(dragState.hoveredFolderId).isEqualTo("f1")

        // Drop directly onto closed folder before 650ms timeout
        dragState.onDragEnd(isReleasedActive = false, originalTabs = tabs)

        assertThat(dragState.hoveredFolderId).isNull()
        assertThat(dragState.springLoadedFolderId).isNull()
        assertThat(toggleCollapseCalled).isFalse()

        // Tab1 should now be at the end of f1's tabs: tab2, tab3, tab1, tab4
        val currentTabs = dragState.currentTabs
        val f1Tabs = currentTabs.filter { it.folderId == "f1" }
        assertThat(f1Tabs.map { it.id }).containsExactly("tab2", "tab3", "tab1").inOrder()
        assertThat(currentTabs.last().id).isEqualTo("tab4")
        assertThat(reorderedTabs).isNotNull()
    }

    @Test
    fun tabStripDragState_hoverOverClosedFolderForTimeout_springLoadsFolder() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { folder },
        )
        var toggleCollapseFolderId: String? = null
        var springLoadVibrated = false
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { toggleCollapseFolderId = it },
            onSpringLoadVibration = { springLoadVibrated = true },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 42f, metrics = metrics)
        assertThat(dragState.hoveredFolderId).isEqualTo("f1")
        assertThat(toggleCollapseFolderId).isNull()

        // Advance coroutine virtual time by 650ms (SPRING_LOAD_HOVER_TIMEOUT_MS)
        advanceUntilIdle()

        assertThat(toggleCollapseFolderId).isEqualTo("f1")
        assertThat(dragState.springLoadedFolderId).isEqualTo("f1")
        assertThat(dragState.hoveredFolderId).isNull()
        assertThat(springLoadVibrated).isTrue()
    }

    @Test
    fun tabStripDragState_draggingAwayFromSpringLoadedFolder_autoCollapsesFolder() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Outside", url = "https://outside.com"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { folder },
        )
        val collapsedFolderIds = mutableListOf<String>()
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { collapsedFolderIds.add(it) },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 42f, metrics = metrics)
        advanceUntilIdle()
        assertThat(dragState.springLoadedFolderId).isEqualTo("f1")
        assertThat(collapsedFolderIds).containsExactly("f1") // Initial expand

        // Drag tab away to the left (back to visualX = 0)
        dragState.onDrag("tab1", deltaX = -42f, metrics = metrics)

        // Must auto-collapse: toggleFolderCollapse called second time
        assertThat(dragState.springLoadedFolderId).isNull()
        assertThat(collapsedFolderIds).containsExactly("f1", "f1")
    }

    @Test
    fun tabStripDragState_dropInsideSpringLoadedFolder_keepsFolderExpanded() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com", folderId = "f1"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        var toggleCollapseCount = 0
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { toggleCollapseCount++ },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.springLoadedFolderId = "f1"

        // Drop tab inside spring-opened folder
        dragState.onDragEnd(isReleasedActive = false, originalTabs = tabs)

        // Must NOT collapse: toggleCollapseCount remains 0
        assertThat(toggleCollapseCount).isEqualTo(0)
        assertThat(dragState.springLoadedFolderId).isNull()
        assertThat(dragState.currentTabs[0].folderId).isEqualTo("f1")
    }

    @Test
    fun tabStripDragState_cancelDragWhileSpringLoaded_autoCollapsesFolder() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Standalone", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        val collapsedFolders = mutableListOf<String>()
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { collapsedFolders.add(it) },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.springLoadedFolderId = "f1"

        // User cancels drag (e.g. gesture aborted or pointer lost)
        dragState.onDragCancel(isReleasedActive = false)

        assertThat(collapsedFolders).containsExactly("f1")
        assertThat(dragState.springLoadedFolderId).isNull()
    }

    @Test
    fun tabStripDragState_dropInsideSpringLoadedFolderWithoutSwapping_placesTabInFolderAndKeepsExpanded() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Standalone", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
        )
        var toggleCollapseCount = 0
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
            onToggleFolderCollapse = { toggleCollapseCount++ },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.springLoadedFolderId = "f1"

        // Drop tab1 while spring-loaded over f1 without manual swap
        dragState.onDragEnd(isReleasedActive = false, originalTabs = tabs)

        assertThat(toggleCollapseCount).isEqualTo(0) // Folder remains expanded
        val droppedTab = dragState.currentTabs.firstOrNull { it.id == "tab1" }
        assertThat(droppedTab?.folderId).isEqualTo("f1")
    }

    @Test
    fun tabStripDragState_getSlotStart_withPrecedingCollapsedFolder_doesNotIncludeHiddenTabsWidth() = runTest(testFrameClock) {
        val folder1 = TabFolder(id = "f1", title = "Collapsed", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val folder2 = TabFolder(id = "f2", title = "Open", color = 0xFF10B981.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "F1 Tab 1", url = "https://f1-1.com", folderId = "f1"),
            Tab(id = "tab2", title = "F1 Tab 2", url = "https://f1-2.com", folderId = "f1"),
            Tab(id = "tab3", title = "Standalone", url = "https://example.com"),
            Tab(id = "tab4", title = "F2 Tab 1", url = "https://f2-1.com", folderId = "f2"),
        )
        val metrics = TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { id -> if (id == "f1") folder1 else if (id == "f2") folder2 else null },
        )
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder1, folder2) },
        )

        // Dock layout should be:
        // folder_f1 (38px) + spacing (4px) = 42px
        // tab3 (standalone): start at 42px.
        // folder_f2 (38px) + spacing (4px) = 84px
        // tab4 (f2 tab): start at 84 + 42 = 126px.
        val tab3SlotStart = dragState.getSlotStart("tab3", tabs, metrics)
        assertThat(tab3SlotStart).isEqualTo(42f)

        val folderF2SlotStart = dragState.getSlotStart("folder_f2", tabs, metrics)
        assertThat(folderF2SlotStart).isEqualTo(84f)
    }

    // --- 9. Passing over a collapsed folder must never capture the dragged tab ---

    private fun dockTestMetrics(folders: List<TabFolder>): TabStripLayoutMetrics =
        TabStripLayoutMetrics(
            activeTabWidthPx = 38f,
            inactiveTabWidthPx = 38f,
            spacingPx = 4f,
            hysteresisPx = 4f,
            itemWidthProvider = { 38f },
            folderProvider = { id -> folders.firstOrNull { it.id == id } },
        )

    @Test
    fun tabStripDragState_dragTabOverCollapsedFolder_passesOverWithoutJoiningAndStaysDraggable() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://work2.com", folderId = "f1"),
        )
        val metrics = dockTestMetrics(listOf(folder))
        var reorderedTabs: List<Tab>? = null
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = { reorderedTabs = it },
            foldersProvider = { listOf(folder) },
        )

        // Dock: tab1 [0..38], folder_f1 [42..80]. The dragged tab's center starts at 19.
        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 42f, metrics = metrics)

        // Center 61: the tab covers the closed folder, so it only hovers and spring-loads it.
        assertThat(dragState.hoveredFolderId).isEqualTo("f1")
        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab1", "tab2", "tab3").inOrder()

        // Center 91: past the folder's right edge, so it crosses the whole group at once.
        dragState.onDrag("tab1", deltaX = 30f, metrics = metrics)

        assertThat(dragState.hoveredFolderId).isNull()
        assertThat(dragState.isDragging).isTrue()
        assertThat(dragState.draggingTabId).isEqualTo("tab1")
        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab2", "tab3", "tab1").inOrder()
        assertThat(dragState.currentTabs.first { it.id == "tab1" }.folderId).isNull()

        dragState.onDragEnd(isReleasedActive = false, originalTabs = tabs)

        assertThat(reorderedTabs).isNotNull()
        assertThat(reorderedTabs!!.map { it.id }).containsExactly("tab2", "tab3", "tab1").inOrder()
        assertThat(reorderedTabs!!.first { it.id == "tab1" }.folderId).isNull()
    }

    @Test
    fun tabStripDragState_dragTabPastCollapsedFolder_continuesToTheNextTab() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Outside", url = "https://outside.com"),
        )
        val metrics = dockTestMetrics(listOf(folder))
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
        )

        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 70f, metrics = metrics)

        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab2", "tab1", "tab3").inOrder()
        assertThat(dragState.draggingTabId).isEqualTo("tab1")

        // The drag keeps working after the crossing instead of freezing next to the folder.
        dragState.onDrag("tab1", deltaX = 120f, metrics = metrics)

        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab2", "tab3", "tab1").inOrder()
        assertThat(dragState.currentTabs.first { it.id == "tab1" }.folderId).isNull()
        assertThat(dragState.draggingTabId).isEqualTo("tab1")
    }

    @Test
    fun tabStripDragState_dragTabLeftAcrossCollapsedFolder_leavesGroupInsteadOfJoining() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://work2.com", folderId = "f1"),
            Tab(id = "tab1", title = "Outside", url = "https://outside.com"),
        )
        val metrics = dockTestMetrics(listOf(folder))
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
        )

        // Dock: folder_f1 [0..38], tab1 [42..80]. Only the folder's outer edge is crossable.
        dragState.onDragStart("tab1", initialVisualX = 42f)
        dragState.onDrag("tab1", deltaX = -70f, metrics = metrics)

        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab1", "tab2", "tab3").inOrder()
        assertThat(dragState.currentTabs.first { it.id == "tab1" }.folderId).isNull()
    }

    @Test
    fun tabStripDragState_dragFolderOverCollapsedFolder_movesCrossedGroupAsOne() = runTest(testFrameClock) {
        val openFolder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val closedFolder = TabFolder(id = "f2", title = "News", color = 0xFF10B981.toInt(), isCollapsed = true)
        val tabs = listOf(
            Tab(id = "f1a", title = "Work 1", url = "https://work1.com", folderId = "f1"),
            Tab(id = "f1b", title = "Work 2", url = "https://work2.com", folderId = "f1"),
            Tab(id = "f2a", title = "News 1", url = "https://news.com", folderId = "f2"),
        )
        val metrics = dockTestMetrics(listOf(openFolder, closedFolder))
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(openFolder, closedFolder) },
        )

        // Dock: folder_f1 [0..38], f1a [42..80], f1b [84..122], folder_f2 [126..164].
        dragState.onDragStart("folder_f1", initialVisualX = 0f)
        dragState.onDrag("folder_f1", deltaX = 150f, metrics = metrics)

        assertThat(dragState.draggingTabId).isEqualTo("folder_f1")
        assertThat(dragState.currentTabs.map { it.id }).containsExactly("f2a", "f1a", "f1b").inOrder()
        assertThat(dragState.currentTabs.filter { it.folderId == "f1" }.map { it.id })
            .containsExactly("f1a", "f1b").inOrder()
    }

    @Test
    fun tabStripDragState_dragTabBetweenVisibleFolderMembers_stillJoinsThatFolder() = runTest(testFrameClock) {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt(), isCollapsed = false)
        val tabs = listOf(
            Tab(id = "tab1", title = "Tab 1", url = "https://example.com"),
            Tab(id = "tab2", title = "Work 1", url = "https://work.com", folderId = "f1"),
            Tab(id = "tab3", title = "Work 2", url = "https://work2.com", folderId = "f1"),
        )
        val metrics = dockTestMetrics(listOf(folder))
        val dragState = TabStripDragState(
            initialTabs = tabs,
            coroutineScope = this,
            onReorder = {},
            foldersProvider = { listOf(folder) },
        )

        // Dock: tab1 [0..38], folder_f1 [42..80], tab2 [84..122], tab3 [126..164].
        dragState.onDragStart("tab1", initialVisualX = 0f)
        dragState.onDrag("tab1", deltaX = 90f, metrics = metrics)

        assertThat(dragState.currentTabs.map { it.id }).containsExactly("tab2", "tab1", "tab3").inOrder()
        assertThat(dragState.currentTabs.first { it.id == "tab1" }.folderId).isEqualTo("f1")
    }
}
