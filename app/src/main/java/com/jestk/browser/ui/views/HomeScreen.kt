package com.jestk.browser.ui.views

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.R
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.RecentTabItem
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.components.ProstoRecentTabCard
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCanvasFloor
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.DefaultBrowserHelper
import kotlinx.coroutines.launch

// Favorite item model for 4-column layout
private data class PresetFavorite(
    val id: Long? = null,
    val title: String,
    val url: String,
    val icon: ImageVector = Icons.Default.Public,
    val iconTint: Color = ProstoBrandPrimary,
    val bgBrush: Brush? = null,
    val iconUrl: String? = null,
)

/**
 * Home Screen - Prosto Canvas design system.
 * Features:
 * - Ambient multi-point mint gradient background
 * - Hero search capsule with search icon, mic, and QR scanner
 * - 4-column squircle favorites grid with mint gradients
 * - 136x184dp snap recent tabs carousel with header preview
 */
@Composable
fun HomeScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val speechLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    onEvent(BrowserUiEvent.SubmitSearch(spokenText))
                }
            }
        }

    val defaultBrowserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            onEvent(BrowserUiEvent.UpdateDefaultBrowserStatus(DefaultBrowserHelper.isDefaultBrowser(context)))
        }

    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    var isDefaultBrowserDismissed by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Ambient background
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .prostoSkyBackground(isDark = isDark),
        )

        // Layer 2: Scrolling foreground content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(top = 16.dp, bottom = 120.dp),
        ) {
            // Hero Greeting
            Text(
                text = "Where to next?",
                style = LocalProstoTypography.current.headlineXl,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
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
                        val intent =
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy & Protection Card
            PrivacyProtectionCard(
                blockedAdsCount = uiState.blockedAdsCount,
                blockedTrackersCount = uiState.blockedTrackersCount,
                isDark = isDark,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                )
            }

            // Feedback Prompt Card
            if (uiState.isFeedbackCardVisible) {
                Spacer(modifier = Modifier.height(20.dp))
                FeedbackPromptCard(
                    isDark = isDark,
                    onRateClick = { onEvent(BrowserUiEvent.OpenFeedbackSheet) },
                    onSnoozeClick = { onEvent(BrowserUiEvent.SnoozeFeedbackPrompt()) },
                    onDismissClick = { onEvent(BrowserUiEvent.DismissFeedbackPrompt) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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
                    },
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
    modifier: Modifier = Modifier,
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier =
            modifier
                .height(52.dp)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search Glyph
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = ProstoMutedSlate,
                modifier = Modifier.size(20.dp),
            )

            // Placeholder Text
            Text(
                text = "Search with $searchEngineName or enter URL",
                style = LocalProstoTypography.current.bodyMd,
                color = ProstoMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Trailing Actions (Voice & Scanner)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = onQrScanClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(20.dp),
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Section Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Favorites",
                style = LocalProstoTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )

            if (canBookmarkCurrent) {
                TextButton(onClick = onBookmarkCurrent) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ProstoBrandPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bookmark Tab",
                        style = LocalProstoTypography.current.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = ProstoBrandPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Level 1 Solid Card enclosing the favorites grid
        Surface(
            shape = ProstoShapes.Card,
            color = if (isDark) DarkSurface else Color.White,
            border = ProstoBorders.cardBorder(isDark),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                val itemsToDisplay: List<PresetFavorite> =
                    favorites
                        .map { fav ->
                            val fallbackFavicon = fav.iconUrl ?: FaviconManager.extractFallbackFaviconUrl(fav.url)
                            PresetFavorite(
                                id = fav.id,
                                title = fav.title.ifEmpty { fav.url },
                                url = fav.url,
                                icon = Icons.Default.Public,
                                iconTint = ProstoBrandPrimary,
                                iconUrl = fallbackFavicon,
                            )
                        }.let { list ->
                            if (isExpanded) list else list.take(7)
                        }

                if (itemsToDisplay.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AddFavoriteButton(
                            isDark = isDark,
                            onClick = onAddClick,
                            modifier = Modifier.weight(1f),
                        )
                        repeat(3) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    val rows = itemsToDisplay.chunked(4)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rows.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowItems.forEach { item ->
                                    FavoriteSquircleCard(
                                        item = item,
                                        isDark = isDark,
                                        onClick = { onSelectUrl(item.url) },
                                        onDelete = item.id?.let { id -> { onDeleteFavorite(id) } },
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                val emptySlots = 4 - rowItems.size
                                if (emptySlots > 0 && rowIndex == rows.lastIndex) {
                                    AddFavoriteButton(
                                        isDark = isDark,
                                        onClick = onAddClick,
                                        modifier = Modifier.weight(1f),
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AddFavoriteButton(
                                    isDark = isDark,
                                    onClick = onAddClick,
                                    modifier = Modifier.weight(1f),
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
                        contentAlignment = Alignment.Center,
                    ) {
                        OutlinedButton(
                            onClick = onToggleExpand,
                            shape = ProstoShapes.Pill,
                            border = BorderStroke(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f)),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = ProstoBrandPrimary,
                                ),
                        ) {
                            Text(
                                text = if (isExpanded) "Show Less" else "Show All Favorites (${favorites.size})",
                                style = LocalProstoTypography.current.labelMd,
                                color = ProstoBrandPrimary,
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
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val squircleBg = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { showMenu = true },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = squircleBg,
                border = null,
                shadowElevation = 0.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (item.bgBrush != null) {
                                    Modifier.background(item.bgBrush)
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    var isFaviconSuccess by remember(item.iconUrl) { mutableStateOf(false) }
                    val imageModel =
                        remember(item.iconUrl) {
                            FaviconManager.toImageModel(item.iconUrl)
                        }

                    if (!isFaviconSuccess) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            onSuccess = { isFaviconSuccess = true },
                            onError = { isFaviconSuccess = false },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = item.title,
                style =
                    LocalProstoTypography.current.bodySm.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_url_action)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    showMenu = false
                    coroutineScope.launch {
                        val clipData = ClipData.newPlainText("URL", item.url)
                        clipboard.setClipEntry(clipData.toClipEntry())
                    }
                },
            )
            if (item.id != null && onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_bookmark_action), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun AddFavoriteButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val squircleBg = if (isDark) DarkSurfaceVariant.copy(alpha = 0.50f) else ProstoCanvasFloor
    val border = BorderStroke(1.dp, ProstoBrandPrimary.copy(alpha = 0.35f))

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = squircleBg,
            border = border,
            shadowElevation = 0.dp,
            modifier = Modifier.size(44.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Favorite",
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.add_action),
            style =
                LocalProstoTypography.current.bodySm.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color = ProstoMutedSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecentTabsSection(
    recentTabs: List<RecentTabItem>,
    isDark: Boolean,
    onOpenRecent: (RecentTabItem) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recently Closed",
                style = LocalProstoTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else ProstoDarkText,
            )

            Text(
                text = "Total ${recentTabs.size}",
                style = LocalProstoTypography.current.labelMd,
                color = ProstoMutedSlate,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Snap Carousel (136x184dp cards)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(recentTabs, key = { it.url }) { item ->
                ProstoRecentTabCard(
                    item = item,
                    isDark = isDark,
                    onClick = { onOpenRecent(item) },
                )
            }

            item {
                // More / Full History Card
                MoreHistoryCard(
                    isDark = isDark,
                    onClick = onMoreClick,
                )
            }
        }
    }
}


@Composable
private fun MoreHistoryCard(
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier =
            Modifier
                .width(110.dp)
                .height(184.dp)
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "More",
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "More",
                style =
                    LocalProstoTypography.current.headlineSm.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "All History",
                style = LocalProstoTypography.current.bodySm.copy(fontSize = 10.sp),
                color = ProstoMutedSlate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "RecentTabsSection Light", showBackground = true, widthDp = 412, heightDp = 260)
@Composable
fun RecentTabsSectionLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .prostoSkyBackground(isDark = false),
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "GitHub", url = "https://github.com"),
                        ),
                    isDark = false,
                    onOpenRecent = {},
                    onMoreClick = {},
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .prostoSkyBackground(isDark = true),
            )
            Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                RecentTabsSection(
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Programming", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Android Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "GitHub", url = "https://github.com"),
                        ),
                    isDark = true,
                    onOpenRecent = {},
                    onMoreClick = {},
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
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.default_browser_prompt_title),
                            style =
                                LocalProstoTypography.current.headlineSm.copy(
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.default_browser_prompt_desc),
                            style =
                                LocalProstoTypography.current.bodySm.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            color = ProstoMutedSlate,
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                            ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSetDefaultClick,
                shape = ProstoShapes.Pill,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ProstoBrandPrimary,
                        contentColor = Color.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.default_browser_prompt_button),
                    style =
                        LocalProstoTypography.current.labelMd.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                )
            }
        }
    }
}

