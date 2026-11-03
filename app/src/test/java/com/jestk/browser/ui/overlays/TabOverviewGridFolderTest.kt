package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
class TabOverviewGridFolderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun folderOverviewGridCard_renders2x2GridAndTitle() {
        val folder = TabFolder(
            id = "f1",
            title = "Research",
            color = 0xFF2196F3.toInt(),
        )
        val tabs = listOf(
            Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin"),
            Tab(id = "2", url = "https://android.com", title = "Android"),
            Tab(id = "3", url = "https://github.com", title = "GitHub"),
            Tab(id = "4", url = "https://google.com", title = "Google"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.size(120.dp, 213.dp)) {
                    FolderOverviewGridCard(
                        folder = folder,
                        tabs = tabs,
                        isDark = false,
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Research", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_header_f1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_slot_f1_0", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_slot_f1_1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_slot_f1_2", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_slot_f1_3", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_overflow_f1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun folderOverviewGridCard_rendersOverflowBadgeWhenMoreThanFourTabs() {
        val folder = TabFolder(
            id = "f1",
            title = "Work",
            color = 0xFF10B981.toInt(),
        )
        val tabs5 = (1..5).map {
            Tab(id = "$it", url = "https://example.com/$it", title = "Tab $it")
        }

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.size(120.dp, 213.dp)) {
                    FolderOverviewGridCard(
                        folder = folder,
                        tabs = tabs5,
                        isDark = false,
                        onClick = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // tabs.size = 5 -> totalTabs - 3 = +2
        composeTestRule.onNodeWithTag("folder_overflow_f1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("+2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun folderOverviewGridCard_rendersMutedEmptySlotsWhenFewerThanFourTabs() {
        val folder = TabFolder(
            id = "f1",
            title = "",
            color = 0xFF8B5CF6.toInt(),
        )
        val tabs2 = listOf(
            Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin"),
            Tab(id = "2", url = "https://android.com", title = "Android"),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.size(120.dp, 213.dp)) {
                    FolderOverviewGridCard(
                        folder = folder,
                        tabs = tabs2,
                        isDark = false,
                        onClick = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Empty title fallback to tab count string
        composeTestRule.onNodeWithText("2 tabs", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_slot_f1_0", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_slot_f1_1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_empty_slot_f1_2", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("folder_empty_slot_f1_3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun searchFilter_matchesFolderNameAndTabsInsideFolder() {
        val folder1 = TabFolder(id = "f1", title = "Productivity", color = 0xFF2196F3.toInt())
        val folder2 = TabFolder(id = "f2", title = "Entertainment", color = 0xFFF59E0B.toInt())

        val tabs = listOf(
            Tab(id = "1", url = "https://notion.so", title = "Notion Work", folderId = "f1"),
            Tab(id = "2", url = "https://slack.com", title = "Slack Chat", folderId = "f1"),
            Tab(id = "3", url = "https://youtube.com", title = "YouTube", folderId = "f2"),
            Tab(id = "4", url = "https://news.ycombinator.com", title = "Hacker News", folderId = null),
        )

        // 1. Search by folder name "Productivity" (auto-expands matching folder with row spacer padding)
        val itemsFolderMatch = computeOverviewItems(
            tabs = tabs,
            folders = listOf(folder1, folder2),
            searchQuery = "Productivity",
        )
        assertThat(itemsFolderMatch).hasSize(4) // 1 Header + 2 Tab cards + 1 Spacer
        val header1 = itemsFolderMatch[0] as OverviewGridItem.FolderHeader
        assertThat(header1.folder.id).isEqualTo("f1")
        assertThat(header1.tabs).hasSize(2)
        val spacer1 = itemsFolderMatch[3] as OverviewGridItem.FolderSpacer
        assertThat(spacer1.folderId).isEqualTo("f1")
        assertThat(spacer1.index).isEqualTo(0)

        // 2. Search by tab title inside folder "Slack" (auto-expands with matched tab + 2 spacers)
        val itemsTabMatch = computeOverviewItems(
            tabs = tabs,
            folders = listOf(folder1, folder2),
            searchQuery = "Slack",
        )
        assertThat(itemsTabMatch).hasSize(4) // 1 Header + 1 Tab card + 2 Spacers
        val header2 = itemsTabMatch[0] as OverviewGridItem.FolderHeader
        assertThat(header2.folder.id).isEqualTo("f1")
        val tabCard = itemsTabMatch[1] as OverviewGridItem.FolderTabCard
        assertThat(tabCard.tab.id).isEqualTo("2")
        val tabSpacer0 = itemsTabMatch[2] as OverviewGridItem.FolderSpacer
        val tabSpacer1 = itemsTabMatch[3] as OverviewGridItem.FolderSpacer
        assertThat(tabSpacer0.folderId).isEqualTo("f1")
        assertThat(tabSpacer0.index).isEqualTo(0)
        assertThat(tabSpacer1.folderId).isEqualTo("f1")
        assertThat(tabSpacer1.index).isEqualTo(1)

        // 3. Search standalone tab "Hacker"
        val itemsStandalone = computeOverviewItems(
            tabs = tabs,
            folders = listOf(folder1, folder2),
            searchQuery = "Hacker",
        )
        assertThat(itemsStandalone).hasSize(1)
        val tabItem = itemsStandalone[0] as OverviewGridItem.TabCard
        assertThat(tabItem.tab.id).isEqualTo("4")

        // 4. Non-matching query
        val itemsNone = computeOverviewItems(
            tabs = tabs,
            folders = listOf(folder1, folder2),
            searchQuery = "NonExistentQuery",
        )
        assertThat(itemsNone).isEmpty()
    }

    @Test
    fun dragTabOntoTab_triggersCreateFolderFromTabs() {
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

        gridNode.performTouchInput {
            moveTo(t2Center)
        }
        composeTestRule.mainClock.advanceTimeBy(500)

        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val folderCreationEvent = events.filterIsInstance<BrowserUiEvent.CreateFolderFromTabs>().firstOrNull()
        assertThat(folderCreationEvent).isNotNull()
        assertThat(folderCreationEvent?.tabId1).isEqualTo("t1")
        assertThat(folderCreationEvent?.tabId2).isEqualTo("t2")
    }

    @Test
    fun dragTabOntoFolder_triggersAddTabToFolder() {
        val events = mutableListOf<BrowserUiEvent>()
        val folder = TabFolder(id = "f1", title = "Work", color = 0xFF2196F3.toInt())
        val tabs = listOf(
            Tab(id = "t1", url = "https://one.com", title = "Tab One", folderId = null),
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
                    onEvent = { events.add(it) },
                )
            }
        }

        composeTestRule.waitForIdle()

        val gridNode = composeTestRule.onNodeWithTag("overview_grid")
        val gridBounds = gridNode.fetchSemanticsNode().boundsInRoot
        val t1Center = composeTestRule.onNodeWithTag("tab_card_t1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft
        val folderCenter = composeTestRule.onNodeWithTag("folder_card_f1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - gridBounds.topLeft

        gridNode.performTouchInput {
            down(t1Center)
        }
        composeTestRule.mainClock.advanceTimeBy(700)

        gridNode.performTouchInput {
            moveTo(folderCenter)
        }
        composeTestRule.mainClock.advanceTimeBy(500)

        gridNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        val addToFolderEvent = events.filterIsInstance<BrowserUiEvent.AddTabToFolder>().firstOrNull()
        assertThat(addToFolderEvent).isNotNull()
        assertThat(addToFolderEvent?.tabId).isEqualTo("t1")
        assertThat(addToFolderEvent?.folderId).isEqualTo("f1")
    }

    @Test
    fun renderTabOverviewGridFolderPreviews() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF9EACB8))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Light (5 tabs)", fontSize = 12.sp, color = Color.Black)
                        Box(modifier = Modifier.size(120.dp, 213.dp)) {
                            FolderOverviewGridCardLightPreview()
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dark (5 tabs)", fontSize = 12.sp, color = Color.White)
                        Box(modifier = Modifier.size(120.dp, 213.dp)) {
                            FolderOverviewGridCardDarkPreview()
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Empty Slots", fontSize = 12.sp, color = Color.Black)
                        Box(modifier = Modifier.size(120.dp, 213.dp)) {
                            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                                FolderOverviewGridCard(
                                    folder = TabFolder(id = "f2", title = "Social", color = 0xFF8B5CF6.toInt()),
                                    tabs = listOf(
                                        Tab(id = "1", url = "https://x.com", title = "X"),
                                        Tab(id = "2", url = "https://reddit.com", title = "Reddit"),
                                    ),
                                    isDark = false,
                                    onClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)

        val width = 440
        val height = 280
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputDir = File("build/reports/previews")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "TabOverviewGridFolderTest.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val buildDir = File("build")
        buildDir.mkdirs()
        val buildOutputFile = File(buildDir, "tab_overview_grid_folder_preview.png")
        FileOutputStream(buildOutputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
