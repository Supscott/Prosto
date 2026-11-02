package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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

        // Slot 0 (tab1): starts at 0
        assertThat(dragState.getSlotStart(0, tabs, metrics)).isEqualTo(0f)

        // Slot 1 (tab2): preceded by tab1 (38 + 4) and folder_f1 (80 + 4) = 42 + 84 = 126
        assertThat(dragState.getSlotStart(1, tabs, metrics)).isEqualTo(126f)

        // Slot 2 (tab3): preceded by tab1, folder_f1, and tab2 (38 + 4) = 126 + 42 = 168
        assertThat(dragState.getSlotStart(2, tabs, metrics)).isEqualTo(168f)
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
}
