package com.jestk.browser.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.PastelRose
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.net.URI

/**
 * Floating Capsule Dock & Menu - Liquid Pastel Sunset design system.
 * Features:
 * - Floating capsule dock (<, active tab card with lock/title/domain/close, >)
 * - Dedicated floating circular Menu button (58x58dp)
 * - Animated popup actions card (Home, Search, Tabs, Settings)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val isHome = uiState.activeOverlay == OverlayType.HOME ||
            uiState.currentUrl.isEmpty() ||
            uiState.currentUrl == "about:blank"

    val activeTab = uiState.activeTab

    // Display title & domain
    val isHomeTab = isHome || activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank" || activeTab?.title == "Home"
    val displayTitle = if (isHomeTab) "Home" else when {
        activeTab?.title?.isNotEmpty() == true && activeTab.title != "Home" && activeTab.title != "New Tab" -> activeTab.title
        uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
        else -> "Jestk Browser"
    }

    val displayDomain = if (isHomeTab) "" else when {
        !activeTab?.url.isNullOrEmpty() && activeTab?.url != "about:blank" -> {
            try {
                URI(activeTab!!.url).host?.removePrefix("www.") ?: activeTab.url.removePrefix("https://").removePrefix("http://")
            } catch (_: Exception) {
                activeTab.url
            }
        }
        else -> "duckduckgo.com"
    }

    // Colors & Glassmorphism
    val bottomBg = if (isDark) Color(0xFF161314) else LiquidSurface
    val dockBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.85f) else LiquidSurface.copy(alpha = 0.88f)
    val dockBorder = if (isDark) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))
    val centerPillBg = if (isDark) Color(0xFF2C2829).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f)
    val centerPillBorder = if (isDark) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.85f))
    val circleBtnBg = if (isDark) Color(0xFF2C2829).copy(alpha = 0.70f) else LiquidSurfaceContainerHigh.copy(alpha = 0.65f)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // 1. Animated Popup Menu Card anchored above the Menu Button
            AnimatedVisibility(
                visible = uiState.isMenuExpanded,
                enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220)) { it / 3 },
                exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(animationSpec = tween(180)) { it / 3 }
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f),
                    border = dockBorder,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 16.dp)
                        .width(148.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Home
                        PopupMenuItem(
                            icon = Icons.Default.Home,
                            label = "Home",
                            isActive = isHome && uiState.activeOverlay == OverlayType.HOME,
                            isDark = isDark,
                            onClick = {
                                onEvent(BrowserUiEvent.OpenHome)
                                onEvent(BrowserUiEvent.SetMenuExpanded(false))
                            }
                        )

                        // Search
                        PopupMenuItem(
                            icon = Icons.Default.Search,
                            label = "Search",
                            isActive = uiState.activeOverlay == OverlayType.SEARCH,
                            isDark = isDark,
                            onClick = {
                                onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                                onEvent(BrowserUiEvent.SetMenuExpanded(false))
                            }
                        )

                        // Tabs
                        PopupMenuItem(
                            icon = Icons.Default.FilterNone,
                            label = "Tabs",
                            isActive = uiState.activeOverlay == OverlayType.TAB_GRID,
                            isDark = isDark,
                            onClick = {
                                onEvent(BrowserUiEvent.OpenTabGrid)
                                onEvent(BrowserUiEvent.SetMenuExpanded(false))
                            }
                        )

                        // Settings
                        PopupMenuItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            isActive = uiState.activeOverlay == OverlayType.SETTINGS,
                            isDark = isDark,
                            onClick = {
                                onEvent(BrowserUiEvent.OpenSettings)
                                onEvent(BrowserUiEvent.SetMenuExpanded(false))
                            }
                        )
                    }
                }
            }

            // 2. Floating Dock Row Container: background & gradient start from top of floating dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.70f to bottomBg.copy(alpha = 0.65f),
                                0.90f to bottomBg.copy(alpha = 0.95f),
                                1.0f to bottomBg
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(top = 0.dp, bottom = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Floating Dock Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Floating Capsule Dock
                Surface(
                    shape = RoundedCornerShape(29.dp),
                    color = dockBg,
                    border = dockBorder,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val totalChipWidth = maxWidth
                        val activeTabWidth = totalChipWidth * 0.5f
                        val lazyListState = rememberLazyListState()

                        val displayTabs = remember(uiState.tabs, activeTab) {
                            if (uiState.tabs.isNotEmpty()) {
                                uiState.tabs
                            } else {
                                listOf(
                                    activeTab ?: Tab(
                                        id = "default_tab",
                                        url = uiState.currentUrl,
                                        title = displayTitle,
                                        isActive = true
                                    )
                                )
                            }
                        }

                        val activeIndex = remember(displayTabs, uiState.activeTabId, uiState.activeTabIndex) {
                            val idx = displayTabs.indexOfFirst { it.id == uiState.activeTabId }
                            if (idx >= 0) idx else uiState.activeTabIndex.coerceIn(0, displayTabs.lastIndex)
                        }

                        LaunchedEffect(uiState.activeTabId, activeIndex) {
                            if (activeIndex in displayTabs.indices) {
                                lazyListState.animateScrollToItem(activeIndex)
                            }
                        }

                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(
                                items = displayTabs,
                                key = { _, tab -> tab.id }
                            ) { index, tab ->
                                val isActive = index == activeIndex
                                val tabIsHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home" || (isHome && isActive)

                                if (isActive) {
                                    val currentTitle = if (tabIsHome) "Home" else when {
                                        tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                                        uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                                        else -> "Jestk Browser"
                                    }
                                    val currentDomain = if (tabIsHome) "" else when {
                                        tab.url.isNotEmpty() && tab.url != "about:blank" -> {
                                            try {
                                                URI(tab.url).host?.removePrefix("www.") ?: tab.url.removePrefix("https://").removePrefix("http://")
                                            } catch (_: Exception) {
                                                tab.url
                                            }
                                        }
                                        else -> "duckduckgo.com"
                                    }

                                    ActiveTabCenterCapsule(
                                        title = currentTitle,
                                        domain = currentDomain,
                                        url = tab.url.ifEmpty { uiState.currentUrl },
                                        faviconUrl = tab.faviconUrl,
                                        isHome = tabIsHome,
                                        backgroundColor = centerPillBg,
                                        border = centerPillBorder,
                                        isDark = isDark,
                                        onOpenSearch = {
                                            val targetPrefill = if (tab.url.isNotBlank() && tab.url != "about:blank") tab.url else null
                                            onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = targetPrefill, openInNewTab = false))
                                        },
                                        onClose = {
                                            onEvent(BrowserUiEvent.CloseTab(tab.id))
                                        },
                                        onAddFavorite = { favTitle, favUrl ->
                                            onEvent(BrowserUiEvent.AddFavorite(favTitle, favUrl))
                                        },
                                        onShare = { urlToShare ->
                                            if (urlToShare.isNotBlank() && !urlToShare.startsWith("about:")) {
                                                try {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, urlToShare)
                                                        type = "text/plain"
                                                    }
                                                    val shareIntent = Intent.createChooser(sendIntent, "Share Webpage")
                                                    context.startActivity(shareIntent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Could not share link", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "No URL to share", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.width(activeTabWidth)
                                    )
                                } else {
                                    InactiveTabButton(
                                        tab = tab,
                                        isDark = isDark,
                                        backgroundColor = circleBtnBg,
                                        border = centerPillBorder,
                                        onClick = {
                                            onEvent(BrowserUiEvent.SelectTab(tab.id))
                                        },
                                        onClose = {
                                            onEvent(BrowserUiEvent.CloseTab(tab.id))
                                        },
                                        onAddFavorite = { favTitle, favUrl ->
                                            onEvent(BrowserUiEvent.AddFavorite(favTitle, favUrl))
                                        }
                                    )
                                }
                            }

                            item(key = "dock_add_tab_button") {
                                AddTabDockButton(
                                    isDark = isDark,
                                    backgroundColor = circleBtnBg,
                                    border = centerPillBorder,
                                    onClick = {
                                        onEvent(BrowserUiEvent.OpenNewTab(""))
                                    }
                                )
                            }
                        }

                        // Left edge fade (visible when scrolled right)
                        if (lazyListState.canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(44.dp)
                                    .clip(RoundedCornerShape(topStart = 29.dp, bottomStart = 29.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                dockBg,
                                                dockBg.copy(alpha = 0.95f),
                                                dockBg.copy(alpha = 0.50f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        // Right edge fade (visible when more items to the right)
                        if (lazyListState.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(44.dp)
                                    .clip(RoundedCornerShape(topEnd = 29.dp, bottomEnd = 29.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                dockBg.copy(alpha = 0.50f),
                                                dockBg.copy(alpha = 0.95f),
                                                dockBg
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                // Dedicated Floating Menu Button (58x58dp)
                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    border = null,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .clickable {
                                onEvent(BrowserUiEvent.ToggleMenu())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = if (uiState.isMenuExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
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
private fun ActiveTabCenterCapsule(
    title: String,
    domain: String,
    url: String,
    faviconUrl: String?,
    isHome: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = backgroundColor,
        border = border,
        shadowElevation = 2.dp,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onOpenSearch,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Favicon / Icon glyph
            if (isHome) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (isDark) PastelRose else LiquidTertiary,
                    modifier = Modifier.size(16.dp)
                )
            } else if (!faviconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                val initial = title.trim().firstOrNull()?.uppercaseChar()
                if (initial != null && initial.isLetterOrDigit() && title != "Jestk Browser" && title != "New Tab") {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF3B3436) else LiquidSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (isDark) Color(0xFFF6EFF0) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Connection",
                        tint = if (isDark) PastelRose else LiquidTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Truncated Title & Domain (if not Home)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (domain.isEmpty()) 12.5.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (domain.isNotEmpty()) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Close Tab Action Glyph
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        TabContextMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            isDark = isDark,
            onCloseTab = onClose,
            onCopyUrl = {
                if (url.isNotBlank() && !url.startsWith("about:")) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
                }
            },
            onAddFavorite = {
                if (url.isNotBlank() && !url.startsWith("about:")) {
                    onAddFavorite(title, url)
                    Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                }
            },
            onShareLink = { onShare(url) }
        )
    }
}

@Composable
private fun TabContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    onAddFavorite: () -> Unit,
    onShareLink: (() -> Unit)? = null
) {
    val menuBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)
    val menuBorder = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.85f))

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = menuBg,
        shadowElevation = 12.dp,
        border = menuBorder,
        modifier = Modifier.width(160.dp),
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Close Tab
            PopupMenuItem(
                icon = Icons.Default.Close,
                label = "Close tab",
                isActive = false,
                isDark = isDark,
                onClick = {
                    onDismissRequest()
                    onCloseTab()
                }
            )

            // Copy URL
            PopupMenuItem(
                icon = Icons.Default.ContentCopy,
                label = "Copy URL",
                isActive = false,
                isDark = isDark,
                onClick = {
                    onDismissRequest()
                    onCopyUrl()
                }
            )

            // Add to Favorites
            PopupMenuItem(
                icon = Icons.Default.Bookmark,
                label = "Add to Favorites",
                isActive = false,
                isDark = isDark,
                onClick = {
                    onDismissRequest()
                    onAddFavorite()
                }
            )

            // Share Link
            if (onShareLink != null) {
                PopupMenuItem(
                    icon = Icons.Default.Share,
                    label = "Share Link",
                    isActive = false,
                    isDark = isDark,
                    onClick = {
                        onDismissRequest()
                        onShareLink()
                    }
                )
            }
        }
    }
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val activeBg = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else LiquidPrimaryFixed.copy(alpha = 0.65f)
    val activeTint = if (isDark) Color(0xFF9ECAFC) else MaterialTheme.colorScheme.primary
    val normalTint = if (isDark) Color(0xFFD0C5C6) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeBg else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeTint else normalTint,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isActive) activeTint else normalTint
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InactiveTabButton(
    tab: Tab,
    isDark: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 2.dp,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home"
            if (isHome) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (isDark) Color(0xFFD0C5C6) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            } else if (!tab.faviconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = tab.faviconUrl,
                    contentDescription = tab.title,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                val initial = tab.title.trim().firstOrNull()?.uppercaseChar()
                if (initial != null && initial.isLetterOrDigit() && tab.title != "New Tab") {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = if (isDark) Color(0xFFF6EFF0) else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = tab.title,
                        tint = if (isDark) Color(0xFFD0C5C6) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            TabContextMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                isDark = isDark,
                onCloseTab = onClose,
                onCopyUrl = {
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.url))
                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
                    }
                },
                onAddFavorite = {
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        onAddFavorite(tab.title, tab.url)
                        Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareLink = {
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, tab.url)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Webpage")
                            context.startActivity(shareIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not share link", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AddTabDockButton(
    isDark: Boolean,
    backgroundColor: Color,
    border: BorderStroke?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 2.dp,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = if (isDark) Color(0xFFD0C5C6) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Backward compatibility alias for DualTierBottomBar.
 */
