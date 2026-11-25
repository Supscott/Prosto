package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.app.DockPromptHost
import com.jestk.browser.ui.components.LocalDockPromptAnimateEnter
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissClearHistoryPrompt
import com.jestk.browser.ui.viewmodel.RequestClearHistory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The Clear History button asks first; only the confirm button sends [BrowserUiEvent.ClearAllHistory]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClearHistoryConfirmPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<BrowserUiEvent>()

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun setScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDockPromptAnimateEnter provides false) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    // Mirrors LibraryEventHandler: the flag flips on request, dismiss and clear.
                    var state by remember { mutableStateOf(BrowserUiState()) }
                    val onEvent: (BrowserUiEvent) -> Unit = { event ->
                        sent += event
                        state = when (event) {
                            RequestClearHistory -> state.copy(showClearHistoryPrompt = true)
                            DismissClearHistoryPrompt, BrowserUiEvent.ClearAllHistory ->
                                state.copy(showClearHistoryPrompt = false)
                            else -> state
                        }
                    }
                    Box {
                        InsetGroupedHistoryCard(
                            items = emptyList(),
                            isDark = false,
                            isExpanded = false,
                            onToggleExpand = {},
                            onItemClick = {},
                            onInsertQuery = {},
                            onClearHistory = { onEvent(RequestClearHistory) },
                        )
                        DockPromptHost(state, onEvent, isDark = false)
                    }
                }
            }
        }
    }

    @Test
    fun clearHistoryShowsThePromptWithoutDeleting() {
        setScreen()
        composeRule.onNodeWithTag("clear_history_confirm_button").assertDoesNotExist()

        composeRule.onNodeWithTag("search_history_clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.clear_history_confirm_title)).assertIsDisplayed()
        assertThat(sent).doesNotContain(BrowserUiEvent.ClearAllHistory)
    }

    @Test
    fun confirmSendsClearAllHistoryOnce() {
        setScreen()
        composeRule.onNodeWithTag("search_history_clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("clear_history_confirm_button").performClick()
        composeRule.waitForIdle()

        assertThat(sent.count { it == BrowserUiEvent.ClearAllHistory }).isEqualTo(1)
        composeRule.onNodeWithTag("clear_history_confirm_button").assertDoesNotExist()
    }

    @Test
    fun cancelDismissesWithoutDeleting() {
        setScreen()
        composeRule.onNodeWithTag("search_history_clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("clear_history_cancel_button").performClick()
        composeRule.waitForIdle()

        assertThat(sent).doesNotContain(BrowserUiEvent.ClearAllHistory)
        assertThat(sent).contains(DismissClearHistoryPrompt)
        composeRule.onNodeWithTag("clear_history_confirm_button").assertDoesNotExist()
    }
}
