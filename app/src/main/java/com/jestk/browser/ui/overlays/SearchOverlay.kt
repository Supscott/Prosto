package com.jestk.browser.ui.overlays

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.theme.sereneSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Search & History Overlay - Liquid Pastel Sunset design system.
 * Features:
 * - Ambient pastel sunset background
 * - Glowing capsule search input with DuckDuckGo engine badge and mic
 * - Inset grouped history cards with insert arrow (↖) to populate input
 * - Quick favorites shortcuts cloud
 */
@Composable
fun SearchOverlay(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialDropdownOpen: Boolean = false
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isDropdownOpen by remember { mutableStateOf(initialDropdownOpen) }

    val carouselState = rememberSearchEngineCarouselState(
        initialEngine = uiState.settings.searchEngine,
        engines = uiState.searchEngines,
        density = density,
        coroutineScope = coroutineScope,
        haptic = haptic,
        onEngineSelected = { target ->
            onEvent(BrowserUiEvent.SetSearchEngine(target))
        }
    )

    LaunchedEffect(uiState.settings.searchEngine) {
        if (carouselState.currentEngine != uiState.settings.searchEngine && !carouselState.isScrolling) {
            carouselState.currentEngine = uiState.settings.searchEngine
        }
    }

    BackHandler(enabled = isDropdownOpen) {
        isDropdownOpen = false
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.searchQuery,
                selection = if (uiState.searchQuery.isNotEmpty()) {
                    TextRange(0, uiState.searchQuery.length)
                } else {
                    TextRange.Zero
                }
            )
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = modifier
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
                }
            )
    ) {
        // Layer 1: Ambient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sereneSkyBackground(isDark = isDark)
        )

        // Layer 2: Foreground content containing solid cards & input
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                scrollState.value,
                uiState.isSearchHistoryExpanded,
                uiState.hasMoreHistory,
                uiState.isLoadingMoreHistory,
                uiState.searchHistorySuggestions.size
            ) {
                if (uiState.isSearchHistoryExpanded &&
                    uiState.hasMoreHistory &&
                    !uiState.isLoadingMoreHistory &&
                    uiState.searchHistorySuggestions.isNotEmpty() &&
                    scrollState.value <= 20
                ) {
                    onEvent(BrowserUiEvent.LoadMoreSearchHistory)
                }
            }

            // Top Area: Scrollable column containing history suggestions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.Bottom
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
                            val queryText = when {
                                item.url.contains("duckduckgo.com/?q=") -> {
                                    try { Uri.parse(item.url).getQueryParameter("q") ?: item.title.ifEmpty { item.url } } catch (_: Exception) { item.title.ifEmpty { item.url } }
                                }
                                item.url.contains("google.com/search?q=") -> {
                                    try { Uri.parse(item.url).getQueryParameter("q") ?: item.title.ifEmpty { item.url } } catch (_: Exception) { item.title.ifEmpty { item.url } }
                                }
                                item.url.contains("bing.com/search?q=") -> {
                                    try { Uri.parse(item.url).getQueryParameter("q") ?: item.title.ifEmpty { item.url } } catch (_: Exception) { item.title.ifEmpty { item.url } }
                                }
                                item.title.isNotEmpty() && !item.title.startsWith("http://") && !item.title.startsWith("https://") -> item.title
                                else -> item.url
                            }
                            textFieldValue = TextFieldValue(
                                text = queryText,
                                selection = TextRange(queryText.length)
                            )
                            onEvent(BrowserUiEvent.UpdateSearchQuery(queryText))
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        onClearHistory = {
                            onEvent(BrowserUiEvent.ClearAllHistory)
                        }
                    )
                } else {
                    HistoryEmptyCard(isDark = isDark)
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Favorites squircle cards placed directly above the search engine switcher
            if (!uiState.isSearchHistoryExpanded && uiState.favorites.isNotEmpty()) {
                SearchFavoritesSection(
                    favorites = uiState.favorites,
                    isDark = isDark,
                    onSelect = { url ->
                        onEvent(BrowserUiEvent.LoadUrl(url))
                        onEvent(BrowserUiEvent.DismissOverlay)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept dismiss clicks
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroSearchInputField(
                    textFieldValue = textFieldValue,
                    onValueChange = { newValue ->
                        val matchedEngine = uiState.searchEngines.firstOrNull { engine ->
                            !engine.shortcut.isNullOrBlank() &&
                                newValue.text.startsWith("${engine.shortcut} ", ignoreCase = true)
                        }
                        if (matchedEngine != null) {
                            carouselState.animateToEngine(matchedEngine)
                            val remaining = newValue.text.substring(matchedEngine.shortcut!!.length + 1)
                            textFieldValue = TextFieldValue(
                                text = remaining,
                                selection = TextRange(remaining.length)
                            )
                            onEvent(BrowserUiEvent.UpdateSearchQuery(remaining))
                        } else {
                            textFieldValue = newValue
                            onEvent(BrowserUiEvent.UpdateSearchQuery(newValue.text))
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
                        textFieldValue = TextFieldValue("", TextRange.Zero)
                        onEvent(BrowserUiEvent.UpdateSearchQuery(""))
                    },
                    onVoiceResult = { spokenText ->
                        textFieldValue = TextFieldValue(
                            text = spokenText,
                            selection = TextRange(spokenText.length)
                        )
                        onEvent(BrowserUiEvent.UpdateSearchQuery(spokenText))
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    carouselState = carouselState,
                    density = density,
                    modifier = Modifier.weight(1f)
                )

                SearchEngineSelectorButton(
                    carouselState = carouselState,
                    isMenuOpen = isDropdownOpen,
                    isDark = isDark,
                    onClick = { isDropdownOpen = !isDropdownOpen }
                )
            }
        }

        // Layer 3: Floating Search Engine Dropdown Menu anchored right above the selector button
        AnimatedVisibility(
            visible = isDropdownOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 60.dp)
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
                isDark = isDark
            )
        }
    }
}

