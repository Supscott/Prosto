package com.jestk.browser.ui.overlays

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
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
    fun searchOverlay_typingDomainPrefix_autocompletesCandidate_andSubmitsFullUrl() {
        val events = mutableListOf<BrowserUiEvent>()
        val favorites = listOf(
            FavoriteItem(id = 1, title = "GitHub", url = "https://github.com/torvalds"),
        )
        val state = BrowserUiState(
            searchQuery = "",
            favorites = favorites,
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                )
            }
        }

        // Type prefix "git"
        composeTestRule.onNodeWithText("Search with Google").performTextInput("git")

        // Input should now display the autocompleted domain "github.com"
        composeTestRule.onNodeWithText("github.com").assertExists()

        // Submit via IME action
        composeTestRule.onNodeWithText("github.com").performImeAction()

        val submitEvents = events.filterIsInstance<BrowserUiEvent.SubmitSearch>()
        assertTrue("Expected SubmitSearch event", submitEvents.isNotEmpty())
        assertEquals("github.com", submitEvents.last().query)
    }

    @Test
    fun searchOverlay_matchingOpenTab_displaysSwitchToTabCapsule_andClicks() {
        val events = mutableListOf<BrowserUiEvent>()
        val tabs = listOf(
            Tab(id = "tab-yt", title = "YouTube", url = "https://youtube.com/watch?v=123", lastActiveTimestamp = 100),
        )
        val state = BrowserUiState(
            searchQuery = "",
            tabs = tabs,
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                )
            }
        }

        // Type prefix "you"
        composeTestRule.onNodeWithText("Search with Google").performTextInput("you")

        // Capsule should be visible with Switch badge and title
        composeTestRule.onNodeWithContentDescription("Switch to open tab: YouTube").assertExists()
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Switch"))[0].assertExists()

        // Click on the open tab capsule
        composeTestRule.onNodeWithContentDescription("Switch to open tab: YouTube").performClick()

        val selectTabEvents = events.filterIsInstance<BrowserUiEvent.SelectTab>()
        assertEquals(1, selectTabEvents.size)
        assertEquals("tab-yt", selectTabEvents.first().tabId)

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertEquals(1, dismissEvents.size)
    }

    @Test
    fun searchOverlay_backspaceOnAutocomplete_cancelsAutocompletion_withoutTrappingUser() {
        val events = mutableListOf<BrowserUiEvent>()
        val favorites = listOf(
            FavoriteItem(id = 1, title = "GitHub", url = "https://github.com/torvalds"),
            FavoriteItem(id = 2, title = "GitLab", url = "https://gitlab.com/repo"),
        )
        val state = BrowserUiState(
            searchQuery = "",
            favorites = favorites,
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                )
            }
        }

        // Type prefix "git"
        composeTestRule.onNodeWithText("Search with Google").performTextInput("git")
        composeTestRule.onNodeWithText("github.com").assertExists()

        // Simulate Backspace: user removes the autocompleted selection back to "git"
        composeTestRule.onNodeWithText("github.com").performTextInput("") // replaces selection with empty
        // Verify text is now "git"
        composeTestRule.onNodeWithText("git").assertExists()

        // Typing forward 'l' resets suppression and finds "gitlab.com"
        composeTestRule.onNodeWithText("git").performTextInput("l")
        composeTestRule.onNodeWithText("gitlab.com").assertExists()
    }
}
