package com.jestk.browser.ui.overlays

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import com.jestk.browser.ui.theme.LocalIsDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DuckDuckGoBadgeBrush
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryContainer
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.PastelBlue
import com.jestk.browser.ui.theme.PastelPeach
import com.jestk.browser.ui.theme.PastelRose
import com.jestk.browser.ui.theme.liquidSunsetBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.delay

private data class QuickShortcut(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val containerColor: Color
)

private val DefaultQuickShortcuts = listOf(
    QuickShortcut("Apple", "https://apple.com", Icons.Default.Public, Color(0xFF7BA7D7)),
    QuickShortcut("GitHub", "https://github.com", Icons.Default.Terminal, Color(0xFFE8A4B8)),
    QuickShortcut("Wikipedia", "https://wikipedia.org", Icons.AutoMirrored.Filled.MenuBook, Color(0xFFF6B99D)),
    QuickShortcut("YouTube", "https://youtube.com", Icons.Default.SmartDisplay, Color(0xFFD391A5))
)

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
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .liquidSunsetBackground(isDark = isDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onEvent(BrowserUiEvent.DismissOverlay) }
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper Area: Inset Grouped History Stack (scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val suggestions = uiState.searchHistorySuggestions
                if (suggestions.isNotEmpty()) {
                    InsetGroupedHistoryCard(
                        items = suggestions,
                        isDark = isDark,
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
                    // Empty State for History
                    HistoryEmptyCard(isDark = isDark)
                }
            }

            // Middle: Hero Centerpiece Search Input Field
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

            Spacer(modifier = Modifier.height(10.dp))

            // Search Engine Switcher Chips (Task 8)
            SearchEngineSwitcherRow(
                selectedEngine = uiState.settings.searchEngine,
                onSelectEngine = { engine ->
                    onEvent(BrowserUiEvent.SetSearchEngine(engine))
                },
                isDark = isDark,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom: Quick Favorites / Shortcuts Cloud
            QuickShortcutsCloud(
                shortcuts = DefaultQuickShortcuts,
                isDark = isDark,
                onSelect = { url ->
                    onEvent(BrowserUiEvent.LoadUrl(url))
                    onEvent(BrowserUiEvent.DismissOverlay)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(0.35f))
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

    val bg = if (isDark) Color(0xFF242021).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)
    val border = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.60f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.90f))

    Surface(
        shape = RoundedCornerShape(27.dp),
        color = bg,
        border = border,
        shadowElevation = 8.dp,
        modifier = modifier.height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Engine Badge (Brand Engine Badge - Task 15)
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.outline
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
                    cursorColor = LiquidPrimaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            // Clear Button
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onSubmit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = LiquidPrimaryContainer,
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
    onItemClick: (HistoryItem) -> Unit,
    onInsertQuery: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.85f)
    val cardBorder = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header (Stitch Screen 2)
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
                    tint = if (isDark) PastelRose else LiquidTertiary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Search History",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TextButton(
                onClick = onClearHistory,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = cardBorder,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items, key = { it.id }) { item ->
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
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(19.dp)
                        )

                        Text(
                            text = item.title.ifEmpty { item.url },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
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
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp).rotate(45f)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = if (isDark) Color(0xFF4D4546).copy(alpha = 0.40f) else LiquidSurfaceContainerHigh.copy(alpha = 0.50f),
                        modifier = Modifier.padding(start = 44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyCard(isDark: Boolean) {
    val bg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.75f)
    val border = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.40f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.60f))

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = border,
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
                    .background(LiquidPrimaryContainer.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Search History",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Recent queries will appear here",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickShortcutsCloud(
    shortcuts: List<QuickShortcut>,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f)
    val cardBorder = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))

    Column(modifier = modifier) {
        Text(
            text = "Quick Favorites",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = cardBorder,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(shortcut.url) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shortcut.containerColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = shortcut.icon,
                                contentDescription = shortcut.title,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = shortcut.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Search Engine Badges & Switcher Chips (Task 8 & 15)
// ==========================================

val DuckDuckGoBrandBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF8543),
        Color(0xFFE54318)
    )
)

val BingBrandBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF00C8D7),
        Color(0xFF0078D4)
    )
)

@Composable
fun SearchEngineBadge(
    engine: SearchEngine,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    isDark: Boolean = false
) {
    when (engine) {
        SearchEngine.DUCKDUCKGO -> DuckDuckGoBadge(size = size, modifier = modifier)
        SearchEngine.GOOGLE -> GoogleBadge(size = size, isDark = isDark, modifier = modifier)
        SearchEngine.BING -> BingBadge(size = size, modifier = modifier)
    }
}

