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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isDark = LocalIsDarkTheme.current
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
                onClick = { onEvent(BrowserUiEvent.DismissOverlay) }
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

            LaunchedEffect(uiState.searchHistorySuggestions.size, uiState.isSearchHistoryExpanded) {
                if (scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            LaunchedEffect(scrollState.maxValue) {
                if (scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
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
                            onEvent(BrowserUiEvent.UpdateSearchQuery(queryText))
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

            // Search Engine Switcher chips placed directly above the input field
            SearchEngineSwitcherRow(
                selectedEngine = uiState.settings.searchEngine,
                onSelectEngine = { engine ->
                    onEvent(BrowserUiEvent.SetSearchEngine(engine))
                },
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            // Search Input Field: Docked at the bottom, styled like a messenger input bar
            HeroSearchInputField(
                query = uiState.searchQuery,
                searchEngine = uiState.settings.searchEngine,
                isDark = isDark,
                focusRequester = focusRequester,
                onQueryChange = { onEvent(BrowserUiEvent.UpdateSearchQuery(it)) },
                onSubmit = {
                    onEvent(BrowserUiEvent.SubmitSearch(uiState.searchQuery))
                    onEvent(BrowserUiEvent.DismissOverlay)
                },
                onClear = { onEvent(BrowserUiEvent.UpdateSearchQuery("")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept dismiss clicks
                    )
            )
        }
    }
}

@Composable
private fun HeroSearchInputField(
    query: String,
    searchEngine: SearchEngine,
    isDark: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onQueryChange(spokenText)
            }
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = if (query.isNotEmpty()) TextRange(0, query.length) else TextRange.Zero
            )
        )
    }

    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = if (query.isNotEmpty()) TextRange(0, query.length) else TextRange.Zero
            )
        }
    }

    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier.height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Engine Badge
            SearchEngineBadge(
                engine = searchEngine,
                size = 32.dp,
                isDark = isDark
            )

            // Input Text Field
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onQueryChange(newValue.text)
                },
                placeholder = {
                    Text(
                        text = "Search in ${searchEngine.displayName} or enter URL",
                        style = LocalSereneTypography.current.bodyMd,
                        color = SereneMutedSlate
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = SereneBrandPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            // Clear Button / Submit Button / Voice Button
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
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
    val imageRequest = remember(engine, context) {
        ImageRequest.Builder(context)
            .data(engine.iconAssetUrl)
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = engine.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun SearchEngineSwitcherRow(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchEngine.entries.forEach { engine ->
            val isSelected = engine == selectedEngine
            SearchEngineChip(
                engine = engine,
                isSelected = isSelected,
                isDark = isDark,
                onClick = { onSelectEngine(engine) }
            )
        }
    }
}

@Composable
private fun SearchEngineChip(
    engine: SearchEngine,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        shadowElevation = 0.dp,
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
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

