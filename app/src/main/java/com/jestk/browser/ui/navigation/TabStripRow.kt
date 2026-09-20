package com.jestk.browser.ui.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.net.URI
import java.util.Collections
import kotlin.math.abs

/**
 * Horizontal scrollable strip of tabs (`LazyRow`).
 * Features:
 * - 50% width active tab capsule ([ActiveTabPill])
 * - Circular inactive tab icons with favicons ([InactiveTabButton])
 * - Long-press contextual menus ([TabContextMenu])
 * - Persistent '+' new tab button pinned on the right ([AddTabDockButton])
 * - Interactive tab drag reordering
 * - Left/right edge gradient fade scrims
 */
@Composable
fun TabStripRow(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current,
    dockBg: Color = if (isDark) Color(0xFF1E1B1C) else LiquidSurface,
    centerPillBg: Color = if (isDark) Color(0xFF2B2728) else Color.White,
    centerPillBorder: BorderStroke? = LiquidBorders.specularBorder(isDark),
    circleBtnBg: Color = if (isDark) Color(0xFF2C2829) else LiquidSurfaceContainerHigh
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val isHome = uiState.isHome

    val activeTab = uiState.activeTab

    val isHomeTab = isHome || activeTab?.url.isNullOrEmpty() || activeTab?.url == "about:blank" || activeTab?.title == "Home"
    val displayTitle = if (isHomeTab) "Home" else when {
        activeTab?.title?.isNotEmpty() == true && activeTab.title != "Home" && activeTab.title != "New Tab" -> activeTab.title
        uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
        else -> "Hori Browser"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalChipWidth = maxWidth
        val activeTabWidth = totalChipWidth * 0.5f
        val lazyListState = rememberLazyListState()

        val displayTabs = remember(uiState.tabs, activeTab) {
            val tabs = if (uiState.tabs.isNotEmpty()) {
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
            tabs.distinctBy { it.id }
        }

        var currentTabs by remember(displayTabs) { mutableStateOf(displayTabs) }
        var draggingTabId by remember { mutableStateOf<String?>(null) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }

        val activeIndex = remember(currentTabs, uiState.activeTabId, uiState.activeTabIndex) {
            val idx = currentTabs.indexOfFirst { it.id == uiState.activeTabId }
            if (idx >= 0) idx else if (currentTabs.isNotEmpty()) uiState.activeTabIndex.coerceIn(0, currentTabs.lastIndex) else 0
        }

        LaunchedEffect(uiState.activeTabId, activeIndex) {
            if (activeIndex in currentTabs.indices) {
                lazyListState.animateScrollToItem(activeIndex)
            }
        }

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 8.dp, end = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = currentTabs,
                key = { _, tab -> tab.id }
            ) { index, tab ->
                val isActive = index == activeIndex
                val tabIsHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home" || (isHome && isActive)

                if (isActive) {
                    val currentTitle = if (tabIsHome) "Home" else when {
                        tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                        uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                        else -> "Hori Browser"
                    }
                    val currentDomain = if (tabIsHome) "" else extractDomain(tab.url)

                    ActiveTabPill(
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
                            shareWebpageUrl(context, urlToShare)
                        },
                        modifier = Modifier.width(activeTabWidth)
                    )
                } else {
                    val isDraggingThis = draggingTabId == tab.id
                    val touchSlop = with(density) { 8.dp.toPx() }

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
                        },
                        modifier = Modifier
                            .zIndex(if (isDraggingThis) 10f else 0f)
                            .graphicsLayer {
                                if (isDraggingThis) {
                                    translationX = dragOffsetPx
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                }
                            }
                            .pointerInput(tab.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val longPress = awaitLongPressOrCancellation(down.id)
                                    if (longPress != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingTabId = tab.id
                                        dragOffsetPx = 0f
                                        isDragging = false

                                        val currentPointerId = longPress.id
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val dragChange = event.changes.firstOrNull { it.id == currentPointerId } ?: break

                                            if (dragChange.isConsumed) break
                                            if (dragChange.changedToUp()) {
                                                dragChange.consume()
                                                break
                                            }

                                            val dragAmount = dragChange.positionChange()
                                            dragOffsetPx += dragAmount.x
                                            dragChange.consume()

                                            if (!isDragging && abs(dragOffsetPx) > touchSlop) {
                                                isDragging = true
                                            }

                                            if (isDragging) {
                                                val currentIndex = currentTabs.indexOfFirst { it.id == draggingTabId }
                                                if (currentIndex != -1) {
                                                    val swapThreshold = with(density) { 44.dp.toPx() }
                                                    if (dragOffsetPx > swapThreshold && currentIndex < currentTabs.lastIndex) {
                                                        val targetIndex = currentIndex + 1
                                                        val mutable = currentTabs.toMutableList()
                                                        Collections.swap(mutable, currentIndex, targetIndex)
                                                        currentTabs = mutable
                                                        dragOffsetPx -= swapThreshold
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (dragOffsetPx < -swapThreshold && currentIndex > 0) {
                                                        val targetIndex = currentIndex - 1
                                                        val mutable = currentTabs.toMutableList()
                                                        Collections.swap(mutable, currentIndex, targetIndex)
                                                        currentTabs = mutable
                                                        dragOffsetPx += swapThreshold
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        }

                                        if (isDragging && currentTabs != displayTabs) {
                                            onEvent(BrowserUiEvent.ReorderTabs(currentTabs))
                                        }
                                        draggingTabId = null
                                        dragOffsetPx = 0f
                                        isDragging = false
                                    }
                                }
                            }
                    )
                }
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

        // Right edge horizontal gradient scrim beneath the pinned button
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(44.dp)
                .clip(RoundedCornerShape(topEnd = 29.dp, bottomEnd = 29.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            dockBg.copy(alpha = 0.8f),
                            dockBg
                        )
                    )
                )
        )

        // Pinned New Tab Button (absolute right-0 over tab strip)
        AddTabDockButton(
            isDark = isDark,
            backgroundColor = circleBtnBg,
            border = centerPillBorder,
            onClick = {
                onEvent(BrowserUiEvent.OpenNewTab(""))
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        )
    }
}

/**
 * Circular inactive tab button with favicon or site initial glyph.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InactiveTabButton(
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
        shadowElevation = 0.dp,
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
                val imageModel = remember(tab.faviconUrl) {
                    FaviconManager.toImageModel(tab.faviconUrl)
                }
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = tab.title,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }
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
                    copyUrlToClipboard(context, tab.url)
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
                    shareWebpageUrl(context, tab.url)
                }
            )
        }
    }
}

/**
 * Pinned persistent circular '+' new tab button on the right edge of the dock.
 */
@Composable
fun AddTabDockButton(
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
        shadowElevation = 0.dp,
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
 * Contextual dropdown menu on tabs (active or inactive).
 */
@Composable
fun TabContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDark: Boolean,
    onCloseTab: () -> Unit,
    onCopyUrl: () -> Unit,
    onAddFavorite: () -> Unit,
    onShareLink: (() -> Unit)? = null
) {
    val menuBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.88f)
    val menuBorder = LiquidBorders.frostedBorder(isDark, alpha = if (isDark) 0.14f else 0.85f)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = LiquidShapes.Card,
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

private fun extractDomain(url: String): String {
    if (url.isEmpty() || url == "about:blank") return ""
    return try {
        URI(url).host?.removePrefix("www.") ?: url.removePrefix("https://").removePrefix("http://")
    } catch (_: Exception) {
        url
    }
}