@Composable
private fun HeroSearchInputField(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    searchEngine: SearchEngine,
    isDark: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onVoiceResult: (String) -> Unit,
    carouselState: SearchEngineCarouselState? = null,
    density: Density = LocalDensity.current,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onVoiceResult(spokenText)
            }
        }
    }

    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)

    val gestureModifier = if (carouselState != null) {
        Modifier.searchEngineInputGestures(
            carouselState = carouselState,
            enabled = textFieldValue.text.isEmpty()
        )
    } else {
        Modifier.horizontalSwipeCycler(
            density = density,
            enabled = textFieldValue.text.isEmpty(),
            thresholdDp = 64.dp,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight
        )
    }

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier
            .height(52.dp)
            .then(gestureModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Leading Search Icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = SereneMutedSlate,
                modifier = Modifier.size(20.dp)
            )

            // Input Text Field
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalSereneTypography.current.bodyMd.copy(
                    color = if (isDark) DarkOnSurface else SereneDarkText
                ),
                cursorBrush = SolidColor(SereneBrandPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_with_engine, searchEngine.displayName),
                                style = LocalSereneTypography.current.bodyMd,
                                color = SereneMutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Clear Button / Submit Button / Voice Button
            if (textFieldValue.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onSubmit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SereneBrandPrimary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InsetGroupedHistoryCard(
    items: List<HistoryItem>,
    isDark: Boolean,
    isExpanded: Boolean,
    isLoadingMore: Boolean = false,
    onToggleExpand: () -> Unit,
    onItemClick: (HistoryItem) -> Unit,
    onInsertQuery: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    val reversedItems = remember(items) { items.reversed() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.search_history_title),
                    style = LocalSereneTypography.current.headlineSm.copy(fontSize = 13.sp),
                    color = if (isDark) DarkOnSurface else SereneDarkText
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onToggleExpand,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (isExpanded) stringResource(R.string.collapse_history) else stringResource(R.string.expand_history),
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpanded) stringResource(R.string.collapse_history) else stringResource(R.string.expand_history),
                        style = LocalSereneTypography.current.labelMd.copy(fontSize = 11.sp),
                        color = SereneBrandPrimary
                    )
                }

                TextButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.clear_history),
                        style = LocalSereneTypography.current.labelMd.copy(fontSize = 11.sp),
                        color = SereneBrandPrimary
                    )
                }
            }
        }

        Surface(
            shape = SereneShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = SereneBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Loading indicator at the top (end of loaded list in reversed layout)
                if (isLoadingMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SereneBrandPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.loading_more_history),
                                style = LocalSereneTypography.current.labelMd,
                                color = if (isDark) DarkOnSurfaceVariant else SereneMutedSlate
                            )
                        }
                    }
                    HorizontalDivider(
                        color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp)
                    )
                }

                reversedItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SereneMutedSlate,
                            modifier = Modifier.size(18.dp)
                        )

                        Text(
                            text = item.title.ifEmpty { item.url },
                            style = LocalSereneTypography.current.bodyMd,
                            color = if (isDark) DarkOnSurface else SereneDarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Insert Arrow (↖) button
                        IconButton(
                            onClick = { onInsertQuery(item) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Insert in search",
                                tint = SereneMutedSlate,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(45f)
                            )
                        }
                    }

                    if (index < reversedItems.lastIndex) {
                        HorizontalDivider(
                            color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                            modifier = Modifier.padding(start = 44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyCard(isDark: Boolean) {
    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = null,
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Search History",
                style = LocalSereneTypography.current.headlineSm.copy(fontSize = 13.sp),
                color = if (isDark) DarkOnSurface else SereneDarkText
            )
            Text(
                text = "Recent queries will appear here",
                style = LocalSereneTypography.current.bodySm.copy(fontSize = 11.sp),
                color = SereneMutedSlate
            )
        }
    }
}

