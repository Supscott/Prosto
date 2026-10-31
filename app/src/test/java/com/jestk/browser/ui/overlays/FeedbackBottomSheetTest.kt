package com.jestk.browser.ui.overlays

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackBottomSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testRendering_displaysHeaderAndBothFeedbackChannels() {
        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FeedbackBottomSheet(
                    isDark = false,
                    onDismiss = {},
                    onFeedbackSubmitted = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Feedback & Community").assertIsDisplayed()
        composeTestRule.onNodeWithText("Telegram Community Chat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discuss features, ask questions, and share thoughts").assertIsDisplayed()
        composeTestRule.onNodeWithText("GitHub Issue Tracker").assertIsDisplayed()
        composeTestRule.onNodeWithText("Report technical bugs, crashes, and suggest features").assertIsDisplayed()
    }

    @Test
    fun testDismissButton_invokesOnDismissCallback() {
        var dismissed = false

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FeedbackBottomSheet(
                    isDark = false,
                    onDismiss = { dismissed = true },
                    onFeedbackSubmitted = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun testTelegramClick_dispatchesIntentAndInvokesCallback() {
        var feedbackSubmitted = false

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FeedbackBottomSheet(
                    isDark = false,
                    onDismiss = {},
                    onFeedbackSubmitted = { feedbackSubmitted = true },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Telegram Community Chat").performClick()
        composeTestRule.waitForIdle()

        assertThat(feedbackSubmitted).isTrue()

        val shadowActivity = shadowOf(composeTestRule.activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(startedIntent.data).isEqualTo(Uri.parse(TELEGRAM_COMMUNITY_URL))
    }

    @Test
    fun testGitHubClick_dispatchesIntentAndInvokesCallback() {
        var feedbackSubmitted = false

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                FeedbackBottomSheet(
                    isDark = true,
                    onDismiss = {},
                    onFeedbackSubmitted = { feedbackSubmitted = true },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("GitHub Issue Tracker").performClick()
        composeTestRule.waitForIdle()

        assertThat(feedbackSubmitted).isTrue()

        val shadowActivity = shadowOf(composeTestRule.activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(startedIntent.data).isEqualTo(Uri.parse(GITHUB_ISSUES_URL))
    }

    @Test
    fun testLaunchFeedbackUrl_activityNotFound_showsToast() {
        val throwingContext = object : ContextWrapper(composeTestRule.activity) {
            override fun startActivity(intent: Intent?) {
                throw ActivityNotFoundException("No activity found to handle intent")
            }
        }

        ShadowToast.reset()
        launchFeedbackUrl(throwingContext, TELEGRAM_COMMUNITY_URL)

        val expectedToastText = composeTestRule.activity.getString(R.string.error_cannot_open_link)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(expectedToastText)
    }

    @Test
    fun testLaunchFeedbackUrl_securityException_showsToast() {
        val throwingContext = object : ContextWrapper(composeTestRule.activity) {
            override fun startActivity(intent: Intent?) {
                throw SecurityException("Permission denied")
            }
        }

        ShadowToast.reset()
        launchFeedbackUrl(throwingContext, GITHUB_ISSUES_URL)

        val expectedToastText = composeTestRule.activity.getString(R.string.error_cannot_open_link)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(expectedToastText)
    }

    @Test
    fun renderFeedbackBottomSheetPreviews() {
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
                    Text("Light Theme", fontSize = 13.sp, color = Color.Black)
                    FeedbackBottomSheetLightPreview()

                    Text("Dark Theme", fontSize = 13.sp, color = Color.White)
                    FeedbackBottomSheetDarkPreview()
                }
            }
        }

        activity.setContentView(composeView)

        val width = 440
        val height = 1000
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/reports/previews/FeedbackBottomSheetPreviewTest.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
