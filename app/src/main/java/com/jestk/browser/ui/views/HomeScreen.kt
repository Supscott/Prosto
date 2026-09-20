package com.jestk.browser.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.util.DefaultBrowserHelper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.ui.theme.CardPreviewBlueBrush
import com.jestk.browser.ui.theme.CardPreviewPeachBrush
import com.jestk.browser.ui.theme.CardPreviewRoseBrush
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryContainer
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.PastelBlue
import com.jestk.browser.ui.theme.PastelPeach
import com.jestk.browser.ui.theme.PastelRose
import com.jestk.browser.ui.theme.PastelWarmOrange
import com.jestk.browser.ui.theme.LocalHazeState
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.theme.liquidSunsetBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.io.File
import java.net.URI

// Fallback Quick Favorite Presets for Stitch 4-column layout
private data class PresetFavorite(
    val id: Long? = null,
    val title: String,
    val url: String,
    val icon: ImageVector,
    val iconTint: Color,
    val bgBrush: Brush? = null,
    val iconUrl: String? = null
)

private val DefaultPresets = listOf(
    PresetFavorite(
        title = "Apple",
        url = "https://apple.com",
        icon = Icons.Default.PhoneIphone,
        iconTint = LiquidOnSurface
    ),
    PresetFavorite(
        title = "DuckDuckGo",
        url = "https://duckduckgo.com",
        icon = Icons.Default.Public,
        iconTint = LiquidSecondary,
        bgBrush = Brush.linearGradient(listOf(Color(0xFFFFDBCC).copy(alpha = 0.70f), Color(0xFFFED7AA).copy(alpha = 0.50f)))
    ),
    PresetFavorite(
        title = "GitHub",
        url = "https://github.com",
        icon = Icons.Default.Terminal,
        iconTint = LiquidOnSurface
    ),
    PresetFavorite(
        title = "Wikipedia",
        url = "https://wikipedia.org",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        iconTint = LiquidOnSurface
    ),
    PresetFavorite(
        title = "YouTube",
        url = "https://youtube.com",
        icon = Icons.Default.SmartDisplay,
        iconTint = LiquidTertiary,
        bgBrush = Brush.linearGradient(listOf(Color(0xFFFFD9E2).copy(alpha = 0.70f), Color(0xFFF9B3C8).copy(alpha = 0.40f)))
    ),
    PresetFavorite(
        title = "Reddit",
        url = "https://reddit.com",
        icon = Icons.Default.Forum,
        iconTint = LiquidSecondary,
        bgBrush = Brush.linearGradient(listOf(Color(0xFFFFDBCC).copy(alpha = 0.60f), Color(0xFFFFD9E2).copy(alpha = 0.50f)))
    ),
    PresetFavorite(
        title = "Dribbble",
        url = "https://dribbble.com",
        icon = Icons.Default.Palette,
        iconTint = LiquidTertiary,
        bgBrush = Brush.linearGradient(listOf(Color(0xFFFFD9E2).copy(alpha = 0.70f), Color(0xFFD391A5).copy(alpha = 0.40f)))
    )
)

/**
 * Home Screen - Liquid Pastel Sunset design system.
 * Features:
 * - Ambient multi-point pastel sunset background
 * - Hero search capsule with search icon, mic, and QR scanner
 * - 4-column squircle favorites grid with pastel gradients
 * - 136x184dp snap recent tabs carousel with header preview
 */
