package com.jestk.browser.ui.overlays

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchOverlayInputTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchOverlay_typingText_dispatchesUpdateQueryEvent() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        // Initially placeholder text should exist
        composeTestRule.onNodeWithText("Search with DuckDuckGo").assertExists()

        // Type into the BasicTextField
        composeTestRule.onNodeWithText("Search with DuckDuckGo").performTextInput("android")

        val updateEvents = events.filterIsInstance<BrowserUiEvent.UpdateSearchQuery>()
        assertTrue("Expected UpdateSearchQuery events", updateEvents.isNotEmpty())
        assertEquals("android", updateEvents.last().query)
    }

    @Test
    fun searchOverlay_withPrefilledUrl_showsTextAndClearButtonClearsIt() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "https://example.com/test",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        // Prefilled text should exist
        composeTestRule.onNodeWithText("https://example.com/test").assertExists()

        // Click Clear button
        composeTestRule.onNodeWithContentDescription("Clear").assertExists()
        composeTestRule.onNodeWithContentDescription("Clear").performClick()

        val updateEvents = events.filterIsInstance<BrowserUiEvent.UpdateSearchQuery>()
        assertTrue("Expected UpdateSearchQuery on clear", updateEvents.isNotEmpty())
        assertEquals("", updateEvents.last().query)
    }

    @Test
    fun searchOverlay_submitButton_submitsExactCurrentText() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "jetpack compose",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Go").assertExists()
        composeTestRule.onNodeWithContentDescription("Go").performClick()

        val submitEvents = events.filterIsInstance<BrowserUiEvent.SubmitSearch>()
        assertEquals(1, submitEvents.size)
        assertEquals("jetpack compose", submitEvents.first().query)

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertEquals(1, dismissEvents.size)
    }

    @Test
    fun searchOverlay_insertHistoryQuery_extractsQueryAndUpdates() {
        val events = mutableListOf<BrowserUiEvent>()
        val history = listOf(
            HistoryItem(
                id = 1L,
                url = "https://duckduckgo.com/?q=android+studio",
                title = "Android Studio",
                timestamp = System.currentTimeMillis()
            )
        )
        val state = BrowserUiState(
            searchQuery = "",
            searchHistorySuggestions = history,
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        // Find the insert action button on the history item
        composeTestRule.onNodeWithContentDescription("Insert in search").assertExists()
        composeTestRule.onNodeWithContentDescription("Insert in search").performClick()

        val updateEvents = events.filterIsInstance<BrowserUiEvent.UpdateSearchQuery>()
        assertTrue("Expected UpdateSearchQuery from history insert", updateEvents.isNotEmpty())
        assertEquals("android studio", updateEvents.last().query)
    }
}
