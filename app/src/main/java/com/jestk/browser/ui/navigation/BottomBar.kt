package com.jestk.browser.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.util.Collections
import kotlin.math.abs

@Composable
fun UnifiedBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultBg = MaterialTheme.colorScheme.background
    val isHome = uiState.activeOverlay == OverlayType.HOME ||
            uiState.currentUrl.isEmpty() ||
            uiState.currentUrl == "about:blank"
    val resolvedThemeColor = uiState.activeThemeColor ?: uiState.activeTab?.themeColor
    val targetBgColor = if (isHome) defaultBg else (resolvedThemeColor?.let { Color(it) } ?: defaultBg)
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 350),
        label = "BottomBarDynamicColor"
    )

    val isLightSite = animatedBgColor.luminance() > 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgColor.copy(alpha = 0f),
                        animatedBgColor.copy(alpha = 0.70f),
                        animatedBgColor.copy(alpha = 0.95f),
                        animatedBgColor
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Scrollable Tab Strip (placed in background layer)
            CenterTabStrip(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                isLightSite = isLightSite,
                isHome = isHome,
                isMenuExpanded = uiState.isMenuExpanded,
                onEvent = onEvent
            )

            // Left Edge Gradient Scrim (soft fade under absolute menu button)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(68.dp)
                    .fillMaxHeight()
                    .zIndex(1f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedBgColor.copy(alpha = 0.95f),
                                animatedBgColor.copy(alpha = 0f)
                            )
                        )
                    )
            )

            // Right Edge Gradient Scrim (soft fade under all tabs button)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(68.dp)
                    .fillMaxHeight()
                    .zIndex(1f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedBgColor.copy(alpha = 0f),
                                animatedBgColor.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Absolute Left Menu (Expandable with Arrow >)
            AbsoluteLeftMenu(
                isMenuExpanded = uiState.isMenuExpanded,
                isHome = isHome,
                activeOverlay = uiState.activeOverlay,
                isLightSite = isLightSite,
                onToggleMenu = {
                    if (isHome) {
                        // On home, keep open
                        onEvent(BrowserUiEvent.ToggleMenu(true))
                    } else {
                        onEvent(BrowserUiEvent.ToggleMenu())
                    }
                },
                onOpenHome = {
                    if (!isHome) {
                        onEvent(BrowserUiEvent.SetMenuExpanded(true))
                    }
                    onEvent(BrowserUiEvent.OpenHome)
                },
                onOpenSearch = {
                    if (!isHome) {
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    }
                    onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                },
                onOpenSettings = {
                    if (!isHome) {
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    }
                    onEvent(BrowserUiEvent.OpenSettings)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .zIndex(5f)
            )

            // Absolute Right Button ("All Tabs" Grid)
            AbsoluteAllTabsButton(
                activeOverlay = uiState.activeOverlay,
                isLightSite = isLightSite,
                onClick = {
                    if (!isHome) {
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    }
                    if (uiState.activeOverlay == OverlayType.TAB_GRID) {
                        onEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        onEvent(BrowserUiEvent.OpenTabGrid)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .zIndex(5f)
            )
        }
    }
}

/**
 * Backward compatibility alias for DualTierBottomBar
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

@Composable
private fun AbsoluteLeftMenu(
    isMenuExpanded: Boolean,
    isHome: Boolean,
    activeOverlay: OverlayType,
    isLightSite: Boolean,
    onToggleMenu: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonBgGradient = if (isLightSite) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFEDEDED).copy(alpha = 0.95f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF333842).copy(alpha = 0.95f),
                Color(0xFF1E222B).copy(alpha = 0.95f)
            )
        )
    }

    val buttonBorder = if (isLightSite) {
        BorderStroke(1.dp, Color.Black.copy(alpha = 0.15f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    }

    val iconTint = if (isLightSite) Color(0xFF1F2937) else Color(0xFFF9FAFB)
    val activeIndicatorBg = if (isLightSite) Color(0xFFD1D5DB).copy(alpha = 0.6f) else Color(0xFF4B5563).copy(alpha = 0.6f)
    val dividerColor = if (isLightSite) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.15f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        border = buttonBorder,
        shadowElevation = 6.dp,
        color = Color.Transparent,
        modifier = modifier
            .animateContentSize(animationSpec = tween(280, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .background(buttonBgGradient)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Toggle Button (Arrow right when closed, arrow left when open)
                IconButton(
                    onClick = onToggleMenu,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isMenuExpanded) {
                            Icons.AutoMirrored.Filled.ArrowBackIos
                        } else {
                            Icons.AutoMirrored.Filled.ArrowForwardIos
                        },
                        contentDescription = if (isMenuExpanded) "Close Menu" else "Open Menu",
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanded Action Buttons: Home, Search, Settings
                if (isMenuExpanded) {
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(dividerColor)
                    )

                    // Home Button
                    IconButton(
                        onClick = onOpenHome,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (activeOverlay == OverlayType.HOME || isHome) activeIndicatorBg else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Search Button
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (activeOverlay == OverlayType.SEARCH) activeIndicatorBg else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (activeOverlay == OverlayType.SETTINGS) activeIndicatorBg else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsoluteAllTabsButton(
    activeOverlay: OverlayType,
    isLightSite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonBgGradient = if (isLightSite) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFEDEDED).copy(alpha = 0.95f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF333842).copy(alpha = 0.95f),
                Color(0xFF1E222B).copy(alpha = 0.95f)
            )
        )
    }

    val buttonBorder = if (isLightSite) {
        BorderStroke(1.dp, Color.Black.copy(alpha = 0.15f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    }

    val iconTint = if (isLightSite) Color(0xFF1F2937) else Color(0xFFF9FAFB)
    val isGridActive = activeOverlay == OverlayType.TAB_GRID
    val activeIndicatorBg = if (isLightSite) Color(0xFFD1D5DB).copy(alpha = 0.6f) else Color(0xFF4B5563).copy(alpha = 0.6f)

    Surface(
        shape = CircleShape,
        border = buttonBorder,
        shadowElevation = 6.dp,
        color = Color.Transparent,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(buttonBgGradient)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isGridActive) activeIndicatorBg else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "All Tabs",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CenterTabStrip(
    tabs: List<Tab>,
    activeTabId: String?,
    isLightSite: Boolean,
    isHome: Boolean,
    isMenuExpanded: Boolean,
    onEvent: (BrowserUiEvent) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val activePillWidth = screenWidth * 0.50f
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var currentTabs by remember(tabs) { mutableStateOf(tabs) }
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var menuTabId by remember { mutableStateOf<String?>(null) }

    val activeIndex = currentTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    // Close menu when user starts scrolling tabs (on web pages)
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isHome && isMenuExpanded) {
            onEvent(BrowserUiEvent.SetMenuExpanded(false))
        }
    }

    LaunchedEffect(activeIndex, currentTabs.size) {
        if (currentTabs.isNotEmpty() && activeIndex in currentTabs.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentPadding = PaddingValues(start = 68.dp, end = 68.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(
            items = currentTabs,
            key = { _, tab -> tab.id }
        ) { _, tab ->
            val isActive = tab.id == activeTabId
            val isDraggingThis = draggingTabId == tab.id
            val touchSlop = with(density) { 10.dp.toPx() }

            val tabModifier = Modifier
                .zIndex(if (isDraggingThis) 10f else 0f)
                .graphicsLayer {
                    if (isDraggingThis) {
                        translationX = dragOffsetPx
                        scaleX = 1.05f
                        scaleY = 1.05f
                        shadowElevation = 8.dp.toPx()
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

                            // Open menu immediately upon long press
                            menuTabId = tab.id

                            val currentPointerId = longPress.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val dragChange = event.changes.firstOrNull { it.id == currentPointerId } ?: break

                                if (dragChange.isConsumed) {
                                    break
                                }

                                if (dragChange.changedToUp()) {
                                    dragChange.consume()
                                    break
                                }

                                val dragAmount = dragChange.positionChange()
                                dragOffsetPx += dragAmount.x
                                dragChange.consume()

                                if (!isDragging && abs(dragOffsetPx) > touchSlop) {
                                    isDragging = true
                                    // Close menu as soon as dragging begins
                                    if (menuTabId != null) {
                                        menuTabId = null
                                    }
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

                            if (isDragging) {
                                if (currentTabs != tabs) {
                                    onEvent(BrowserUiEvent.ReorderTabs(currentTabs))
                                }
                            }
                            draggingTabId = null
                            dragOffsetPx = 0f
                            isDragging = false
                        } else {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.isConsumed) {
                                up.consume()
                                if (!isHome && isMenuExpanded) {
                                    onEvent(BrowserUiEvent.SetMenuExpanded(false))
                                }
                                if (isActive) {
                                    onEvent(BrowserUiEvent.OpenSearch(tab.url))
                                } else {
                                    onEvent(BrowserUiEvent.SelectTab(tab.id))
                                }
                            }
                        }
                    }
                }

            if (isActive) {
                ActiveTabPill(
                    tab = tab,
                    width = activePillWidth,
                    isLightSite = isLightSite,
                    isMenuExpanded = menuTabId == tab.id,
                    onDismissMenu = { if (menuTabId == tab.id) menuTabId = null },
                    onClose = { onEvent(BrowserUiEvent.CloseTab(tab.id)) },
                    onAddFavorite = { onEvent(BrowserUiEvent.AddFavorite(tab.title, tab.url)) },
                    modifier = tabModifier
                )
            } else {
                InactiveTabCircle(
                    tab = tab,
                    isLightSite = isLightSite,
                    isMenuExpanded = menuTabId == tab.id,
                    onDismissMenu = { if (menuTabId == tab.id) menuTabId = null },
                    onClose = { onEvent(BrowserUiEvent.CloseTab(tab.id)) },
                    onAddFavorite = { onEvent(BrowserUiEvent.AddFavorite(tab.title, tab.url)) },
                    modifier = tabModifier
                )
            }
        }

        item {
            // Trailing + Action Button
            val addBtnBg = if (isLightSite) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            val addBtnBorder = if (isLightSite) BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            val addBtnTint = if (isLightSite) Color(0xFF374151) else MaterialTheme.colorScheme.onSurfaceVariant

            IconButton(
                onClick = { onEvent(BrowserUiEvent.OpenNewTab("")) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(addBtnBg)
                    .border(addBtnBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = addBtnTint
                )
            }
        }
    }
}

@Composable
private fun ActiveTabPill(
    tab: Tab,
    width: androidx.compose.ui.unit.Dp,
    isLightSite: Boolean,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pillBg = if (isLightSite) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val pillBorder = if (isLightSite) BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val textTint = if (isLightSite) Color(0xFF1F2937) else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isLightSite) Color(0xFF4B5563) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = pillBg,
        border = pillBorder,
        shadowElevation = if (isLightSite) 4.dp else 3.dp,
        modifier = modifier
            .width(width)
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Favicon with fallback to globe
            if (!tab.faviconUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = tab.faviconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    error = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title / Hostname (truncated)
            val displayTitle = when {
                tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                tab.url.isNotEmpty() -> tab.url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                else -> "Search or enter URL"
            }

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = textTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onDismissMenu,
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            DropdownMenuItem(
                text = { Text("Close Tab") },
                leadingIcon = { Icon(Icons.Default.Close, null) },
                onClick = {
                    onDismissMenu()
                    onClose()
                }
            )
            DropdownMenuItem(
                text = { Text("Copy URL") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    onDismissMenu()
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.url))
                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Add to Favorites") },
                leadingIcon = { Icon(Icons.Default.Bookmark, null) },
                onClick = {
                    onDismissMenu()
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        onAddFavorite()
                        Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun InactiveTabCircle(
    tab: Tab,
    isLightSite: Boolean,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val circleBg = if (isLightSite) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val circleBorder = if (isLightSite) BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val iconTint = if (isLightSite) Color(0xFF4B5563) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(circleBg)
            .border(circleBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!tab.faviconUrl.isNullOrEmpty()) {
            SubcomposeAsyncImage(
                model = tab.faviconUrl,
                contentDescription = tab.title,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = tab.title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                },
                error = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = tab.title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = tab.title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onDismissMenu,
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            DropdownMenuItem(
                text = { Text("Close Tab") },
                leadingIcon = { Icon(Icons.Default.Close, null) },
                onClick = {
                    onDismissMenu()
                    onClose()
                }
            )
            DropdownMenuItem(
                text = { Text("Copy URL") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    onDismissMenu()
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.url))
                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No URL to copy", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Add to Favorites") },
                leadingIcon = { Icon(Icons.Default.Bookmark, null) },
                onClick = {
                    onDismissMenu()
                    if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                        onAddFavorite()
                        Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Cannot add Home to Favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
