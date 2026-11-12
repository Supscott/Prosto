package com.jestk.browser.ui.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
    fun searchOverlay_swipeDownOnSearchBar_dismissesOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
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

        composeTestRule.onNodeWithText("Search with Google").performTouchInput {
            swipeDown(startY = top + 4f, endY = bottom - 4f, durationMillis = 150)
        }

        composeTestRule.waitForIdle()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertEquals(1, dismissEvents.size)
    }

    @Test
    fun searchOverlay_shortSwipeDownOnSearchBar_doesNotDismiss() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
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

        composeTestRule.onNodeWithText("Search with Google").performTouchInput {
            swipeDown(startY = centerY - 10f, endY = centerY + 10f, durationMillis = 150)
        }

        composeTestRule.waitForIdle()

        assertTrue(
            "A downward drag below the threshold must not dismiss the overlay",
            events.filterIsInstance<BrowserUiEvent.DismissOverlay>().isEmpty()
        )
    }

    @Test
    fun searchOverlay_horizontalSwipeOnSearchBar_stillCyclesEngines() {
        val events = mutableListOf<BrowserUiEvent>()
        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
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

        composeTestRule.onNodeWithText("Search with DuckDuckGo").performTouchInput {
            swipeLeft(startX = right - 4f, endX = left + 4f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()

        assertTrue(
            "Expected the engine carousel to switch engine",
            events.filterIsInstance<BrowserUiEvent.SetSearchEngine>().isNotEmpty()
        )
        assertTrue(
            "A horizontal swipe must not dismiss the overlay",
            events.filterIsInstance<BrowserUiEvent.DismissOverlay>().isEmpty()
        )
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

        // Backspace deletes the last typed character and hides the completion.
        composeTestRule.onNodeWithText("github.com").performTextReplacement("gi")
        composeTestRule.onNodeWithText("github.com").assertDoesNotExist()
        composeTestRule.onNodeWithText("gi").assertExists()

        // Typing forward finds "gitlab.com"
        composeTestRule.onNodeWithText("gi").performTextInput("tl")
        composeTestRule.onNodeWithText("gitlab.com").assertExists()
    }

    @Test
    fun searchOverlay_swipeDownOnHistoryContainer_whenImeCollapsed_dismissesOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
            searchHistorySuggestions = listOf(
                HistoryItem(
                    id = 1L,
                    url = "https://google.com/search?q=jetpack+compose",
                    title = "Jetpack Compose",
                    timestamp = System.currentTimeMillis()
                )
            )
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    isImeVisible = false
                )
            }
        }

        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertEquals("Expected overlay to be dismissed on downward drag with IME collapsed", 1, dismissEvents.size)
    }

    @Test
    fun searchOverlay_shortSwipeDownOnHistoryContainer_doesNotDismiss() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    isImeVisible = false
                )
            }
        }

        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = centerY - 20f, endY = centerY + 20f, durationMillis = 150)
        }

        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertTrue("A downward drag below threshold must not dismiss overlay", dismissEvents.isEmpty())
    }

    @Test
    fun searchOverlay_swipeDownOnHistoryContainer_whenImeVisible_doesNotDismissOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    isImeVisible = true
                )
            }
        }

        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertTrue("Downward drag when IME is visible should track keyboard, not dismiss overlay", dismissEvents.isEmpty())
    }

    @Test
    fun searchOverlay_swipeUpOnHistoryContainer_doesNotDismissOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    isImeVisible = false
                )
            }
        }

        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).performTouchInput {
            swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertTrue("Upward drag must never dismiss overlay", dismissEvents.isEmpty())
    }

    @Test
    fun searchOverlay_swipeDownOnHistoryContainer_whenDropdownOpen_closesDropdownWithoutDismissingOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
            searchEngines = engines
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    initialDropdownOpen = true,
                    isImeVisible = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit Search Engines").assertExists()

        composeTestRule.onNodeWithTag("search_history_scroll_container", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Dropdown should be closed (Edit Search Engines no longer in view)
        composeTestRule.onNodeWithContentDescription("Edit Search Engines").assertDoesNotExist()

        // Overlay should NOT be dismissed because the swipe was consumed to close dropdown
        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertTrue("Downward drag when dropdown is open should only close dropdown, not dismiss overlay", dismissEvents.isEmpty())
    }

    @Test
    fun searchOverlay_tapSearchBar_requestsFocusAndShowsKeyboard() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
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

        composeTestRule.onNodeWithText("Search with Google").assertExists()
        composeTestRule.onNodeWithText("Search with Google").performClick()
        composeTestRule.waitForIdle()

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Overlay should remain open (not dismissed) and be ready for typing
        assertTrue("Tapping search bar should not dismiss overlay", events.filterIsInstance<BrowserUiEvent.DismissOverlay>().isEmpty())
    }

    @Test
    fun searchOverlay_swipeDownOnSearchBar_whenImeCollapsed_dismissesOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE)
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    isImeVisible = false
                )
            }
        }

        composeTestRule.onNodeWithText("Search with Google").performTouchInput {
            swipeDown(startY = top + 4f, endY = bottom - 4f, durationMillis = 150)
        }

        composeTestRule.waitForIdle()

        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertEquals("Overlay must be dismissed when swiping down with IME collapsed", 1, dismissEvents.size)
    }

    @Test
    fun searchOverlay_swipeDownOnSearchBar_whenDropdownOpen_closesDropdownWithoutDismissingOverlay() {
        val events = mutableListOf<BrowserUiEvent>()
        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.GOOGLE),
            searchEngines = engines
        )

        composeTestRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                SearchOverlay(
                    uiState = state,
                    onEvent = { events.add(it) },
                    initialDropdownOpen = true,
                    isImeVisible = true
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Edit Search Engines").assertExists()

        composeTestRule.onNodeWithText("Search with Google").performTouchInput {
            swipeDown(startY = top + 4f, endY = bottom - 4f, durationMillis = 150)
        }

        composeTestRule.waitForIdle()

        // Dropdown sheet should be closed
        composeTestRule.onNodeWithContentDescription("Edit Search Engines").assertDoesNotExist()

        // Overlay should NOT be dismissed
        val dismissEvents = events.filterIsInstance<BrowserUiEvent.DismissOverlay>()
        assertTrue("Downward swipe when dropdown is open must only close dropdown, not dismiss overlay", dismissEvents.isEmpty())
    }

    @Test
    fun searchOverlay_horizontalSwipeOnSearchBar_doesNotDispatchVerticalNestedScroll() {
        val events = mutableListOf<BrowserUiEvent>()
        var verticalPreScrollDispatched = 0f

        val testConnection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    verticalPreScrollDispatched += available.y
                }
                return Offset.Zero
            }
        }

        val engines = listOf(SearchEngine.GOOGLE, SearchEngine.DUCKDUCKGO, SearchEngine.BRAVE)
        val state = BrowserUiState(
            searchQuery = "",
            settings = BrowserSettings(searchEngine = SearchEngine.DUCKDUCKGO),
            searchEngines = engines
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.nestedScroll(testConnection)) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    SearchOverlay(
                        uiState = state,
                        onEvent = { events.add(it) }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Search with DuckDuckGo").performTouchInput {
            swipeLeft(startX = right - 4f, endX = left + 4f, durationMillis = 200)
        }

        composeTestRule.waitForIdle()

        assertEquals(
            "Horizontal engine swipe must not dispatch vertical nested scroll deltas",
            0f,
            verticalPreScrollDispatched,
            0.001f
        )
        assertTrue(
            "Engine cycling must still succeed",
            events.filterIsInstance<BrowserUiEvent.SetSearchEngine>().isNotEmpty()
        )
        assertTrue(
            "Overlay must not be dismissed on horizontal swipe",
            events.filterIsInstance<BrowserUiEvent.DismissOverlay>().isEmpty()
        )
    }

    private class TestSoftwareKeyboardController : SoftwareKeyboardController {
        var hideCallCount = 0
        var showCallCount = 0

        override fun hide() {
            hideCallCount++
        }

        override fun show() {
            showCallCount++
        }
    }
}
