package com.jestk.browser.ui.overlays

import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
    fun searchOverlay_nonEmptyInput_showsClearButtonAndNoGoButton_submitsViaIme() {
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

        // Only Clear button exists; Go button is removed
        composeTestRule.onNodeWithContentDescription("Clear").assertExists()
        composeTestRule.onNodeWithContentDescription("Go").assertDoesNotExist()

        // Submit via IME Action
        composeTestRule.onNodeWithText("jetpack compose").performImeAction()

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

    @Test
    fun searchOverlay_doubleTapSearchEngineSelector_scrollsToFirstEngine() {
        val events = mutableListOf<BrowserUiEvent>()
        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.BRAVE),
            searchEngines = engines
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        // Double tap on the search engine selector button
        composeTestRule.onNodeWithContentDescription("Search engine selector")
            .assertExists()
            .performTouchInput {
                doubleClick()
            }

        composeTestRule.waitForIdle()

        val setEngineEvents = events.filterIsInstance<BrowserUiEvent.SetSearchEngine>()
        assertTrue("Expected SetSearchEngine event on double tap", setEngineEvents.isNotEmpty())
        assertEquals(SearchEngine.GOOGLE, setEngineEvents.last().engine)
    }

    @Test
    fun searchOverlay_singleTapSearchEngineSelector_opensDropdownMenu() {
        val events = mutableListOf<BrowserUiEvent>()
        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.BRAVE),
            searchEngines = engines
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) }
                )
            }
        }

        // Single tap on search engine selector
        composeTestRule.onNodeWithContentDescription("Search engine selector")
            .assertExists()
            .performTouchInput {
                click()
            }

        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        // Dropdown card should now be visible with the edit button
        composeTestRule.onNodeWithContentDescription("Edit Search Engines").assertExists()

        // No SetSearchEngine event should have been dispatched on single tap
        val setEngineEvents = events.filterIsInstance<BrowserUiEvent.SetSearchEngine>()
        assertTrue("Single tap should not change search engine", setEngineEvents.isEmpty())
    }
}
