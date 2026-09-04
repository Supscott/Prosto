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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.ui.theme.DuckDuckGoBadgeBrush
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
    val isDark = isSystemInDarkTheme()

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
                searchEngineName = uiState.settings.searchEngine.displayName,
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

            Spacer(modifier = Modifier.height(18.dp))

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
    searchEngineName: String,
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
            // Engine Badge (Warm Sunset Circle)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DuckDuckGoBadgeBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = searchEngineName,
                    tint = LiquidSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Input Text Field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search in $searchEngineName or enter URL",
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
