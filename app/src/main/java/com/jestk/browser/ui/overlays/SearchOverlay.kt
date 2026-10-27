package com.jestk.browser.ui.overlays

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.SearchInputAutocompleteMatcher
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import kotlinx.coroutines.flow.filter

/**
 * Extracts the `q` parameter from a search-engine history URL.
 * Falls back to the item title (or URL) when the query is missing or the URL cannot be parsed.
 */
private fun historyItemQuery(item: HistoryItem): String {
    val fallback = item.title.ifEmpty { item.url }
    return try {
        Uri.parse(item.url).getQueryParameter("q") ?: fallback
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Search & History Overlay - Prosto Canvas design system (NO BLUR).
 * Orchestrator composable assembling:
 * - Prosto sky canvas background
 * - Upward-expanding paginated history stack ([InsetGroupedHistoryCard])
 * - Quick favorites section ([SearchFavoritesSection])
 * - Capsule search input bar ([HeroSearchInputField])
 * - Search engine selector carousel button ([SearchEngineSelectorButton])
 * - Search engine dropdown management sheet ([SearchEngineDropdownCard])
 */
@Composable
fun SearchOverlay(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialDropdownOpen: Boolean = false,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isDark = LocalIsDarkTheme.current
    val vibration = LocalVibrationFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isDropdownOpen by remember { mutableStateOf(initialDropdownOpen) }

    val carouselState =
        rememberSearchEngineCarouselState(
            initialEngine = uiState.settings.searchEngine,
            engines = uiState.searchEngines,
            density = density,
            coroutineScope = coroutineScope,
            vibration = vibration,
            onEngineSelected = { target ->
                onEvent(BrowserUiEvent.SetSearchEngine(target))
            },
        )

    LaunchedEffect(uiState.settings.searchEngine) {
        if (carouselState.currentEngine != uiState.settings.searchEngine && !carouselState.isScrolling) {
            carouselState.currentEngine = uiState.settings.searchEngine
        }
    }

    BackHandler(enabled = isDropdownOpen) {
        isDropdownOpen = false
    }

    val autocompleteMatcher = remember { SearchInputAutocompleteMatcher() }
    var suppressAutocomplete by remember { mutableStateOf(false) }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.searchQuery,
                selection =
                    if (uiState.searchQuery.isNotEmpty()) {
                        TextRange(0, uiState.searchQuery.length)
                    } else {
                        TextRange.Zero
                    },
            ),
        )
    }

    val activeQuery = remember(textFieldValue) {
        if (!textFieldValue.selection.collapsed) {
            textFieldValue.text.substring(0, textFieldValue.selection.min)
        } else {
            textFieldValue.text
        }
    }
    val matchingOpenTabs = remember(activeQuery, uiState.tabs) {
        val inactiveTabs = uiState.tabs.filterNot { it.isActive }
        autocompleteMatcher.findMatchingOpenTabs(activeQuery, inactiveTabs)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isDropdownOpen) {
                            isDropdownOpen = false
                        } else {
                            onEvent(BrowserUiEvent.DismissOverlay)
                        }
                    },
                ),
    ) {
        // Layer 1: Ambient background
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .prostoSkyBackground(isDark = isDark),
        )

        // Layer 2: Foreground content containing solid cards & input
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val scrollState = rememberScrollState()

            var prevSize by remember { mutableIntStateOf(uiState.searchHistorySuggestions.size) }
            var prevMaxValue by remember { mutableIntStateOf(0) }

            // Scroll to bottom on initial open, expansion toggle, or query change
            LaunchedEffect(uiState.isSearchHistoryExpanded, textFieldValue.text) {
                if (scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
                prevSize = uiState.searchHistorySuggestions.size
                prevMaxValue = scrollState.maxValue
            }

            // Smooth scroll preservation on pagination, or bottom-scroll on initial layout
            LaunchedEffect(scrollState.maxValue) {
                val currentSize = uiState.searchHistorySuggestions.size
                val currentMax = scrollState.maxValue
                if (currentSize > prevSize && prevSize > 0) {
                    val delta = currentMax - prevMaxValue
                    if (delta > 0) {
                        scrollState.scrollTo(scrollState.value + delta)
                    }
                } else if (prevSize == 0 && currentMax > 0) {
                    scrollState.scrollTo(currentMax)
                }
                prevSize = currentSize
                prevMaxValue = currentMax
            }

            // Pagination trigger: When user scrolls to the end of the loaded list (top of reversed list)
            LaunchedEffect(
                uiState.isSearchHistoryExpanded,
                uiState.hasMoreHistory,
                uiState.isLoadingMoreHistory,
            ) {
                snapshotFlow { scrollState.value }
                    .filter { it <= 20 }
                    .collect {
                        if (uiState.isSearchHistoryExpanded &&
                            uiState.hasMoreHistory &&
                            !uiState.isLoadingMoreHistory &&
                            uiState.searchHistorySuggestions.isNotEmpty()
                        ) {
                            onEvent(BrowserUiEvent.LoadMoreSearchHistory)
                        }
                    }
            }

            // Top Area: Scrollable column containing history suggestions
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                // Search History Suggestions starting from the bottom
                val suggestions = uiState.searchHistorySuggestions
                if (suggestions.isNotEmpty()) {
                    InsetGroupedHistoryCard(
                        items = suggestions,
                        isDark = isDark,
                        isExpanded = uiState.isSearchHistoryExpanded,
                        isLoadingMore = uiState.isLoadingMoreHistory,
                        onToggleExpand = {
                            keyboardController?.hide()
                            onEvent(BrowserUiEvent.ToggleSearchHistoryExpanded)
                        },
                        onItemClick = { item ->
                            onEvent(BrowserUiEvent.LoadUrl(item.url))
                            onEvent(BrowserUiEvent.DismissOverlay)
                        },
                        onInsertQuery = { item ->
                            val queryText =
                                when {
                                    item.url.contains("duckduckgo.com/?q=") -> historyItemQuery(item)

                                    item.url.contains("google.com/search?q=") -> historyItemQuery(item)

                                    item.url.contains("bing.com/search?q=") -> historyItemQuery(item)

                                    item.title.isNotEmpty() &&
                                        !item.title.startsWith(
                                            "http://",
                                        ) && !item.title.startsWith("https://") -> item.title

                                    else -> item.url
                                }
                            suppressAutocomplete = true
                            textFieldValue =
                                TextFieldValue(
                                    text = queryText,
                                    selection = TextRange(queryText.length),
                                )
                            onEvent(BrowserUiEvent.UpdateSearchQuery(queryText))
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        onClearHistory = {
                            onEvent(BrowserUiEvent.ClearAllHistory)
                        },
                    )
                } else {
                    HistoryEmptyCard(isDark = isDark)
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Favorites squircle cards placed directly above the open tabs carousel and input bar
            if (!uiState.isSearchHistoryExpanded && uiState.favorites.isNotEmpty()) {
                SearchFavoritesSection(
                    favorites = uiState.favorites,
                    isDark = isDark,
                    onSelect = { url ->
                        onEvent(BrowserUiEvent.LoadUrl(url))
                        onEvent(BrowserUiEvent.DismissOverlay)
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Open Tab Switch Carousel (matching open tabs displayed directly above the input)
            if (matchingOpenTabs.isNotEmpty()) {
                OpenTabSwitchCarousel(
                    tabs = matchingOpenTabs,
                    isDark = isDark,
                    onTabClick = { tab ->
                        vibration?.tick()
                        onEvent(BrowserUiEvent.SelectTab(tab.id))
                        onEvent(BrowserUiEvent.DismissOverlay)
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Bottom Input Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}, // Intercept dismiss clicks
                        ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroSearchInputField(
                    textFieldValue = textFieldValue,
                    onValueChange = { newValue ->
                        val matchedEngine =
                            uiState.searchEngines.firstOrNull { engine ->
                                !engine.shortcut.isNullOrBlank() &&
                                    newValue.text.startsWith("${engine.shortcut} ", ignoreCase = true)
                            }
                        if (matchedEngine != null) {
                            suppressAutocomplete = false
                            carouselState.animateToEngine(matchedEngine)
                            val remaining = newValue.text.substring(matchedEngine.shortcut!!.length + 1)
                            textFieldValue =
                                TextFieldValue(
                                    text = remaining,
                                    selection = TextRange(remaining.length),
                                )
                            onEvent(BrowserUiEvent.UpdateSearchQuery(remaining))
                            return@HeroSearchInputField
                        }

                        val oldText = textFieldValue.text
                        val newText = newValue.text
                        val hadSelection = !textFieldValue.selection.collapsed

                        val applyAutocomplete: (String, TextFieldValue) -> Unit = { query, fallbackValue ->
                            val match = autocompleteMatcher.findInlineMatch(
                                query = query,
                                tabs = uiState.tabs,
                                favorites = uiState.favorites,
                                history = uiState.searchHistorySuggestions,
                            )
                            if (match != null) {
                                textFieldValue = TextFieldValue(
                                    text = match,
                                    selection = TextRange(query.length, match.length),
                                )
                                onEvent(BrowserUiEvent.UpdateSearchQuery(query))
                            } else {
                                textFieldValue = fallbackValue
                                onEvent(BrowserUiEvent.UpdateSearchQuery(query))
                            }
                        }

                        if (hadSelection) {
                            val textWithoutSelection = oldText.removeRange(
                                textFieldValue.selection.min,
                                textFieldValue.selection.max,
                            )
                            if (newText == textWithoutSelection) {
                                // User pressed Backspace deleting the autocompleted selection
                                suppressAutocomplete = true
                                textFieldValue = newValue
                                onEvent(BrowserUiEvent.UpdateSearchQuery(newText))
                            } else {
                                // User typed a character replacing the selection
                                suppressAutocomplete = false
                                applyAutocomplete(newText, newValue)
                            }
                        } else {
                            if (newText.length < oldText.length) {
                                // User deleted a character
                                suppressAutocomplete = true
                                textFieldValue = newValue
                                onEvent(BrowserUiEvent.UpdateSearchQuery(newText))
                            } else if (newText == oldText) {
                                // Selection or cursor moved
                                textFieldValue = newValue
                            } else {
                                // User typed forward
                                suppressAutocomplete = false
                                applyAutocomplete(newText, newValue)
                            }
                        }
                    },
                    searchEngine = carouselState.currentEngine,
                    isDark = isDark,
                    focusRequester = focusRequester,
                    onSubmit = {
                        onEvent(BrowserUiEvent.SubmitSearch(textFieldValue.text))
                        onEvent(BrowserUiEvent.DismissOverlay)
                    },
                    onClear = {
                        suppressAutocomplete = false
                        textFieldValue = TextFieldValue("", TextRange.Zero)
                        onEvent(BrowserUiEvent.UpdateSearchQuery(""))
                    },
                    onVoiceResult = { spokenText ->
                        suppressAutocomplete = true
                        textFieldValue =
                            TextFieldValue(
                                text = spokenText,
                                selection = TextRange(spokenText.length),
                            )
                        onEvent(BrowserUiEvent.UpdateSearchQuery(spokenText))
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    carouselState = carouselState,
                    density = density,
                    modifier = Modifier.weight(1f),
                )

                SearchEngineSelectorButton(
                    carouselState = carouselState,
                    isMenuOpen = isDropdownOpen,
                    isDark = isDark,
                    onClick = { isDropdownOpen = !isDropdownOpen },
                )
            }
        }

        // Layer 3: Floating Search Engine Dropdown Menu anchored right above the selector button
        AnimatedVisibility(
            visible = isDropdownOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 3 },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 60.dp),
        ) {
            SearchEngineDropdownCard(
                selectedEngine = carouselState.currentEngine,
                engines = uiState.searchEngines,
                onSelectEngine = { engine ->
                    carouselState.animateToEngine(engine)
                    isDropdownOpen = false
                },
                onAddEngineClick = {
                    isDropdownOpen = false
                    onEvent(BrowserUiEvent.ShowAddSearchEngineDialog())
                },
                onEditEngineClick = { engine ->
                    isDropdownOpen = false
                    onEvent(BrowserUiEvent.ShowAddSearchEngineDialog(engine))
                },
                onDeleteEngineClick = { engine ->
                    onEvent(BrowserUiEvent.DeleteSearchEngine(engine.id))
                },
                onReorderEngines = { reordered ->
                    onEvent(BrowserUiEvent.ReorderSearchEngines(reordered))
                },
                isDark = isDark,
            )
        }
    }
}

@Preview(name = "SearchOverlay Light", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFFE9F6F2)
@Composable
fun SearchOverlayLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        SearchOverlay(
            uiState =
                BrowserUiState(
                    searchQuery = "",
                    searchHistorySuggestions =
                        listOf(
                            HistoryItem(
                                id = 1,
                                title = "Kotlin Backdrop Blur Jetpack Compose",
                                url = "https://duckduckgo.com/?q=kotlin+backdrop+blur",
                                timestamp = System.currentTimeMillis(),
                            ),
                            HistoryItem(
                                id = 2,
                                title = stringResource(R.string.search_engine_design_system_label),
                                url = "https://duckduckgo.com/?q=prosto+canvas+design",
                                timestamp =
                                    System.currentTimeMillis() - 60000,
                            ),
                            HistoryItem(
                                id = 3,
                                title = "Android Developer Documentation",
                                url = "https://developer.android.com",
                                timestamp =
                                    System.currentTimeMillis() - 120000,
                            ),
                        ),
                    favorites =
                        listOf(
                            FavoriteItem(id = 1, title = "Google", url = "https://google.com"),
                            FavoriteItem(id = 2, title = "GitHub", url = "https://github.com"),
                            FavoriteItem(id = 3, title = "Reddit", url = "https://reddit.com"),
                            FavoriteItem(id = 4, title = "Wikipedia", url = "https://wikipedia.org"),
                        ),
                    settings =
                        BrowserSettings(
                            searchEngine = SearchEngine.DUCKDUCKGO,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "SearchOverlay Dark", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFF0E1513)
@Composable
fun SearchOverlayDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SearchOverlay(
            uiState =
                BrowserUiState(
                    searchQuery = "",
                    searchHistorySuggestions =
                        listOf(
                            HistoryItem(
                                id = 1,
                                title = "Kotlin Backdrop Blur Jetpack Compose",
                                url = "https://duckduckgo.com/?q=kotlin+backdrop+blur",
                                timestamp = System.currentTimeMillis(),
                            ),
                            HistoryItem(
                                id = 2,
                                title = stringResource(R.string.search_engine_design_system_label),
                                url = "https://duckduckgo.com/?q=prosto+canvas+design",
                                timestamp =
                                    System.currentTimeMillis() - 60000,
                            ),
                        ),
                    favorites =
                        listOf(
                            FavoriteItem(id = 1, title = "Google", url = "https://google.com"),
                            FavoriteItem(id = 2, title = "GitHub", url = "https://github.com"),
                        ),
                    settings =
                        BrowserSettings(
                            searchEngine = SearchEngine.GOOGLE,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "SearchOverlay Expanded Dark", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFF0E1513)
@Composable
fun SearchOverlayExpandedDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SearchOverlay(
            uiState =
                BrowserUiState(
                    searchQuery = "",
                    isSearchHistoryExpanded = true,
                    searchHistorySuggestions =
                        listOf(
                            HistoryItem(
                                id = 1,
                                title = "Kotlin Backdrop Blur Jetpack Compose",
                                url = "https://duckduckgo.com/?q=kotlin+backdrop+blur",
                                timestamp = System.currentTimeMillis(),
                            ),
                            HistoryItem(
                                id = 2,
                                title = stringResource(R.string.search_engine_design_system_label),
                                url = "https://duckduckgo.com/?q=prosto+canvas+design",
                                timestamp =
                                    System.currentTimeMillis() - 60000,
                            ),
                            HistoryItem(
                                id = 3,
                                title = "Android Developer Documentation",
                                url = "https://developer.android.com",
                                timestamp =
                                    System.currentTimeMillis() - 120000,
                            ),
                        ),
                    favorites =
                        listOf(
                            FavoriteItem(id = 1, title = "Google", url = "https://google.com"),
                        ),
                    settings =
                        BrowserSettings(
                            searchEngine = SearchEngine.DUCKDUCKGO,
                        ),
                ),
            onEvent = {},
        )
    }
}
