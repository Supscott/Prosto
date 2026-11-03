package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
class FolderEditBottomSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testTitleInput_allowsUpTo12Characters() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, _ -> savedTitle = title },
                )
            }
        }
        composeTestRule.waitForIdle()

        // 10-character Russian string: "Разработка"
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Разработка")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()
        assertThat(savedTitle).isEqualTo("Разработка")

        // 12-character English string: "123456789012"
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("123456789012")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()
        assertThat(savedTitle).isEqualTo("123456789012")
    }

    @Test
    fun testTitleInput_rejectsInputLongerThan12Characters() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, _ -> savedTitle = title },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Attempting to type or replace with 13 characters
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("1234567890123")
        composeTestRule.waitForIdle()

        // Since replacing with 13 chars is rejected, the text field should remain empty
        composeTestRule.onNodeWithText("Folder name").assertIsDisplayed()

        // Enter exactly 12 characters first
        composeTestRule.onNode(hasSetTextAction()).performTextInput("123456789012")
        composeTestRule.waitForIdle()

        // Attempt to append an extra 13th character
        composeTestRule.onNode(hasSetTextAction()).performTextInput("3")
        composeTestRule.waitForIdle()

        // Title should remain 12 characters
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()
        assertThat(savedTitle).isEqualTo("123456789012")
    }

    @Test
    fun testInitialTitle_longerThan12Characters_isClampedTo12Characters() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = true,
                    initialTitle = "12345678901234567890",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, _ -> savedTitle = title },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        assertThat(savedTitle).isEqualTo("123456789012")
    }

    @Test
    fun testSaveTitle_trimsWhitespaceAndClampsTo12Characters() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, _ -> savedTitle = title },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Input with whitespace: "  Work  " (total 8 chars, trimmed to 4 chars)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("  Work  ")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()

        assertThat(savedTitle).isEqualTo("Work")
    }

    @Test
    fun testDoneImeAction_savesTitleUpTo12Characters() {
        var savedTitle: String? = null
        var savedColor: Int? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE[3],
                    onDismiss = {},
                    onSave = { title, color ->
                        savedTitle = title
                        savedColor = color
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Finance")
        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        composeTestRule.waitForIdle()

        assertThat(savedTitle).isEqualTo("Finance")
        assertThat(savedColor).isEqualTo(TabFolder.PALETTE[3])
    }

    @Test
    fun testClearButton_allowsReEnteringNewTitle() {
        var savedTitle: String? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "OldTitle",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, _ -> savedTitle = title },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Clear name").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("NewTitle")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()

        assertThat(savedTitle).isEqualTo("NewTitle")
    }
}
