package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderOverviewGridHeaderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun folderOverviewGridHeader_rendersEditIconAndTriggersCallback() {
        var editClicked = false
        var collapseClicked = false

        val folder = TabFolder(
            id = "f1",
            title = "Work",
            color = 0xFF2196F3.toInt(),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.padding(16.dp)) {
                    FolderOverviewGridHeader(
                        folder = folder,
                        tabCount = 3,
                        onClick = { collapseClicked = true },
                        onLongClick = {},
                        onOpenEditSheet = { editClicked = true },
                        isSticky = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. Verify edit button is displayed with correct testTag
        val editButton = composeTestRule.onNodeWithTag("folder_header_edit_f1", useUnmergedTree = true)
        editButton.assertIsDisplayed()

        // 2. Click edit button -> invokes onOpenEditSheet, does NOT collapse
        editButton.performClick()
        composeTestRule.waitForIdle()

        assertThat(editClicked).isTrue()
        assertThat(collapseClicked).isFalse()

        // 3. Click the header surface -> invokes collapse onClick
        composeTestRule.onNodeWithTag("folder_in_grid_header_f1").performClick()
        composeTestRule.waitForIdle()

        assertThat(collapseClicked).isTrue()
    }

    @Test
    fun stickyFolderOverviewGridHeader_rendersEditIconWithStickyTag() {
        var editClicked = false

        val folder = TabFolder(
            id = "f2",
            title = "Research",
            color = 0xFF8B5CF6.toInt(),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    FolderOverviewGridHeader(
                        folder = folder,
                        tabCount = 5,
                        onClick = {},
                        onLongClick = {},
                        onOpenEditSheet = { editClicked = true },
                        isSticky = true,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val stickyEditButton = composeTestRule.onNodeWithTag("folder_sticky_header_edit_f2", useUnmergedTree = true)
        stickyEditButton.assertIsDisplayed()

        stickyEditButton.performClick()
        composeTestRule.waitForIdle()

        assertThat(editClicked).isTrue()
    }
}