@Composable
fun DualTierBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    UnifiedBottomBar(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier
    )
}

@Preview(name = "BottomBar Light", showBackground = true, backgroundColor = 0xFFC5CED9)
@Composable
fun UnifiedBottomBarLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE8EDF5)),
            contentAlignment = Alignment.BottomCenter
        ) {
            UnifiedBottomBar(
                uiState = BrowserUiState(
                    currentUrl = "https://example.com",
                    pageTitle = "Example Domain",
                    tabs = listOf(
                        Tab(id = "1", url = "https://example.com", title = "Example Domain", isActive = true),
                        Tab(id = "2", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                    ),
                    activeTabId = "1"
                ),
                onEvent = {}
            )
        }
    }
}

@Preview(name = "BottomBar Dark", showBackground = true, backgroundColor = 0xFF100E0F)
@Composable
fun UnifiedBottomBarDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF100E0F)),
            contentAlignment = Alignment.BottomCenter
        ) {
            UnifiedBottomBar(
                uiState = BrowserUiState(
                    currentUrl = "https://example.com",
                    pageTitle = "Example Domain",
                    tabs = listOf(
                        Tab(id = "1", url = "https://example.com", title = "Example Domain", isActive = true),
                        Tab(id = "2", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                    ),
                    activeTabId = "1"
                ),
                onEvent = {}
            )
        }
    }
}

