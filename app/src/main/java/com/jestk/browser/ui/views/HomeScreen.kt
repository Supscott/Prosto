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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.ui.theme.CardPreviewBlueBrush
import com.jestk.browser.ui.theme.CardPreviewPeachBrush
import com.jestk.browser.ui.theme.CardPreviewRoseBrush
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneCardSurface
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.theme.sereneCardSurface
import com.jestk.browser.ui.theme.sereneSkyBackground
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

// Favorite item model for 4-column layout
private data class PresetFavorite(
    val id: Long? = null,
    val title: String,
    val url: String,
    val icon: ImageVector = Icons.Default.Public,
    val iconTint: Color = SereneBrandPrimary,
    val bgBrush: Brush? = null,
    val iconUrl: String? = null
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
    var isDefaultBrowserDismissed by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Ambient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sereneSkyBackground(isDark = isDark)
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
                // Hero Greeting
                Text(
                    text = "Where to next?",
                    style = LocalSereneTypography.current.headlineXl,
                    color = if (isDark) DarkOnSurface else SereneDarkText,
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

@Composable
private fun HeroSearchCapsule(
    searchEngineName: String,
    isDark: Boolean,
    onClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onQrScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Glyph
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = SereneMutedSlate,
                modifier = Modifier.size(20.dp)
            )

            // Placeholder Text
            Text(
                text = "Search with $searchEngineName or enter URL",
                style = LocalSereneTypography.current.bodyMd,
                color = SereneMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Trailing Actions (Voice & Scanner)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onQrScanClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(20.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorites",
                style = LocalSereneTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else SereneDarkText
            )

            if (canBookmarkCurrent) {
                TextButton(onClick = onBookmarkCurrent) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SereneBrandPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bookmark Tab",
                        style = LocalSereneTypography.current.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = SereneBrandPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Level 1 Solid Card enclosing the favorites grid
        Surface(
            shape = SereneShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = SereneBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val itemsToDisplay: List<PresetFavorite> = favorites.map { fav ->
                    val fallbackFavicon = fav.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(fav.url)
                    PresetFavorite(
                        id = fav.id,
                        title = fav.title.ifEmpty { fav.url },
                        url = fav.url,
                        icon = Icons.Default.Public,
                        iconTint = SereneBrandPrimary,
                        iconUrl = fallbackFavicon
                    )
                }.let { list ->
                    if (isExpanded) list else list.take(7)
                }

                if (itemsToDisplay.isEmpty()) {
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
                } else {
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
                }

                if (favorites.size > 7) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = onToggleExpand,
                            shape = SereneShapes.Pill,
                            border = BorderStroke(1.dp, SereneBrandPrimary.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SereneBrandPrimary
                            )
                        ) {
                            Text(
                                text = if (isExpanded) "Show Less" else "Show All Favorites (${favorites.size})",
                                style = LocalSereneTypography.current.labelMd,
                                color = SereneBrandPrimary
                            )
                        }
                    }
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
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val squircleBg = if (isDark) DarkSurfaceVariant else SereneCanvasFloor

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
                shape = RoundedCornerShape(14.dp),
                color = squircleBg,
                border = null,
                shadowElevation = 0.dp,
                modifier = Modifier.size(44.dp)
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
                    val imageModel = remember(item.iconUrl) {
                        FaviconManager.toImageModel(item.iconUrl)
                    }

                    if (!isFaviconSuccess) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            onSuccess = { isFaviconSuccess = true },
                            onError = { isFaviconSuccess = false },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Text(
                text = item.title,
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
                    coroutineScope.launch {
                        val clipData = ClipData.newPlainText("URL", item.url)
                        clipboard.setClipEntry(clipData.toClipEntry())
                    }
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
    val squircleBg = if (isDark) DarkSurfaceVariant.copy(alpha = 0.50f) else SereneCanvasFloor
    val border = BorderStroke(1.dp, SereneBrandPrimary.copy(alpha = 0.35f))

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = squircleBg,
            border = border,
            shadowElevation = 0.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Favorite",
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = "Add",
            style = LocalSereneTypography.current.bodySm.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = SereneMutedSlate,
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
                text = "Recently Closed",
                style = LocalSereneTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else SereneDarkText
            )

            Text(
                text = "Total ${recentTabs.size}",
                style = LocalSereneTypography.current.labelMd,
                color = SereneMutedSlate
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
    val previewGradient = when (kotlin.math.abs(item.url.hashCode()) % 3) {
        0 -> CardPreviewBlueBrush
        1 -> CardPreviewRoseBrush
        else -> CardPreviewPeachBrush
    }

    val iconTint = SereneBrandPrimary

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
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier
            .width(136.dp)
            .height(184.dp)
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
                    .clip(RoundedCornerShape(14.dp))
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
                    // Decorative header with category glyph and wireframe preview bars
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
                                    .background(Color.White),
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
                                    .background(SereneAccent.copy(alpha = 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SereneAccent.copy(alpha = 0.20f))
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
                    style = LocalSereneTypography.current.labelMd.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isDark) DarkOnSurface else SereneDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displaySubtitle != null) {
                    Text(
                        text = displaySubtitle,
                        style = LocalSereneTypography.current.bodySm.copy(fontSize = 10.sp),
                        color = SereneMutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val relativeTime = formatRelativeTime(item.lastVisitedTimestamp)
                if (relativeTime.isNotEmpty()) {
                    Text(
                        text = relativeTime,
                        style = LocalSereneTypography.current.bodySm.copy(fontSize = 9.sp),
                        color = SereneMutedSlate,
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
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier
            .width(110.dp)
            .height(184.dp)
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
                    .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "More",
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "More",
                style = LocalSereneTypography.current.headlineSm.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isDark) DarkOnSurface else SereneDarkText,
                textAlign = TextAlign.Center
            )

            Text(
                text = "All History",
                style = LocalSereneTypography.current.bodySm.copy(fontSize = 10.sp),
                color = SereneMutedSlate,
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
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sereneSkyBackground(isDark = false)
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
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sereneSkyBackground(isDark = true)
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
    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier
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
                                if (isDark) DarkSurfaceVariant else SereneCanvasFloor
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.default_browser_prompt_title),
                            style = LocalSereneTypography.current.headlineSm.copy(
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = if (isDark) DarkOnSurface else SereneDarkText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.default_browser_prompt_desc),
                            style = LocalSereneTypography.current.bodySm.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = SereneMutedSlate
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) DarkSurfaceVariant else SereneCanvasFloor
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSetDefaultClick,
                shape = SereneShapes.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SereneBrandPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(
                    text = stringResource(R.string.default_browser_prompt_button),
                    style = LocalSereneTypography.current.labelMd.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