@Composable
private fun DuckDuckGoBadge(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DuckDuckGoBrandBrush),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Duck Head
            val headRadius = w * 0.30f
            val headCenter = Offset(w * 0.44f, h * 0.44f)
            drawCircle(
                color = Color.White,
                radius = headRadius,
                center = headCenter
            )

            // 2. Duck Beak (Warm Golden Orange)
            val beakPath = Path().apply {
                moveTo(w * 0.52f, h * 0.38f)
                quadraticTo(w * 0.80f, h * 0.42f, w * 0.86f, h * 0.49f)
                quadraticTo(w * 0.76f, h * 0.58f, w * 0.48f, h * 0.57f)
                close()
            }
            drawPath(beakPath, color = Color(0xFFFFB300))

            // 3. Eye (Dark pupil with specular reflection)
            drawCircle(
                color = Color(0xFF1E1B1C),
                radius = w * 0.055f,
                center = Offset(w * 0.40f, h * 0.36f)
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.02f,
                center = Offset(w * 0.39f, h * 0.35f)
            )

            // 4. Green Bowtie (Dax signature)
            val bowLeft = Path().apply {
                moveTo(w * 0.28f, h * 0.70f)
                lineTo(w * 0.44f, h * 0.75f)
                lineTo(w * 0.28f, h * 0.80f)
                close()
            }
            val bowRight = Path().apply {
                moveTo(w * 0.60f, h * 0.70f)
                lineTo(w * 0.44f, h * 0.75f)
                lineTo(w * 0.60f, h * 0.80f)
                close()
            }
            drawPath(bowLeft, color = Color(0xFF43A047))
            drawPath(bowRight, color = Color(0xFF43A047))
            drawCircle(
                color = Color(0xFF2E7D32),
                radius = w * 0.045f,
                center = Offset(w * 0.44f, h * 0.75f)
            )
        }
    }
}

@Composable
private fun GoogleBadge(
    size: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF2E2A2B) else Color.White
    val border = if (isDark) {
        BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, Color(0xFFE2DCDD))
    }

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.58f)) {
                val w = this.size.width
                val h = this.size.height
                val center = Offset(w / 2f, h / 2f)

                val strokeWidth = w * 0.22f
                val radius = (w - strokeWidth) / 2f
                val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(radius * 2f, radius * 2f)

                // 1. Red (Top: 215° to 320°)
                drawArc(
                    color = Color(0xFFEA4335),
                    startAngle = 215f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 2. Yellow (Left: 135° to 220°)
                drawArc(
                    color = Color(0xFFFBBC05),
                    startAngle = 135f,
                    sweepAngle = 85f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 3. Green (Bottom: 40° to 140°)
                drawArc(
                    color = Color(0xFF34A853),
                    startAngle = 40f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 4. Blue (Bottom-right: 330° to 45°)
                drawArc(
                    color = Color(0xFF4285F4),
                    startAngle = 330f,
                    sweepAngle = 75f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 5. Blue Horizontal Crossbar
                val barLeft = center.x - w * 0.05f
                val barWidth = center.x + radius + strokeWidth / 2f - barLeft
                drawRect(
                    color = Color(0xFF4285F4),
                    topLeft = Offset(barLeft, center.y - strokeWidth / 2f),
                    size = Size(barWidth, strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun BingBadge(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BingBrandBrush),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.58f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.20f

            // Clean white Bing 'b' glyph:
            // Vertical stem on left
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.14f, h * 0.06f),
                size = Size(strokeW, h * 0.88f),
                cornerRadius = CornerRadius(strokeW / 2f, strokeW / 2f)
            )

            // Loop on bottom right
            val loopRadius = w * 0.31f
            val loopCenter = Offset(w * 0.56f, h * 0.63f)
            drawCircle(
                color = Color.White,
                radius = loopRadius,
                center = loopCenter,
                style = Stroke(width = strokeW)
            )
        }
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
        isSelected && isDark -> Color(0xFF33618D).copy(alpha = 0.35f)
        isSelected && !isDark -> LiquidPrimaryFixed.copy(alpha = 0.70f)
        !isSelected && isDark -> Color(0xFF242021).copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.60f)
    }

    val border = when {
        isSelected && isDark -> BorderStroke(1.5.dp, PastelBlue)
        isSelected && !isDark -> BorderStroke(1.5.dp, LiquidPrimary)
        !isSelected && isDark -> BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.40f))
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.85f))
    }

    val contentColor = when {
        isSelected && isDark -> Color.White
        isSelected && !isDark -> LiquidPrimary
        !isSelected && isDark -> Color(0xFFDFD8D9)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
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
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

@Preview(name = "SearchOverlay Light", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
fun SearchOverlayLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        SearchOverlay(
            uiState = BrowserUiState(
                searchQuery = "",
                settings = com.jestk.browser.domain.model.BrowserSettings(
                    searchEngine = SearchEngine.DUCKDUCKGO
                )
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "SearchOverlay Dark", showBackground = true, backgroundColor = 0xFF161314)
@Composable
fun SearchOverlayDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        SearchOverlay(
            uiState = BrowserUiState(
                searchQuery = "",
                settings = com.jestk.browser.domain.model.BrowserSettings(
                    searchEngine = SearchEngine.GOOGLE
                )
            ),
            onEvent = {}
        )
    }
}

