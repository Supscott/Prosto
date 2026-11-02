package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
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
class FolderEditBottomSheetPreviewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testCreateFolder_rendersHeaderAndCreateButton() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Folder name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").assertIsDisplayed()
    }

    @Test
    fun testEditFolder_rendersEditHeaderAndSaveButton() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                FolderEditBottomSheetContent(
                    isDark = true,
                    isEditing = true,
                    initialTitle = "Work",
                    initialColor = TabFolder.PALETTE[1],
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Edit folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        // Live preview chip and text field both show "Work"
        assertThat(composeTestRule.onAllNodesWithText("Work").fetchSemanticsNodes().size).isAtLeast(1)
    }

    @Test
    fun testClearButton_clearsInput() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "Temporary",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Clear name").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear name").performClick()
        composeTestRule.waitForIdle()

        // After clearing, placeholder should be visible
        composeTestRule.onNodeWithText("Folder name").assertIsDisplayed()
    }

    @Test
    fun testSaveButton_invokesCallbackWithUpdatedValues() {
        var savedTitle: String? = null
        var savedColor: Int? = null

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = {},
                    onSave = { title, color ->
                        savedTitle = title
                        savedColor = color
                    },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Folder name").performTextInput("Finance")
        composeTestRule.waitForIdle()

        // Select the third color
        val colorOptions = composeTestRule.onAllNodesWithContentDescription("Select color")
        if (colorOptions.fetchSemanticsNodes().size >= 3) {
            colorOptions[2].performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()

        assertThat(savedTitle).isEqualTo("Finance")
        assertThat(savedColor).isEqualTo(TabFolder.PALETTE[2])
    }

    @Test
    fun testDismissButton_invokesOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FolderEditBottomSheetContent(
                    isDark = false,
                    isEditing = false,
                    initialTitle = "",
                    initialColor = TabFolder.PALETTE.first(),
                    onDismiss = { dismissed = true },
                    onSave = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun renderFolderEditBottomSheetPreviews() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF9EACB8))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("Light Theme - Creation", fontSize = 13.sp, color = Color.Black)
                    FolderEditBottomSheetLightPreview()

                    Text("Dark Theme - Edit", fontSize = 13.sp, color = Color.White)
                    FolderEditBottomSheetDarkPreview()
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
        val outputFile = File(outputDir, "FolderEditBottomSheetPreviewTest.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val buildDir = File("build")
        buildDir.mkdirs()
        val buildOutputFile = File(buildDir, "folder_edit_bottom_sheet_preview.png")
        FileOutputStream(buildOutputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
