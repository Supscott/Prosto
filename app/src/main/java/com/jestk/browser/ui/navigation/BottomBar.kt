package com.jestk.browser.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import java.util.Collections
import kotlin.math.abs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

@Composable
fun DualTierBottomBar(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tier 1: Horizontal Tab Strip
            Tier1TabStrip(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                onEvent = onEvent
            )

            // Tier 2: Persistent Action Dock (hides when scrolling down on websites)
            AnimatedVisibility(
                visible = uiState.isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Tier2ActionDock(
                        activeOverlay = uiState.activeOverlay,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun Tier1TabStrip(
    tabs: List<Tab>,
    activeTabId: String?,
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

    val inactiveCount = (currentTabs.size - 1).coerceAtLeast(0)
    val totalContentWidth = activePillWidth + (44.dp * inactiveCount) + (8.dp * currentTabs.size) + 44.dp
    val fitsOnScreen = totalContentWidth <= (screenWidth - 32.dp)

    val horizontalPadding = if (fitsOnScreen) {
        16.dp
    } else {
        ((screenWidth - activePillWidth) / 2).coerceAtLeast(16.dp)
    }

    LaunchedEffect(activeIndex, fitsOnScreen, currentTabs.size) {
        if (!fitsOnScreen && currentTabs.isNotEmpty() && activeIndex in currentTabs.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
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
                    isMenuExpanded = menuTabId == tab.id,
                    onDismissMenu = { if (menuTabId == tab.id) menuTabId = null },
                    onClose = { onEvent(BrowserUiEvent.CloseTab(tab.id)) },
                    onAddFavorite = { onEvent(BrowserUiEvent.AddFavorite(tab.title, tab.url)) },
                    modifier = tabModifier
                )
            } else {
                InactiveTabCircle(
                    tab = tab,
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
            IconButton(
                onClick = { onEvent(BrowserUiEvent.OpenNewTab("")) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveTabPill(
    tab: Tab,
    width: androidx.compose.ui.unit.Dp,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 3.dp,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    error = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurface,
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
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), CircleShape),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                },
                error = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = tab.title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = tab.title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun Tier2ActionDock(
    activeOverlay: OverlayType,
    onEvent: (BrowserUiEvent) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Home Button
            IconButton(
                onClick = {
                    if (activeOverlay == OverlayType.HOME) {
                        onEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        onEvent(BrowserUiEvent.OpenHome)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeOverlay == OverlayType.HOME)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (activeOverlay == OverlayType.HOME)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search Button
            IconButton(
                onClick = {
                    if (activeOverlay == OverlayType.SEARCH) {
                        onEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        onEvent(BrowserUiEvent.OpenSearch(null))
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeOverlay == OverlayType.SEARCH)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (activeOverlay == OverlayType.SEARCH)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // All Tabs (Grid) Button
            IconButton(
                onClick = {
                    if (activeOverlay == OverlayType.TAB_GRID) {
                        onEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        onEvent(BrowserUiEvent.OpenTabGrid)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeOverlay == OverlayType.TAB_GRID)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "All Tabs",
                    tint = if (activeOverlay == OverlayType.TAB_GRID)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Settings Button
            IconButton(
                onClick = {
                    if (activeOverlay == OverlayType.SETTINGS) {
                        onEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        onEvent(BrowserUiEvent.OpenSettings)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeOverlay == OverlayType.SETTINGS)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (activeOverlay == OverlayType.SETTINGS)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
