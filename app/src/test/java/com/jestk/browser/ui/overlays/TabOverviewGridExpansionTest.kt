package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabOverviewGridExpansionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun expandingFolder_togglesExpandedFolderIdsAndRendersInGridHeaderAndMemberCards() {
        val folder = TabFolder(
            id = "f1",
            title = "Work",
            color = 0xFF2196F3.toInt(),
        )
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
            Tab(id = "t3", url = "https://three.com", title = "Tab Three", folderId = null),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder),
                        activeTabId = "t1",
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initially folder is collapsed: card exists, member cards & in-grid header do not exist as grid items
        composeTestRule.onNodeWithTag("folder_card_f1").assertExists()
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tab_card_t1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tab_card_t2").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tab_card_t3").assertExists()

        // Tap folder card to expand
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        // Now folder is expanded: collapsed card is gone, in-grid header and member cards are displayed
        composeTestRule.onNodeWithTag("folder_card_f1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_header_title_f1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_header_arrow_f1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_card_t1").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_card_t2").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_card_t3").assertExists().assertIsDisplayed()
    }

    @Test
    fun multipleFolders_canExpandSimultaneously() {
        val folder1 = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val folder2 = TabFolder(id = "f2", title = "Personal", color = 0xFF10B981.toInt())

        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
            Tab(id = "t3", url = "https://three.com", title = "Tab Three", folderId = "f2"),
            Tab(id = "t4", url = "https://four.com", title = "Tab Four", folderId = "f2"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder1, folder2),
                        activeTabId = "t1",
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Expand first folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertExists()
        composeTestRule.onNodeWithTag("folder_card_f2").assertExists()

        // Expand second folder
        composeTestRule.onNodeWithTag("folder_card_f2").performClick()
        composeTestRule.waitForIdle()

        // Both folders are expanded simultaneously
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_in_grid_header_f2").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_card_t1").assertExists()
        composeTestRule.onNodeWithTag("tab_card_t2").assertExists()
        composeTestRule.onNodeWithTag("tab_card_t3").assertExists()
        composeTestRule.onNodeWithTag("tab_card_t4").assertExists()
    }

    @Test
    fun clickingHeader_collapsesFolder() {
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder),
                        activeTabId = "t1",
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Expand
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertExists()

        // Click in-grid header to collapse
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").performClick()
        composeTestRule.waitForIdle()

        // Folder is collapsed back
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("folder_card_f1").assertExists().assertIsDisplayed()
    }

    @Test
    fun floatingStickyHeader_stateCalculation_pinsAndUnpinsSmoothly() {
        val folder1 = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val folderTabsMap = mapOf(
            "f1" to listOf(
                Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
                Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
            )
        )
        val headerHeightPx = 132f

        // Case 1: In-grid header is visible below top of viewport (offset.y = 24 > 0)
        val itemsVisibleHeader = listOf(
            GridItemBounds(key = "folder_header_f1", offset = IntOffset(0, 24), size = IntSize(1000, 132)),
            GridItemBounds(key = "tab_t1", offset = IntOffset(0, 160), size = IntSize(300, 500)),
        )
        val state1 = computeStickyHeaderStateFromBounds(
            expandedFolders = listOf(folder1),
            folderMemberTabsMap = folderTabsMap,
            visibleItems = itemsVisibleHeader,
            headerHeightPx = headerHeightPx,
        )
        assertThat(state1.visible).isFalse()

        // Case 2: In-grid header scrolled past top (offset.y = -10 < 0), member cards visible with folderBottom > headerHeightPx
        val itemsHeaderScrolledPast = listOf(
            GridItemBounds(key = "folder_header_f1", offset = IntOffset(0, -10), size = IntSize(1000, 132)),
            GridItemBounds(key = "tab_t1", offset = IntOffset(0, 126), size = IntSize(300, 500)),
        )
        val state2 = computeStickyHeaderStateFromBounds(
            expandedFolders = listOf(folder1),
            folderMemberTabsMap = folderTabsMap,
            visibleItems = itemsHeaderScrolledPast,
            headerHeightPx = headerHeightPx,
        )
        assertThat(state2.visible).isTrue()
        assertThat(state2.folder?.id).isEqualTo("f1")
        assertThat(state2.translationY).isEqualTo(0f)

        // Case 3: In-grid header completely scrolled off (headerItem == null), member cards visible
        val itemsHeaderOffScreen = listOf(
            GridItemBounds(key = "tab_t1", offset = IntOffset(0, 50), size = IntSize(300, 500)),
            GridItemBounds(key = "tab_t2", offset = IntOffset(310, 50), size = IntSize(300, 500)),
        )
        val state3 = computeStickyHeaderStateFromBounds(
            expandedFolders = listOf(folder1),
            folderMemberTabsMap = folderTabsMap,
            visibleItems = itemsHeaderOffScreen,
            headerHeightPx = headerHeightPx,
        )
        assertThat(state3.visible).isTrue()
        assertThat(state3.folder?.id).isEqualTo("f1")
        assertThat(state3.tabCount).isEqualTo(2)
        assertThat(state3.translationY).isEqualTo(0f)

        // Case 4: Last member item is scrolling off (folderBottom = 80 < headerHeightPx = 132)
        val itemsLastMemberUnpinning = listOf(
            GridItemBounds(key = "tab_t2", offset = IntOffset(0, -420), size = IntSize(300, 500)), // bottom = 80
        )
        val state4 = computeStickyHeaderStateFromBounds(
            expandedFolders = listOf(folder1),
            folderMemberTabsMap = folderTabsMap,
            visibleItems = itemsLastMemberUnpinning,
            headerHeightPx = headerHeightPx,
        )
        assertThat(state4.visible).isTrue()
        assertThat(state4.translationY).isEqualTo(80f - 132f) // Smooth upward push!

        // Case 5: All member items have scrolled off top (folderBottom = -10 <= 0)
        val itemsAllOffScreen = listOf(
            GridItemBounds(key = "tab_t2", offset = IntOffset(0, -510), size = IntSize(300, 500)), // bottom = -10
        )
        val state5 = computeStickyHeaderStateFromBounds(
            expandedFolders = listOf(folder1),
            folderMemberTabsMap = folderTabsMap,
            visibleItems = itemsAllOffScreen,
            headerHeightPx = headerHeightPx,
        )
        assertThat(state5.visible).isFalse()
    }

    @Test
    fun folderUnderlayBounds_calculatesBoundingBoxProperly() {
        val memberKeys = setOf("tab_t1", "tab_t2", "tab_t3")
        val visibleItems = listOf(
            GridItemBounds(key = "folder_header_f1", offset = IntOffset(16, 20), size = IntSize(900, 130)),
            GridItemBounds(key = "tab_t1", offset = IntOffset(16, 160), size = IntSize(280, 500)),
            GridItemBounds(key = "tab_t2", offset = IntOffset(306, 160), size = IntSize(280, 500)),
            GridItemBounds(key = "tab_t3", offset = IntOffset(596, 160), size = IntSize(280, 500)),
        )

        val bounds = calculateFolderUnderlayBoundsFromBounds(
            visibleItems = visibleItems,
            folderTabKeys = memberKeys,
            gridWidth = 1000f,
            horizontalMarginPx = 10f,
            verticalPaddingPx = 6f,
        )

        assertThat(bounds).isNotNull()
        assertThat(bounds?.minX).isEqualTo(10f)
        assertThat(bounds?.maxX).isEqualTo(990f)
        assertThat(bounds?.minY).isEqualTo(154f) // 160 - 6
        assertThat(bounds?.maxY).isEqualTo(666f) // 160 + 500 + 6
    }

    @Test
    fun shouldRemoveTabFromFolderOnDrop_handlesAllScenarios() {
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f1"),
            Tab(id = "t3", url = "https://three.com", title = "Tab Three", folderId = null),
            Tab(id = "t4", url = "https://four.com", title = "Tab Four", folderId = "f2"),
        )
        val visibleItems = listOf(
            GridItemBounds(key = "folder_header_f1", offset = IntOffset(0, 100), size = IntSize(900, 100)),
            GridItemBounds(key = "tab_t1", offset = IntOffset(0, 210), size = IntSize(280, 500)),
            GridItemBounds(key = "tab_t2", offset = IntOffset(300, 210), size = IntSize(280, 500)),
        )

        // 1. Standalone tab being dragged -> should not remove
        val removeStandalone = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = null,
            dropTargetKey = "tab_t3",
            dropPosition = Offset(100f, 250f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removeStandalone).isFalse()

        // 2. Member tab dropped on another member of SAME folder -> should not remove
        val removeSameFolder = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = "f1",
            dropTargetKey = "tab_t2",
            dropPosition = Offset(320f, 250f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removeSameFolder).isFalse()

        // 3. Member tab dropped on standalone tab -> should remove (ungroup)
        val removeStandaloneTarget = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = "f1",
            dropTargetKey = "tab_t3",
            dropPosition = Offset(100f, 800f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removeStandaloneTarget).isTrue()

        // 4. Member tab dropped on another folder card -> should remove
        val removeOtherFolder = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = "f1",
            dropTargetKey = "folder_f2",
            dropPosition = Offset(100f, 800f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removeOtherFolder).isTrue()

        // 5. Member tab dropped past the end of folder items (Y = 800 > maxY 710) -> should remove
        val removePastEnd = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = "f1",
            dropTargetKey = null,
            dropPosition = Offset(100f, 800f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removePastEnd).isTrue()

        // 6. Member tab dropped inside folder bounds -> should not remove
        val removeInside = shouldRemoveTabFromFolderOnDropFromBounds(
            sourceFolderId = "f1",
            dropTargetKey = null,
            dropPosition = Offset(100f, 400f),
            tabs = tabs,
            visibleItems = visibleItems,
        )
        assertThat(removeInside).isFalse()
    }

    @Test
    fun dragOut_fromExpandedFolder_triggersRemoveTabFromFolderEvent() {
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
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder),
                        activeTabId = "t1",
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Expand the folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot

        val t1Center = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val t3Center = composeTestRule.onNodeWithTag("tab_card_t3", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        // 2. Drag tab t1 onto standalone tab t3 (outside folder bounds)
        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)

        gridNode.performTouchInput {
            moveTo(t3Center)
        }
        composeTestRule.mainClock.advanceTimeBy(50)

        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        // 3. Verify RemoveTabFromFolder was fired for t1
        val removeEvent = events.filterIsInstance<BrowserUiEvent.RemoveTabFromFolder>().firstOrNull()
        assertThat(removeEvent).isNotNull()
        assertThat(removeEvent?.tabId).isEqualTo("t1")
    }

    @Test
    fun computeOverviewItems_expandedFolderWithNonMultipleOf3Tabs_appendsSpacers() {
        val folder = TabFolder(id = "f1", title = "Folder 1", color = 0xFF2196F3.toInt())
        val tabs1 = listOf(
            Tab(id = "t1", url = "https://t1.com", title = "T1", folderId = "f1"),
            Tab(id = "ext1", url = "https://ext1.com", title = "Ext1", folderId = null),
        )
        // Case 1: 1 tab in folder -> 3 - (1 % 3) = 2 spacers
        val items1 = computeOverviewItems(tabs1, listOf(folder), searchQuery = "", expandedFolderIds = setOf("f1"))
        // Expected: FolderHeader, FolderTabCard(t1), FolderSpacer(f1, 0), FolderSpacer(f1, 1), TabCard(ext1)
        assertThat(items1).hasSize(5)
        assertThat(items1[0]).isInstanceOf(OverviewGridItem.FolderHeader::class.java)
        assertThat(items1[1]).isInstanceOf(OverviewGridItem.FolderTabCard::class.java)
        assertThat(items1[2]).isEqualTo(OverviewGridItem.FolderSpacer("f1", 0))
        assertThat(items1[3]).isEqualTo(OverviewGridItem.FolderSpacer("f1", 1))
        assertThat(items1[4]).isEqualTo(OverviewGridItem.TabCard(tabs1[1]))

        // Case 2: 2 tabs in folder -> 3 - (2 % 3) = 1 spacer
        val tabs2 = listOf(
            Tab(id = "t1", url = "https://t1.com", title = "T1", folderId = "f1"),
            Tab(id = "t2", url = "https://t2.com", title = "T2", folderId = "f1"),
            Tab(id = "ext1", url = "https://ext1.com", title = "Ext1", folderId = null),
        )
        val items2 = computeOverviewItems(tabs2, listOf(folder), searchQuery = "", expandedFolderIds = setOf("f1"))
        // Expected: FolderHeader, FolderTabCard(t1), FolderTabCard(t2), FolderSpacer(f1, 0), TabCard(ext1)
        assertThat(items2).hasSize(5)
        assertThat(items2[0]).isInstanceOf(OverviewGridItem.FolderHeader::class.java)
        assertThat(items2[1]).isInstanceOf(OverviewGridItem.FolderTabCard::class.java)
        assertThat(items2[2]).isInstanceOf(OverviewGridItem.FolderTabCard::class.java)
        assertThat(items2[3]).isEqualTo(OverviewGridItem.FolderSpacer("f1", 0))
        assertThat(items2[4]).isEqualTo(OverviewGridItem.TabCard(tabs2[2]))

        // Case 3: 3 tabs in folder -> 0 spacers
        val tabs3 = listOf(
            Tab(id = "t1", url = "https://t1.com", title = "T1", folderId = "f1"),
            Tab(id = "t2", url = "https://t2.com", title = "T2", folderId = "f1"),
            Tab(id = "t3", url = "https://t3.com", title = "T3", folderId = "f1"),
            Tab(id = "ext1", url = "https://ext1.com", title = "Ext1", folderId = null),
        )
        val items3 = computeOverviewItems(tabs3, listOf(folder), searchQuery = "", expandedFolderIds = setOf("f1"))
        assertThat(items3).hasSize(5) // Header + 3 tabs + 1 ext
        assertThat(items3.filterIsInstance<OverviewGridItem.FolderSpacer>()).isEmpty()
        assertThat(items3[4]).isEqualTo(OverviewGridItem.TabCard(tabs3[3]))
    }

    @Test
    fun expandedFolder_with1Or2Tabs_rendersSpacersAndPlacesSubsequentItemsOnNextRow() {
        val folder1 = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val folder2 = TabFolder(id = "f2", title = "Social", color = 0xFF10B981.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
            Tab(id = "ext1", url = "https://ext1.com", title = "Ext One", folderId = null),
            Tab(id = "t2", url = "https://two.com", title = "Tab Two", folderId = "f2"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder1, folder2),
                        activeTabId = "t1",
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initially collapsed: tap folder1 card to expand
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        // Spacers exist for folder f1 (1 tab -> 2 spacers)
        composeTestRule.onNodeWithTag("folder_spacer_f1_0").assertExists()
        composeTestRule.onNodeWithTag("folder_spacer_f1_1").assertExists()

        // Check layout coordinates: ext1 and f2 must be on subsequent row below t1
        val t1Bounds = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val ext1Bounds = composeTestRule.onNodeWithTag("tab_card_ext1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val f2Bounds = composeTestRule.onNodeWithTag("folder_card_f2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        // ext1 top is strictly below or equal to t1 bottom (next row)
        assertThat(ext1Bounds.top).isAtLeast(t1Bounds.bottom)
        // f2 is also on the next row alongside ext1
        assertThat(f2Bounds.top).isAtLeast(t1Bounds.bottom)
        assertThat(f2Bounds.top).isEqualTo(ext1Bounds.top)
    }

    @Test
    fun folderUnderlayBounds_withSpacers_neverEncompassesExternalItemsOnNextRow() {
        val folderKeys = setOf("tab_t1", "folder_spacer_f1_0", "folder_spacer_f1_1")
        val visibleItems = listOf(
            GridItemBounds(key = "folder_header_f1", offset = IntOffset(16, 20), size = IntSize(900, 100)),
            GridItemBounds(key = "tab_t1", offset = IntOffset(16, 140), size = IntSize(280, 500)),
            GridItemBounds(key = "folder_spacer_f1_0", offset = IntOffset(306, 140), size = IntSize(280, 500)),
            GridItemBounds(key = "folder_spacer_f1_1", offset = IntOffset(596, 140), size = IntSize(280, 500)),
            // External tab on the next row
            GridItemBounds(key = "tab_ext1", offset = IntOffset(16, 660), size = IntSize(280, 500)),
            // External folder on the next row
            GridItemBounds(key = "folder_f2", offset = IntOffset(306, 660), size = IntSize(280, 500)),
        )

        val bounds = calculateFolderUnderlayBoundsFromBounds(
            visibleItems = visibleItems,
            folderTabKeys = folderKeys,
            gridWidth = 1000f,
            horizontalMarginPx = 10f,
            verticalPaddingPx = 6f,
        )

        assertThat(bounds).isNotNull()
        // Underlay encompasses rows of the folder: from 140 - 6 to 140 + 500 + 6 = 646
        assertThat(bounds?.minY).isEqualTo(134f)
        assertThat(bounds?.maxY).isEqualTo(646f)

        // External items on next row start at Y = 660, which is strictly > bounds.maxY (646f)
        val ext1Item = visibleItems.first { it.key == "tab_ext1" }
        val f2Item = visibleItems.first { it.key == "folder_f2" }
        assertThat(ext1Item.offset.y.toFloat()).isGreaterThan(bounds!!.maxY)
        assertThat(f2Item.offset.y.toFloat()).isGreaterThan(bounds.maxY)
    }

    @Test
    fun folderSpacer_cannotBeDraggedOrLongPressed() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = "f1"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TabOverviewGrid(
                    uiState = BrowserUiState(
                        tabs = tabs,
                        folders = listOf(folder),
                        activeTabId = "t1",
                    ),
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.waitForIdle()

        // Expand folder
        composeTestRule.onNodeWithTag("folder_card_f1").performClick()
        composeTestRule.waitForIdle()

        val spacerNode = composeTestRule.onNodeWithTag("folder_spacer_f1_0")
        spacerNode.assertExists()

        // Long press on spacer should not open edit folder sheet or trigger drag proxy
        spacerNode.performTouchInput {
            down(center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)
        spacerNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drag_proxy").assertDoesNotExist()
        assertThat(events.filterIsInstance<BrowserUiEvent.OpenEditFolderSheet>()).isEmpty()
    }

    @Test
    fun renderTabOverviewGridExpansionPreviews() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val folder = TabFolder(
            id = "f1",
            title = "Work & Research",
            color = 0xFF2196F3.toInt(),
        )
        val tabs = listOf(
            Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin", folderId = "f1"),
            Tab(id = "2", url = "https://developer.android.com", title = "Android", folderId = "f1"),
            Tab(id = "3", url = "https://github.com", title = "GitHub", folderId = "f1"),
            Tab(id = "4", url = "https://duckduckgo.com", title = "DuckDuckGo", folderId = null),
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF9EACB8))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("Expanded In-Grid Header (Light)", fontSize = 13.sp, color = Color.Black)
                    FolderOverviewGridHeaderLightPreview()

                    Text("Floating Sticky Header (Dark)", fontSize = 13.sp, color = Color.White)
                    FolderOverviewGridHeaderDarkPreview()

                    Text("Full Grid with Expanded Folder (Light)", fontSize = 13.sp, color = Color.Black)
                    Box(modifier = Modifier.size(380.dp, 400.dp)) {
                        JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                            TabOverviewGrid(
                                uiState = BrowserUiState(
                                    tabs = tabs,
                                    folders = listOf(folder),
                                    activeTabId = "1",
                                ),
                                onEvent = {},
                            )
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)

        val width = 440
        val height = 1100
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputDir = File("build/reports/previews")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "TabOverviewGridExpansionTest.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val buildDir = File("build")
        buildDir.mkdirs()
        val buildOutputFile = File(buildDir, "tab_overview_grid_expansion_preview.png")
        FileOutputStream(buildOutputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val rootBuildDir = File("../build")
        if (rootBuildDir.exists() || File("..").isDirectory) {
            rootBuildDir.mkdirs()
            val rootBuildOutputFile = File(rootBuildDir, "tab_overview_grid_expansion_preview.png")
            FileOutputStream(rootBuildOutputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
        assertThat(buildOutputFile.exists()).isTrue()
        assertThat(buildOutputFile.length()).isGreaterThan(0L)
    }
}