@Composable
fun HomeScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onEvent(BrowserUiEvent.SubmitSearch(spokenText))
            }
        }
    }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onEvent(BrowserUiEvent.CheckDefaultBrowser(context))
    }

    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    val homeHazeState = hazeState ?: remember { HazeState() }
    var isDefaultBrowserDismissed by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalHazeState provides homeHazeState) {
        Box(modifier = modifier.fillMaxSize()) {
            // Layer 1: Ambient multi-point pastel sunset background registered with Haze
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidSunsetBackground(isDark = isDark)
                    .haze(homeHazeState)
            )

            // Layer 2: Scrolling foreground content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(top = 16.dp, bottom = 120.dp)
            ) {
                // App Title / Top Space
                Text(
                    text = "Jestk Browser",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Hero Search Capsule
                HeroSearchCapsule(
                    searchEngineName = uiState.settings.searchEngine.displayName,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = false))
                    },
                    onVoiceClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onQrScanClick = {
                        Toast.makeText(context, "QR Scanner coming soon", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                // Default Browser Recommendation Card
                if (!uiState.isDefaultBrowser && !isDefaultBrowserDismissed) {
                    Spacer(modifier = Modifier.height(20.dp))
                    DefaultBrowserPromptCard(
                        isDark = isDark,
                        onSetDefaultClick = {
                            DefaultBrowserHelper.openDefaultBrowserSettings(context, defaultBrowserLauncher)
                        },
                        onDismissClick = { isDefaultBrowserDismissed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 2. Favorites Section ("Favorites")
                FavoritesSection(
                    favorites = uiState.favorites,
                    isExpanded = uiState.isFavoritesExpanded,
                    canBookmarkCurrent = uiState.currentUrl.isNotEmpty() && !uiState.currentUrl.startsWith("about:"),
                    isDark = isDark,
                    onSelectUrl = { url -> onEvent(BrowserUiEvent.LoadUrl(url)) },
                    onDeleteFavorite = { id -> onEvent(BrowserUiEvent.RemoveFavorite(id)) },
                    onBookmarkCurrent = { onEvent(BrowserUiEvent.AddCurrentTabToFavorites) },
                    onToggleExpand = { onEvent(BrowserUiEvent.ToggleFavoritesExpanded) },
                    onAddClick = {
                        onEvent(BrowserUiEvent.ShowAddFavoriteDialog)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Recent Tabs Section ("Recent Tabs")
                val recentTabs = uiState.recentTabs.take(8)
                if (recentTabs.isNotEmpty()) {
                    RecentTabsSection(
                        recentTabs = recentTabs,
                        isDark = isDark,
                        onOpenRecent = { item -> onEvent(BrowserUiEvent.OpenRecentTab(item)) },
                        onMoreClick = {
                            onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSearchCapsule(
    searchEngineName: String,
    isDark: Boolean,
    onClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onQrScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF242021) else Color.White
    val border = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(52.dp)
            .liquidFrostedGlass(
                shape = RoundedCornerShape(26.dp),
                isDark = isDark,
                blurRadius = 24.dp,
                tintColor = bg,
                alpha = if (isDark) 0.52f else 0.46f,
                hazeState = LocalHazeState.current
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Glyph
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = LiquidPrimaryContainer,
                modifier = Modifier.size(21.dp)
            )

            // Placeholder Text
            Text(
                text = "Search with $searchEngineName or enter URL",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Trailing Actions (Voice & Scanner)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onQrScanClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesSection(
    favorites: List<FavoriteItem>,
    isExpanded: Boolean,
    canBookmarkCurrent: Boolean,
    isDark: Boolean,
    onSelectUrl: (String) -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onBookmarkCurrent: () -> Unit,
    onToggleExpand: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (canBookmarkCurrent) {
                TextButton(onClick = onBookmarkCurrent) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bookmark Tab", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4-Column Squircle Grid
        val itemsToDisplay: List<PresetFavorite> = if (favorites.isNotEmpty()) {
            val list = favorites.map { fav ->
                val fallbackFavicon = fav.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(fav.url)
                PresetFavorite(
                    id = fav.id,
                    title = fav.title.ifEmpty { fav.url },
                    url = fav.url,
                    icon = Icons.Default.Public,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconUrl = fallbackFavicon
                )
            }
            if (isExpanded) list else list.take(7)
        } else {
            DefaultPresets
        }

        val rows = itemsToDisplay.chunked(4)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        FavoriteSquircleCard(
                            item = item,
                            isDark = isDark,
                            onClick = { onSelectUrl(item.url) },
                            onDelete = item.id?.let { id -> { onDeleteFavorite(id) } },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // If this is the last row and there are empty slots, add the "+" button or spacers
                    val emptySlots = 4 - rowItems.size
                    if (emptySlots > 0 && rowIndex == rows.lastIndex) {
                        AddFavoriteButton(
                            isDark = isDark,
                            onClick = onAddClick,
                            modifier = Modifier.weight(1f)
                        )
                        repeat(emptySlots - 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // If rows filled all 4 slots exactly, show Add button on next row
            if (itemsToDisplay.size % 4 == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AddFavoriteButton(
                        isDark = isDark,
                        onClick = onAddClick,
                        modifier = Modifier.weight(1f)
                    )
                    repeat(3) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (favorites.size > 7) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (isExpanded) "Show Less" else "Show All Favorites (${favorites.size})")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSquircleCard(
    item: PresetFavorite,
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val defaultBg = if (isDark) Color(0xFF282425).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val border: BorderStroke? = if (isDark) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.70f))

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = defaultBg,
                border = border,
                shadowElevation = 0.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (item.bgBrush != null) Modifier.background(item.bgBrush)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    var isFaviconSuccess by remember(item.iconUrl) { mutableStateOf(false) }

                    if (!isFaviconSuccess) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (item.iconTint == LiquidOnSurface || (isDark && item.bgBrush == null)) MaterialTheme.colorScheme.onSurface else item.iconTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    if (!item.iconUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            onSuccess = { isFaviconSuccess = true },
                            onError = { isFaviconSuccess = false },
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy URL") },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    clipboardManager.setText(AnnotatedString(item.url))
                }
            )
            if (item.id != null && onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete Bookmark", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun AddFavoriteButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            border = border,
            shadowElevation = 0.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = "Add",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecentTabsSection(
    recentTabs: List<RecentTabItem>,
    isDark: Boolean,
    onOpenRecent: (RecentTabItem) -> Unit,
    onMoreClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Tabs",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Total ${recentTabs.size}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Snap Carousel (136x184dp cards)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recentTabs, key = { it.url }) { item ->
                RecentTabSunsetCard(
                    item = item,
                    isDark = isDark,
                    onClick = { onOpenRecent(item) }
                )
            }

            item {
                // More / Full History Card
                MoreHistoryCard(
                    isDark = isDark,
                    onClick = onMoreClick
                )
            }
        }
    }
}

@Composable
private fun RecentTabSunsetCard(
    item: RecentTabItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val homeHazeState = LocalHazeState.current
    val tintColor = if (isDark) Color(0xFF1E1B1C) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f)

    // Determine preview header gradient based on url hash for visual variety
    val previewGradient = when (kotlin.math.abs(item.url.hashCode()) % 3) {
        0 -> CardPreviewBlueBrush
        1 -> CardPreviewRoseBrush
        else -> CardPreviewPeachBrush
    }

    val iconTint = when (kotlin.math.abs(item.url.hashCode()) % 3) {
        0 -> LiquidPrimary
        1 -> LiquidTertiary
        else -> LiquidSecondary
    }

    val previewIcon = when (kotlin.math.abs(item.url.hashCode()) % 4) {
        0 -> Icons.Default.Code
        1 -> Icons.Default.Architecture
        2 -> Icons.Default.Public
        else -> Icons.Default.Palette
    }

    val hostDomain = try {
        URI(item.url).host?.removePrefix("www.") ?: item.url.removePrefix("https://").removePrefix("http://")
    } catch (_: Exception) {
        item.url
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(136.dp)
            .height(184.dp)
            .liquidFrostedGlass(
                shape = RoundedCornerShape(18.dp),
                isDark = isDark,
                blurRadius = 24.dp,
                tintColor = tintColor,
                alpha = if (isDark) 0.55f else 0.50f,
                hazeState = homeHazeState
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Preview Header (92dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewGradient)
            ) {
                val file = item.snapshotPath?.let { File(it) }
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Decorative pastel header with category glyph and wireframe preview bars
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.90f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = previewIcon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = iconTint.copy(alpha = 0.70f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Wireframe mockup bars
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(iconTint.copy(alpha = 0.30f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(iconTint.copy(alpha = 0.20f))
                            )
                        }
                    }
                }
            }

            // Bottom Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val displayTitle = item.title.ifEmpty { hostDomain }
                val displaySubtitle = if (item.title.isNotEmpty() && !item.title.equals(hostDomain, ignoreCase = true)) hostDomain else null

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displaySubtitle != null) {
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val relativeTime = formatRelativeTime(item.lastVisitedTimestamp)
                if (relativeTime.isNotEmpty()) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreHistoryCard(
    isDark: Boolean,
    onClick: () -> Unit
) {
    val homeHazeState = LocalHazeState.current
    val tintColor = if (isDark) Color(0xFF1E1B1C) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(110.dp)
            .height(184.dp)
            .liquidFrostedGlass(
                shape = RoundedCornerShape(18.dp),
                isDark = isDark,
                blurRadius = 24.dp,
                tintColor = tintColor,
                alpha = if (isDark) 0.55f else 0.50f,
                hazeState = homeHazeState
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "More",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "All History",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0) return "Just now"
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 5 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        else -> "${days}d ago"
    }
}

@Preview(name = "RecentTabsSection Light", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
fun RecentTabsSectionLightPreview() {
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT, hazeState = hazeState) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFB4A2),
                                Color(0xFFE5989B),
                                Color(0xFFB5E2FA),
                                Color(0xFFEDDCD2)
                            )
                        )
                    )
                    .haze(hazeState)
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs = listOf(
                        RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                        RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                        RecentTabItem(title = "GitHub", url = "https://github.com")
                    ),
                    isDark = false,
                    onOpenRecent = {},
                    onMoreClick = {}
                )
            }
        }
    }
}

@Preview(name = "RecentTabsSection Dark", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
fun RecentTabsSectionDarkPreview() {
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    JestkBrowserTheme(themeMode = ThemeMode.DARK, hazeState = hazeState) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2B193D),
                                Color(0xFF1B263B),
                                Color(0xFF4A0E2E)
                            )
                        )
                    )
                    .haze(hazeState)
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs = listOf(
                        RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                        RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                        RecentTabItem(title = "GitHub", url = "https://github.com")
                    ),
                    isDark = true,
                    onOpenRecent = {},
                    onMoreClick = {}
                )
            }
        }
    }
}