@Composable
fun FeedbackPromptCard(
    isDark: Boolean,
    onRateClick: () -> Unit,
    onSnoozeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Feedback icon",
                            tint = ProstoBrandPrimary,
                            modifier = Modifier.size(24.dp).testTag("feedback_card_icon"),
                        )
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_card_title),
                            style =
                                LocalProstoTypography.current.headlineSm.copy(
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = if (isDark) DarkOnSurface else ProstoDarkText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.feedback_card_desc),
                            style =
                                LocalProstoTypography.current.bodySm.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                            color = ProstoMutedSlate,
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
                            )
                            .testTag("feedback_card_dismiss"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = ProstoMutedSlate,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRateClick,
                    shape = ProstoShapes.Pill,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ProstoBrandPrimary,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.height(40.dp).testTag("feedback_card_rate_button"),
                ) {
                    Text(
                        text = stringResource(R.string.feedback_card_action_rate),
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                    )
                }

                TextButton(
                    onClick = onSnoozeClick,
                    shape = ProstoShapes.Pill,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = ProstoMutedSlate,
                        ),
                    modifier = Modifier.height(40.dp).testTag("feedback_card_later_button"),
                ) {
                    Text(
                        text = stringResource(R.string.feedback_card_action_later),
                        style =
                            LocalProstoTypography.current.labelMd.copy(
                                fontWeight = FontWeight.Medium,
                                color = ProstoMutedSlate,
                            ),
                    )
                }
            }
        }
    }
}

