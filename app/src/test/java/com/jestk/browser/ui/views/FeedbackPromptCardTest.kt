package com.jestk.browser.ui.views

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
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
class FeedbackPromptCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun displaysTitleDescriptionIconAndButtons_lightMode() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FeedbackPromptCard(
                    isDark = false,
                    onRateClick = {},
                    onSnoozeClick = {},
                    onDismissClick = {},
                )
            }
        }

        val title = context.getString(R.string.feedback_card_title)
        val desc = context.getString(R.string.feedback_card_desc)
        val rateAction = context.getString(R.string.feedback_card_action_rate)
        val laterAction = context.getString(R.string.feedback_card_action_later)
        val dismissDesc = context.getString(R.string.dismiss)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(desc).assertIsDisplayed()
        composeRule.onNodeWithText(rateAction).assertIsDisplayed()
        composeRule.onNodeWithText(laterAction).assertIsDisplayed()
        composeRule.onNodeWithTag("feedback_card_icon").assertExists()
        composeRule.onNodeWithContentDescription(dismissDesc).assertIsDisplayed()
    }

    @Test
    fun displaysTitleDescriptionIconAndButtons_darkMode() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                FeedbackPromptCard(
                    isDark = true,
                    onRateClick = {},
                    onSnoozeClick = {},
                    onDismissClick = {},
                )
            }
        }

        val title = context.getString(R.string.feedback_card_title)
        val desc = context.getString(R.string.feedback_card_desc)
        val rateAction = context.getString(R.string.feedback_card_action_rate)
        val laterAction = context.getString(R.string.feedback_card_action_later)
        val dismissDesc = context.getString(R.string.dismiss)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(desc).assertIsDisplayed()
        composeRule.onNodeWithText(rateAction).assertIsDisplayed()
        composeRule.onNodeWithText(laterAction).assertIsDisplayed()
        composeRule.onNodeWithTag("feedback_card_icon").assertExists()
        composeRule.onNodeWithContentDescription(dismissDesc).assertIsDisplayed()
    }

    @Test
    fun clickRateButton_triggersCallback() {
        var rateClicked = false

        composeRule.setContent {
            JestkBrowserTheme {
                FeedbackPromptCard(
                    isDark = false,
                    onRateClick = { rateClicked = true },
                    onSnoozeClick = {},
                    onDismissClick = {},
                )
            }
        }

        val rateAction = context.getString(R.string.feedback_card_action_rate)
        composeRule.onNodeWithText(rateAction).assertIsDisplayed().performClick()

        assertThat(rateClicked).isTrue()
    }

    @Test
    fun clickSnoozeButton_triggersCallback() {
        var snoozeClicked = false

        composeRule.setContent {
            JestkBrowserTheme {
                FeedbackPromptCard(
                    isDark = false,
                    onRateClick = {},
                    onSnoozeClick = { snoozeClicked = true },
                    onDismissClick = {},
                )
            }
        }

        val laterAction = context.getString(R.string.feedback_card_action_later)
        composeRule.onNodeWithText(laterAction).assertIsDisplayed().performClick()

        assertThat(snoozeClicked).isTrue()
    }

    @Test
    fun clickDismissButton_triggersCallback() {
        var dismissClicked = false

        composeRule.setContent {
            JestkBrowserTheme {
                FeedbackPromptCard(
                    isDark = false,
                    onRateClick = {},
                    onSnoozeClick = {},
                    onDismissClick = { dismissClicked = true },
                )
            }
        }

        val dismissDesc = context.getString(R.string.dismiss)
        composeRule.onNodeWithContentDescription(dismissDesc).assertIsDisplayed().performClick()

        assertThat(dismissClicked).isTrue()
    }

    @Test
    fun homeScreen_whenFeedbackCardVisible_displaysCardAndDispatchesEvents() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            isFeedbackCardVisible = true,
            isDefaultBrowser = true,
        )

        composeRule.setContent {
            JestkBrowserTheme {
                HomeScreen(
                    uiState = state,
                    onEvent = { events.add(it) },
                )
            }
        }

        val title = context.getString(R.string.feedback_card_title)
        val rateAction = context.getString(R.string.feedback_card_action_rate)
        val laterAction = context.getString(R.string.feedback_card_action_later)
        val dismissDesc = context.getString(R.string.dismiss)

        composeRule.onNodeWithText(title).assertIsDisplayed()

        composeRule.onNodeWithText(rateAction).performClick()
        assertThat(events.last()).isEqualTo(BrowserUiEvent.OpenFeedbackSheet)

        composeRule.onNodeWithText(laterAction).performClick()
        assertThat(events.last()).isEqualTo(BrowserUiEvent.SnoozeFeedbackPrompt())

        composeRule.onNodeWithContentDescription(dismissDesc).performClick()
        assertThat(events.last()).isEqualTo(BrowserUiEvent.DismissFeedbackPrompt)
    }

    @Test
    fun homeScreen_whenFeedbackCardNotVisible_cardIsHidden() {
        val state = BrowserUiState(
            isFeedbackCardVisible = false,
            isDefaultBrowser = true,
        )

        composeRule.setContent {
            JestkBrowserTheme {
                HomeScreen(
                    uiState = state,
                    onEvent = {},
                )
            }
        }

        val title = context.getString(R.string.feedback_card_title)
        composeRule.onNodeWithText(title).assertDoesNotExist()
    }
}