@Composable
private fun DefaultBrowserPromptCard(
    isDark: Boolean,
    onSetDefaultClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF242021) else Color.White
    val border = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp,
        modifier = modifier
            .liquidFrostedGlass(
                shape = RoundedCornerShape(20.dp),
                isDark = isDark,
                blurRadius = 24.dp,
                tintColor = bg,
                alpha = if (isDark) 0.55f else 0.50f,
                hazeState = LocalHazeState.current
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isDark) Color(0xFF1B4975) else LiquidPrimaryFixed.copy(alpha = 0.70f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.default_browser_prompt_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.default_browser_prompt_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color(0xFF2C2829) else LiquidSurfaceContainerHigh.copy(alpha = 0.60f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSetDefaultClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(
                    text = stringResource(R.string.default_browser_prompt_button),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}

@Preview(name = "HomeScreen Light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            uiState = BrowserUiState(
                favorites = listOf(
                    FavoriteItem(1, "Google", "https://google.com"),
                    FavoriteItem(2, "GitHub", "https://github.com"),
                    FavoriteItem(3, "Reddit", "https://reddit.com"),
                    FavoriteItem(4, "Wikipedia", "https://wikipedia.org")
                ),
                recentTabs = listOf(
                    RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                    RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                    RecentTabItem(title = "Android Documentation", url = "https://developer.android.com")
                )
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "HomeScreen Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            uiState = BrowserUiState(
                favorites = listOf(
                    FavoriteItem(1, "Google", "https://google.com"),
                    FavoriteItem(2, "GitHub", "https://github.com"),
                    FavoriteItem(3, "Reddit", "https://reddit.com"),
                    FavoriteItem(4, "Wikipedia", "https://wikipedia.org")
                ),
                recentTabs = listOf(
                    RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                    RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                    RecentTabItem(title = "Android Documentation", url = "https://developer.android.com")
                )
            ),
            onEvent = {}
        )
    }
}