fun formatStatCount(count: Long): String {
    if (count <= 0) return "0"
    if (count < 1000) return count.toString()
    if (count < 1_000_000) {
        val thousands = count / 1000.0
        val formatted = String.format(java.util.Locale.US, "%.1f", thousands)
        return if (formatted.endsWith(".0")) "${count / 1000}k" else "${formatted}k"
    }
    val millions = count / 1_000_000.0
    val formatted = String.format(java.util.Locale.US, "%.1f", millions)
    return if (formatted.endsWith(".0")) "${count / 1_000_000}M" else "${formatted}M"
}

@Composable
fun PrivacyProtectionCard(
    blockedAdsCount: Long,
    blockedTrackersCount: Long,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = ProstoShapes.Card,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        border = ProstoBorders.hairlineBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header Row: Shield icon and "Protection Active"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ProstoBrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Text(
                    text = "Protection Active",
                    style = LocalProstoTypography.current.headlineSm.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = if (isDark) DarkOnSurface else ProstoDarkText,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two Stat Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProtectionStatChip(
                    title = "Ads Blocked",
                    count = formatStatCount(blockedAdsCount),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )

                ProtectionStatChip(
                    title = "Trackers Stopped",
                    count = formatStatCount(blockedTrackersCount),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProtectionStatChip(
    title: String,
    count: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) DarkSurfaceVariant else ProstoCanvasFloor,
        border = ProstoBorders.hairlineBorder(isDark),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = count,
                style = LocalProstoTypography.current.headlineSm.copy(fontWeight = FontWeight.Bold),
                color = ProstoBrandPrimary,
            )
            Text(
                text = title,
                style = LocalProstoTypography.current.labelSm,
                color = ProstoMutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "HomeScreen Light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            uiState =
                BrowserUiState(
                    blockedAdsCount = 14L,
                    blockedTrackersCount = 1200L,
                    favorites =
                        listOf(
                            FavoriteItem(1, "Google", "https://google.com"),
                            FavoriteItem(2, "GitHub", "https://github.com"),
                            FavoriteItem(3, "Reddit", "https://reddit.com"),
                            FavoriteItem(4, "Wikipedia", "https://wikipedia.org"),
                        ),
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "Android Documentation", url = "https://developer.android.com"),
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeScreen Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun HomeScreenDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            uiState =
                BrowserUiState(
                    blockedAdsCount = 42L,
                    blockedTrackersCount = 380L,
                    favorites =
                        listOf(
                            FavoriteItem(1, "Google", "https://google.com"),
                            FavoriteItem(2, "GitHub", "https://github.com"),
                            FavoriteItem(3, "Reddit", "https://reddit.com"),
                            FavoriteItem(4, "Wikipedia", "https://wikipedia.org"),
                        ),
                    recentTabs =
                        listOf(
                            RecentTabItem(title = "Kotlin Language", url = "https://kotlinlang.org"),
                            RecentTabItem(title = "Jetpack Compose", url = "https://developer.android.com/compose"),
                            RecentTabItem(title = "Android Documentation", url = "https://developer.android.com"),
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedbackPromptCard Light", showBackground = true)
@Composable
fun FeedbackPromptCardLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(16.dp)) {
            FeedbackPromptCard(
                isDark = false,
                onRateClick = {},
                onSnoozeClick = {},
                onDismissClick = {},
            )
        }
    }
}

@Preview(name = "FeedbackPromptCard Dark", showBackground = true)
@Composable
fun FeedbackPromptCardDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(16.dp)) {
            FeedbackPromptCard(
                isDark = true,
                onRateClick = {},
                onSnoozeClick = {},
                onDismissClick = {},
            )
        }
    }
}