@Composable
private fun SearchFavoritesSection(
    favorites: List<FavoriteItem>,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favorites.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = SereneBrandPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Favorites",
                style = LocalSereneTypography.current.headlineSm.copy(fontSize = 13.sp),
                color = if (isDark) DarkOnSurface else SereneDarkText
            )
        }

        Surface(
            shape = SereneShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = SereneBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            val rows = favorites.take(8).chunked(4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            SearchFavoriteSquircleCard(
                                item = item,
                                isDark = isDark,
                                onClick = { onSelect(item.url) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val emptySlots = 4 - rowItems.size
                        if (emptySlots > 0) {
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFavoriteSquircleCard(
    item: FavoriteItem,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val squircleBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor
    val faviconUrl = item.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(item.url)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = squircleBg,
            border = null,
            shadowElevation = 0.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var isImageSuccess by remember(faviconUrl) { mutableStateOf(false) }

                if (!isImageSuccess) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = item.title,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val imageModel = remember(faviconUrl) {
                    FaviconManager.toImageModel(faviconUrl)
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        onSuccess = { isImageSuccess = true },
                        onError = { isImageSuccess = false },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = item.title.ifEmpty { item.url },
            style = LocalSereneTypography.current.bodySm.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isDark) DarkOnSurface else SereneDarkText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==========================================
// Search Engine Selector Carousel & Dropdown Menu
// ==========================================

/**
 * State and gesture controller for the search engine logo carousel in SearchOverlay.
 * Provides synchronized dragging, spline-based fling decay with inertia,
 * spring snapping to the nearest engine slot, and haptic feedback on slot crossing.
 */
class SearchEngineCarouselState(
    initialEngine: SearchEngine,
    val density: Density,
    val coroutineScope: CoroutineScope,
    val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    var engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    val onEngineSelected: (SearchEngine) -> Unit
) {
    var currentEngine by mutableStateOf(initialEngine)
    val animOffset = Animatable(0f)
    var isDragging by mutableStateOf(false)
    var isFlinging by mutableStateOf(false)
    val isScrolling: Boolean get() = isDragging || isFlinging || abs(animOffset.value) > 0.5f

    val itemSpacingDp: Dp = 64.dp
    val itemSpacingPx: Float = with(density) { itemSpacingDp.toPx() }

    private var lastHapticSlot: Int = 0
    private var activeJob: Job? = null

    fun onDragStart() {
        activeJob?.cancel()
        isDragging = true
        isFlinging = false
        lastHapticSlot = (-animOffset.value / itemSpacingPx).roundToInt()
    }

    fun onDrag(dragAmount: Float) {
        activeJob?.cancel()
        coroutineScope.launch {
            val newOffset = animOffset.value + dragAmount
            animOffset.snapTo(newOffset)
            val currentSlot = (-newOffset / itemSpacingPx).roundToInt()
            if (currentSlot != lastHapticSlot) {
                lastHapticSlot = currentSlot
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun onDragEnd(velocity: Float) {
        isDragging = false
        val minFlingVelocity = 400f // px/sec
        activeJob = coroutineScope.launch {
            val currentOffset = animOffset.value
            val targetSlot: Int

            if (abs(velocity) >= minFlingVelocity) {
                isFlinging = true
                val decay = splineBasedDecay<Float>(density)
                val predictedDelta = decay.calculateTargetValue(0f, velocity)
                val maxSlots = 6
                val maxDistance = maxSlots * itemSpacingPx
                val clampedDelta = predictedDelta.coerceIn(-maxDistance, maxDistance)
                val predictedTarget = currentOffset + clampedDelta

                var slot = (-predictedTarget / itemSpacingPx).roundToInt()
                val currentBaseSlot = (-currentOffset / itemSpacingPx).toInt()
                if (velocity < -minFlingVelocity && slot <= currentBaseSlot) {
                    slot = currentBaseSlot + 1
                } else if (velocity > minFlingVelocity && slot >= currentBaseSlot) {
                    slot = currentBaseSlot - 1
                }
                targetSlot = slot
            } else {
                targetSlot = (-currentOffset / itemSpacingPx).roundToInt()
            }

            val targetX = -targetSlot * itemSpacingPx

            try {
                if (abs(velocity) >= minFlingVelocity) {
                    animOffset.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialVelocity = velocity
                    ) {
                        val currentSlot = (-value / itemSpacingPx).roundToInt()
                        if (currentSlot != lastHapticSlot) {
                            lastHapticSlot = currentSlot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                } else {
                    animOffset.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) {
                        val currentSlot = (-value / itemSpacingPx).roundToInt()
                        if (currentSlot != lastHapticSlot) {
                            lastHapticSlot = currentSlot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }

                // Settle and commit
                val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                val currentIndex = list.indexOfFirst { it.id == currentEngine.id }.let { if (it == -1) 0 else it }
                val finalIndex = (currentIndex + targetSlot).mod(list.size)
                val newEngine = list[finalIndex]

                currentEngine = newEngine
                animOffset.snapTo(0f)
                lastHapticSlot = 0
                isFlinging = false
                isDragging = false
                onEngineSelected(newEngine)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    isFlinging = false
                } else {
                    throw e
                }
            }
        }
    }

    fun setEngineDirectly(engine: SearchEngine) {
        activeJob?.cancel()
        currentEngine = engine
        coroutineScope.launch {
            animOffset.snapTo(0f)
            lastHapticSlot = 0
            isDragging = false
            isFlinging = false
            onEngineSelected(engine)
        }
    }

    fun animateToEngine(targetEngine: SearchEngine) {
        val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
        val currentIndex = list.indexOfFirst { it.id == currentEngine.id }.let { if (it == -1) 0 else it }
        val targetIndex = list.indexOfFirst { it.id == targetEngine.id }
        if (targetIndex == -1) {
            setEngineDirectly(targetEngine)
            return
        }

        val size = list.size
        if (size <= 1) {
            setEngineDirectly(targetEngine)
            return
        }

        val rawDiff = (targetIndex - currentIndex).mod(size)
        val targetSlot = if (rawDiff <= size / 2) rawDiff else rawDiff - size

        if (targetSlot == 0) {
            if (animOffset.value != 0f) {
                activeJob?.cancel()
                activeJob = coroutineScope.launch {
                    isFlinging = true
                    try {
                        animOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        animOffset.snapTo(0f)
                        lastHapticSlot = 0
                        isFlinging = false
                        isDragging = false
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        isFlinging = false
                    }
                }
            }
            onEngineSelected(targetEngine)
            return
        }

        activeJob?.cancel()
        onEngineSelected(targetEngine)
        isFlinging = true
        val targetX = -targetSlot * itemSpacingPx
        lastHapticSlot = (-animOffset.value / itemSpacingPx).roundToInt()

        activeJob = coroutineScope.launch {
            try {
                animOffset.animateTo(
                    targetValue = targetX,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    val currentSlot = (-value / itemSpacingPx).roundToInt()
                    if (currentSlot != lastHapticSlot) {
                        lastHapticSlot = currentSlot
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                currentEngine = targetEngine
                animOffset.snapTo(0f)
                lastHapticSlot = 0
                isFlinging = false
                isDragging = false
                onEngineSelected(targetEngine)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    isFlinging = false
                } else {
                    throw e
                }
            }
        }
    }
}

/**
 * Creates and remembers a [SearchEngineCarouselState].
 */
@Composable
fun rememberSearchEngineCarouselState(
    initialEngine: SearchEngine,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    density: Density = LocalDensity.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback = LocalHapticFeedback.current,
    onEngineSelected: (SearchEngine) -> Unit
): SearchEngineCarouselState {
    val currentOnEngineSelected by rememberUpdatedState(onEngineSelected)
    val state = remember(density) {
        SearchEngineCarouselState(
            initialEngine = initialEngine,
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            engines = engines,
            onEngineSelected = { currentOnEngineSelected(it) }
        )
    }
    state.engines = engines
    return state
}

@Composable
fun SearchEngineSelectorButton(
    carouselState: SearchEngineCarouselState,
    isMenuOpen: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)
    val density = carouselState.density

    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "arrowRotation"
    )

    val arrowAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "arrowAlpha"
    )

    val fadeAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "fadeAlpha"
    )

    val restingShiftPx = with(density) { (-11).dp.toPx() } * arrowAlpha

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier
            .width(68.dp)
            .height(52.dp)
            .semantics {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .searchEngineButtonGestures(
                carouselState = carouselState,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Badges Carousel with Edge Fade Mask
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        if (fadeAlpha > 0.01f) {
                            val fadeWidth = 14.dp.toPx() * fadeAlpha
                            val w = size.width
                            if (fadeWidth > 0f && w > 0f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        (fadeWidth / w) to Color.Black,
                                        ((w - fadeWidth) / w) to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val list = if (carouselState.engines.isNotEmpty()) carouselState.engines else SearchEngine.DEFAULT_ENGINES
                val baseIndex = list.indexOfFirst { it.id == carouselState.currentEngine.id }.let { if (it == -1) 0 else it }
                val centerSlotFloat = -carouselState.animOffset.value / carouselState.itemSpacingPx
                val centerSlotInt = centerSlotFloat.roundToInt()

                for (k in (centerSlotInt - 2)..(centerSlotInt + 2)) {
                    key(k) {
                        val engineIndex = (baseIndex + k).mod(list.size)
                        val engine = list[engineIndex]
                        val xPos = k * carouselState.itemSpacingPx + carouselState.animOffset.value + restingShiftPx

                        SearchEngineBadge(
                            engine = engine,
                            size = 26.dp,
                            isDark = isDark,
                            modifier = Modifier.graphicsLayer {
                                translationX = xPos
                            }
                        )
                    }
                }
            }

            // Dropdown Indicator Arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Search engine selector",
                tint = if (isDark) DarkOnSurfaceVariant else SereneMutedSlate,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = arrowRotation
                        alpha = arrowAlpha
                    }
            )
        }
    }
}

@Composable
fun SearchEngineSelectorButton(
    selectedEngine: SearchEngine,
    isMenuOpen: Boolean,
    isDark: Boolean,
    density: Density,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    val carouselState = remember(selectedEngine, density, engines) {
        SearchEngineCarouselState(
            initialEngine = selectedEngine,
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            engines = engines,
            onEngineSelected = { target ->
                val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                val oldIdx = list.indexOfFirst { it.id == selectedEngine.id }.let { if (it == -1) 0 else it }
                val newIdx = list.indexOfFirst { it.id == target.id }.let { if (it == -1) 0 else it }
                val diff = (newIdx - oldIdx).mod(list.size)
                if (diff == 1) {
                    currentOnSwipeLeft()
                } else if (diff == list.size - 1) {
                    currentOnSwipeRight()
                }
            }
        )
    }

    SearchEngineSelectorButton(
        carouselState = carouselState,
        isMenuOpen = isMenuOpen,
        isDark = isDark,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * State and gesture controller for vertical drag & drop reordering in SearchEngineDropdownCard.
 * Provides real drag & drop with continuous finger tracking, fluid spring animated gap opening
 * under the dragged element ("освобождается место под ним"), bounds clamping, and bouncy snap landing.
 */
class SearchEngineReorderState(
    val slotHeightPx: Float,
    val coroutineScope: CoroutineScope,
    val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    val onReorder: (List<SearchEngine>) -> Unit
) {
    var draggingEngineId by mutableStateOf<String?>(null)
    var draggingIndex by mutableStateOf<Int?>(null)
    var targetIndex by mutableIntStateOf(-1)
    var dragOffsetY by mutableFloatStateOf(0f)
    var isDropping by mutableStateOf(false)
    val dropAnim = Animatable(0f)

    val isDragging: Boolean get() = draggingEngineId != null

    fun onDragStart(engineId: String, currentList: List<SearchEngine>) {
        if (isDropping) return
        val startIndex = currentList.indexOfFirst { it.id == engineId }
        if (startIndex == -1) return
        draggingEngineId = engineId
        draggingIndex = startIndex
        targetIndex = startIndex
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDragStart(index: Int, currentList: List<SearchEngine>) {
        if (index in currentList.indices) {
            onDragStart(currentList[index].id, currentList)
        }
    }

    fun onDragStart(index: Int) {
        if (isDropping) return
        draggingEngineId = "index_$index"
        draggingIndex = index
        targetIndex = index
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDrag(deltaY: Float, currentList: List<SearchEngine>) {
        val id = draggingEngineId ?: return
        if (isDropping) return
        val currentIndex = currentList.indexOfFirst { it.id == id }.takeIf { it != -1 } ?: draggingIndex ?: return
        draggingIndex = currentIndex
        dragOffsetY += deltaY

        // Clamp drag offset so the item stays within list bounds
        val minOffset = -currentIndex * slotHeightPx
        val maxOffset = (currentList.size - 1 - currentIndex) * slotHeightPx
        dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)

        // Calculate prospective slot index
        val currentSlotFloat = currentIndex + (dragOffsetY / slotHeightPx)
        val newTarget = currentSlotFloat.roundToInt().coerceIn(0, currentList.size - 1)
        if (newTarget != targetIndex) {
            targetIndex = newTarget
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDrag(deltaY: Float, listSize: Int) {
        val currentIndex = draggingIndex ?: return
        if (isDropping) return
        dragOffsetY += deltaY

        // Clamp drag offset so the item stays within list bounds
        val minOffset = -currentIndex * slotHeightPx
        val maxOffset = (listSize - 1 - currentIndex) * slotHeightPx
        dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)

        // Calculate prospective slot index
        val currentSlotFloat = currentIndex + (dragOffsetY / slotHeightPx)
        val newTarget = currentSlotFloat.roundToInt().coerceIn(0, listSize - 1)
        if (newTarget != targetIndex) {
            targetIndex = newTarget
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd(
        currentList: List<SearchEngine>,
        onListUpdated: (List<SearchEngine>) -> Unit
    ) {
        val id = draggingEngineId ?: return
        if (isDropping) return
        val startIndex = currentList.indexOfFirst { it.id == id }.takeIf { it != -1 } ?: draggingIndex ?: return
        val finalTarget = targetIndex.coerceIn(0, currentList.lastIndex)

        coroutineScope.launch {
            isDropping = true
            val targetOffset = (finalTarget - startIndex) * slotHeightPx
            dropAnim.snapTo(dragOffsetY)
            dropAnim.animateTo(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            if (finalTarget != startIndex) {
                val mutable = currentList.toMutableList()
                val moved = mutable.removeAt(startIndex)
                mutable.add(finalTarget, moved)
                onListUpdated(mutable)
                onReorder(mutable)
            }

            draggingEngineId = null
            draggingIndex = null
            targetIndex = -1
            dragOffsetY = 0f
            dropAnim.snapTo(0f)
            isDropping = false
        }
    }

    fun onDragCancel() {
        if (draggingEngineId == null && draggingIndex == null) return
        coroutineScope.launch {
            isDropping = true
            dropAnim.snapTo(dragOffsetY)
            dropAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            draggingEngineId = null
            draggingIndex = null
            targetIndex = -1
            dragOffsetY = 0f
            dropAnim.snapTo(0f)
            isDropping = false
        }
    }

    fun getSiblingTargetOffset(engineId: String, currentList: List<SearchEngine>): Float {
        val id = draggingEngineId ?: return 0f
        if (engineId == id) return 0f
        val activeIndex = currentList.indexOfFirst { it.id == id }
        val itemIndex = currentList.indexOfFirst { it.id == engineId }
        if (activeIndex == -1 || itemIndex == -1) return 0f
        val target = targetIndex
        if (target == -1 || target == activeIndex) return 0f

        return if (target > activeIndex) {
            if (itemIndex in (activeIndex + 1)..target) -slotHeightPx else 0f
        } else {
            if (itemIndex in target until activeIndex) slotHeightPx else 0f
        }
    }

    fun getSiblingTargetOffset(index: Int): Float {
        val activeIndex = draggingIndex ?: return 0f
        if (index == activeIndex) return 0f
        val target = targetIndex
        if (target == -1 || target == activeIndex) return 0f

        return if (target > activeIndex) {
            if (index in (activeIndex + 1)..target) -slotHeightPx else 0f
        } else {
            if (index in target until activeIndex) slotHeightPx else 0f
        }
    }
}

@Composable
fun SearchEngineDropdownCard(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    onAddEngineClick: () -> Unit,
    onEditEngineClick: (SearchEngine) -> Unit,
    onDeleteEngineClick: (SearchEngine) -> Unit,
    onReorderEngines: (List<SearchEngine>) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    initialEditMode: Boolean = false,
    reorderState: SearchEngineReorderState? = null,
) {
    var isEditMode by remember { mutableStateOf(initialEditMode) }
    var currentList by remember(engines) { mutableStateOf(engines) }
    LaunchedEffect(engines) {
        currentList = engines
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val itemHeight = 44.dp
    val itemSpacing = 4.dp
    val slotHeightPx = with(density) { (itemHeight + itemSpacing).toPx() }

    val reorderState = reorderState ?: remember(slotHeightPx, onReorderEngines) {
        SearchEngineReorderState(
            slotHeightPx = slotHeightPx,
            coroutineScope = coroutineScope,
            haptic = haptic,
            onReorder = onReorderEngines
        )
    }

    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = border,
        shadowElevation = 10.dp,
        modifier = modifier.width(if (isEditMode) 250.dp else 210.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            currentList.forEachIndexed { index, engine ->
                key(engine.id) {
                    val isSelected = engine.id == selectedEngine.id
                    val isDraggingThis = reorderState.draggingEngineId == engine.id
                    val isReorderActive = reorderState.isDragging || reorderState.isDropping

                    val targetSiblingOffset = if (isDraggingThis) 0f else reorderState.getSiblingTargetOffset(engine.id, currentList)
                    val siblingOffsetAnim = remember(engine.id) { Animatable(0f) }

                    LaunchedEffect(targetSiblingOffset, isReorderActive) {
                        if (!isReorderActive) {
                            siblingOffsetAnim.snapTo(0f)
                        } else {
                            siblingOffsetAnim.animateTo(
                                targetValue = targetSiblingOffset,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }

                    val itemTranslationY = if (isDraggingThis) {
                        if (reorderState.isDropping) reorderState.dropAnim.value else reorderState.dragOffsetY
                    } else {
                        if (isReorderActive) siblingOffsetAnim.value else 0f
                    }

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isDraggingThis) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "ItemScale_${engine.id}"
                    )

                    val animatedElevation by animateDpAsState(
                        targetValue = if (isDraggingThis) 10.dp else 0.dp,
                        label = "ItemElevation_${engine.id}"
                    )

                    val itemBg = when {
                        isDraggingThis && isDark -> DarkSurfaceVariant
                        isDraggingThis && !isDark -> Color.White
                        !isEditMode && isSelected && isDark -> DarkSurfaceVariant
                        !isEditMode && isSelected && !isDark -> SerenePaleBlue
                        else -> Color.Transparent
                    }

                    val itemBorder = when {
                        isDraggingThis -> BorderStroke(2.dp, if (isDark) DarkPrimary else SereneBrandPrimary)
                        !isEditMode && isSelected -> BorderStroke(1.dp, if (isDark) DarkPrimary.copy(alpha = 0.5f) else SereneBrandPrimary.copy(alpha = 0.5f))
                        else -> null
                    }

                    val textColor = when {
                        !isEditMode && isSelected && isDark -> DarkOnSurface
                        !isEditMode && isSelected && !isDark -> SereneBrandPrimary
                        isDark -> DarkOnSurfaceVariant
                        else -> SereneDarkText
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = itemBg,
                        border = itemBorder,
                        shadowElevation = animatedElevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .zIndex(if (isDraggingThis) 10f else 0f)
                            .graphicsLayer {
                                translationY = itemTranslationY
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (!isEditMode) {
                                    Modifier.clickable { onSelectEngine(engine) }
                                } else {
                                    Modifier.pointerInput(engine.id) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val pointerId = down.id

                                            // If another item is already dragging or dropping, ignore until released
                                            if (reorderState.isDragging || reorderState.isDropping) {
                                                while (true) {
                                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                    if (!change.pressed) break
                                                }
                                                return@awaitEachGesture
                                            }

                                            // Hold detection: wait up to 200ms while finger remains pressed
                                            val holdTriggered = withTimeoutOrNull(200L) {
                                                while (true) {
                                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull false
                                                    if (!change.pressed) {
                                                        // Finger released before 200ms -> this is a tap/click
                                                        return@withTimeoutOrNull false
                                                    }
                                                }
                                            } == null

                                            if (holdTriggered) {
                                                reorderState.onDragStart(engine.id, currentList)

                                                while (true) {
                                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                                    if (change == null) {
                                                        reorderState.onDragCancel()
                                                        break
                                                    }
                                                    if (!change.pressed) {
                                                        change.consume()
                                                        reorderState.onDragEnd(currentList) { updated -> currentList = updated }
                                                        break
                                                    }
                                                    val deltaY = change.position.y - change.previousPosition.y
                                                    change.consume()
                                                    reorderState.onDrag(deltaY, currentList)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isEditMode) {
                                // Left 1: Drag handle icon
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        tint = if (isDraggingThis) {
                                            if (isDark) DarkPrimary else SereneBrandPrimary
                                        } else {
                                            if (isDark) DarkOnSurfaceVariant else SereneMutedSlate
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Left 2: Edit pencil button
                                IconButton(
                                    onClick = { onEditEngineClick(engine) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Provider",
                                        tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            SearchEngineBadge(
                                engine = engine,
                                size = 22.dp,
                                isDark = isDark
                            )

                            Text(
                                text = engine.displayName,
                                style = LocalSereneTypography.current.bodyMd.copy(
                                    fontWeight = if (!isEditMode && isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (!engine.shortcut.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    Text(
                                        text = engine.shortcut,
                                        style = LocalSereneTypography.current.labelSm.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                        color = SereneMutedSlate,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (!isEditMode) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                // Right: Delete trash icon (disabled if currentList.size <= 1)
                                val canDelete = currentList.size > 1
                                IconButton(
                                    onClick = { onDeleteEngineClick(engine) },
                                    enabled = canDelete,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Provider",
                                        tint = if (canDelete) Color(0xFFEF4444) else if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Divider above bottom row
            HorizontalDivider(
                color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Pinned Bottom Actions
            if (!isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isEditMode = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Search Engines",
                            tint = if (isDark) DarkOnSurfaceVariant else SereneMutedSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onAddEngineClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Search Engine",
                            tint = if (isDark) DarkPrimary else SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) DarkSurfaceVariant else SerenePaleBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            isEditMode = false
                            onReorderEngines(currentList)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Done",
                            style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) DarkPrimary else SereneBrandPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchEngineDropdownCard(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    SearchEngineDropdownCard(
        selectedEngine = selectedEngine,
        onSelectEngine = onSelectEngine,
        onAddEngineClick = {},
        onEditEngineClick = {},
        onDeleteEngineClick = {},
        onReorderEngines = {},
        isDark = isDark,
        modifier = modifier
    )
}

@Composable
fun Modifier.searchEngineButtonGestures(
    carouselState: SearchEngineCarouselState,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val viewConfig = androidx.compose.ui.platform.LocalViewConfiguration.current

    return this.pointerInput(carouselState) {
        val velocityTracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!isDrag) {
                        currentOnClick()
                    } else {
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (!isDrag && abs(totalDragX) > viewConfig.touchSlop) {
                    isDrag = true
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
fun Modifier.searchEngineButtonGestures(
    density: Density = LocalDensity.current,
    thresholdDp: Dp = 64.dp,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(density) { thresholdDp.toPx() }

    return this.pointerInput(density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!isDrag) {
                        currentOnClick()
                    }
                    break
                }
                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (abs(totalDragX) > viewConfiguration.touchSlop) {
                    isDrag = true
                }
                if (isDrag) {
                    if (totalDragX <= -thresholdPx) {
                        totalDragX += thresholdPx
                        change.consume()
                        currentOnSwipeLeft()
                    } else if (totalDragX >= thresholdPx) {
                        totalDragX -= thresholdPx
                        change.consume()
                        currentOnSwipeRight()
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.searchEngineInputGestures(
    carouselState: SearchEngineCarouselState,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val viewConfig = androidx.compose.ui.platform.LocalViewConfiguration.current

    return this.pointerInput(enabled, carouselState) {
        val velocityTracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (isDrag) {
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (!isDrag && abs(totalDragX) > viewConfig.touchSlop) {
                    isDrag = true
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
fun Modifier.horizontalSwipeCycler(
    density: Density = LocalDensity.current,
    enabled: Boolean = true,
    thresholdDp: Dp = 64.dp,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    if (!enabled) return this
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(density) { thresholdDp.toPx() }

    return this.pointerInput(enabled, density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (abs(totalDragX) > viewConfiguration.touchSlop) {
                    isDrag = true
                }
                if (isDrag) {
                    if (totalDragX <= -thresholdPx) {
                        totalDragX += thresholdPx
                        change.consume()
                        currentOnSwipeLeft()
                    } else if (totalDragX >= thresholdPx) {
                        totalDragX -= thresholdPx
                        change.consume()
                        currentOnSwipeRight()
                    }
                }
            }
        }
    }
}

// ==========================================
// Search Engine Badges & Switcher Chips
// ==========================================

@Composable
fun SearchEngineBadge(
    engine: SearchEngine,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    isDark: Boolean = false
) {
    val context = LocalContext.current
    var isImageError by remember(engine.iconUrl) { mutableStateOf(false) }

    val imageModel = remember(engine.iconUrl) {
        FaviconManager.toImageModel(engine.iconUrl)
    }

    val imageRequest = remember(imageModel, context) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
        contentAlignment = Alignment.Center
    ) {
        if (!isImageError && imageModel != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = engine.displayName,
                contentScale = ContentScale.Fit,
                onError = { isImageError = true },
                onSuccess = { isImageError = false },
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = engine.displayName.take(1).uppercase(),
                style = LocalSereneTypography.current.labelSm.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp
                ),
                color = if (isDark) DarkPrimary else SereneBrandPrimary
            )
        }
    }
}

@Composable
fun SearchEngineSwitcherRow(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    elevation: Dp = 0.dp
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        engines.forEach { engine ->
            val isSelected = engine.id == selectedEngine.id
            SearchEngineChip(
                engine = engine,
                isSelected = isSelected,
                isDark = isDark,
                elevation = elevation,
                onClick = { onSelectEngine(engine) }
            )
        }
    }
}

@Composable
fun SearchEngineChip(
    engine: SearchEngine,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp
) {
    val containerColor = when {
        isSelected && isDark -> DarkSurfaceVariant
        isSelected && !isDark -> SerenePaleBlue
        !isSelected && isDark -> DarkSurface
        else -> Color.White
    }

    val border = when {
        isSelected -> BorderStroke(1.5.dp, SereneBrandPrimary)
        isDark -> BorderStroke(1.dp, DarkSurfaceVariant)
        else -> BorderStroke(1.dp, Color(0x2664748B))
    }

    val contentColor = when {
        isSelected && isDark -> DarkOnSurface
        isSelected && !isDark -> SereneBrandPrimary
        isDark -> DarkOnSurfaceVariant
        else -> SereneDarkText
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        shadowElevation = elevation,
        onClick = onClick,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchEngineBadge(
                engine = engine,
                size = 20.dp,
                isDark = isDark
            )

            Text(
                text = engine.displayName,
                style = LocalSereneTypography.current.labelMd.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

@Preview(name = "SearchOverlay Light", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFFF1F7FC)
@Composable
fun SearchOverlayLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        SearchOverlay(
            uiState = BrowserUiState(
                searchQuery = "",
                searchHistorySuggestions = listOf(
                    com.jestk.browser.domain.model.HistoryItem(id = 1, title = "Kotlin Backdrop Blur Jetpack Compose", url = "https://duckduckgo.com/?q=kotlin+backdrop+blur", timestamp = System.currentTimeMillis()),
                    com.jestk.browser.domain.model.HistoryItem(id = 2, title = "Material 3 Liquid Glass Design System", url = "https://duckduckgo.com/?q=material+3+liquid+glass", timestamp = System.currentTimeMillis() - 60000),
                    com.jestk.browser.domain.model.HistoryItem(id = 3, title = "Android Developer Documentation", url = "https://developer.android.com", timestamp = System.currentTimeMillis() - 120000)
                ),
                favorites = listOf(
                    com.jestk.browser.domain.model.FavoriteItem(id = 1, title = "Google", url = "https://google.com"),
                    com.jestk.browser.domain.model.FavoriteItem(id = 2, title = "GitHub", url = "https://github.com"),
                    com.jestk.browser.domain.model.FavoriteItem(id = 3, title = "Reddit", url = "https://reddit.com"),
                    com.jestk.browser.domain.model.FavoriteItem(id = 4, title = "Wikipedia", url = "https://wikipedia.org")
                ),
                settings = com.jestk.browser.domain.model.BrowserSettings(
                    searchEngine = SearchEngine.DUCKDUCKGO
                )
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "SearchOverlay Dark", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFF0F172A)
@Composable
fun SearchOverlayDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SearchOverlay(
            uiState = BrowserUiState(
                searchQuery = "",
                searchHistorySuggestions = listOf(
                    com.jestk.browser.domain.model.HistoryItem(id = 1, title = "Kotlin Backdrop Blur Jetpack Compose", url = "https://duckduckgo.com/?q=kotlin+backdrop+blur", timestamp = System.currentTimeMillis()),
                    com.jestk.browser.domain.model.HistoryItem(id = 2, title = "Material 3 Liquid Glass Design System", url = "https://duckduckgo.com/?q=material+3+liquid+glass", timestamp = System.currentTimeMillis() - 60000)
                ),
                favorites = listOf(
                    com.jestk.browser.domain.model.FavoriteItem(id = 1, title = "Google", url = "https://google.com"),
                    com.jestk.browser.domain.model.FavoriteItem(id = 2, title = "GitHub", url = "https://github.com")
                ),
                settings = com.jestk.browser.domain.model.BrowserSettings(
                    searchEngine = SearchEngine.GOOGLE
                )
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "SearchOverlay Expanded Dark", showBackground = true, widthDp = 412, heightDp = 892, backgroundColor = 0xFF0F172A)
@Composable
fun SearchOverlayExpandedDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SearchOverlay(
            uiState = BrowserUiState(
                searchQuery = "",
                isSearchHistoryExpanded = true,
                searchHistorySuggestions = listOf(
                    com.jestk.browser.domain.model.HistoryItem(id = 1, title = "Kotlin Backdrop Blur Jetpack Compose", url = "https://duckduckgo.com/?q=kotlin+backdrop+blur", timestamp = System.currentTimeMillis()),
                    com.jestk.browser.domain.model.HistoryItem(id = 2, title = "Material 3 Liquid Glass Design System", url = "https://duckduckgo.com/?q=material+3+liquid+glass", timestamp = System.currentTimeMillis() - 60000),
                    com.jestk.browser.domain.model.HistoryItem(id = 3, title = "Android Developer Documentation", url = "https://developer.android.com", timestamp = System.currentTimeMillis() - 120000)
                ),
                favorites = listOf(
                    com.jestk.browser.domain.model.FavoriteItem(id = 1, title = "Google", url = "https://google.com")
                ),
                settings = com.jestk.browser.domain.model.BrowserSettings(
                    searchEngine = SearchEngine.DUCKDUCKGO
                )
            ),
            onEvent = {}
        )
    }
}

