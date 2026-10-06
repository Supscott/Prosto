package com.jestk.browser.ui.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkDockSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCardSurface
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.net.URI
import java.util.Collections
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Horizontal scrollable strip of tabs (`LazyRow`).
 * Adheres to Serene Canvas design system:
 * - 50% width active tab capsule ([ActiveTabPill]) with solid surface and light elevation shadow.
 * - Inactive tabs: transparent background, [SereneMutedSlate] icons and text, label-md typography.
 * - Long-press contextual menus ([TabContextMenu]) with solid card surface and hairline dividers.
 * - Persistent '+' new tab button pinned on the right edge ([AddTabDockButton]).
 * - Interactive tab drag reordering.
 * - Left/right edge smooth gradient fade scrims.
 */
@Composable
fun TabStripRow(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current,
    dockBg: Color = if (isDark) DarkDockSurface.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f),
    centerPillBg: Color = if (isDark) DarkDockSurface else Color.White,
    centerPillBorder: BorderStroke? = null,
    circleBtnBg: Color = Color.Transparent
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

        val coroutineScope = rememberCoroutineScope()
        val dragState = rememberTabStripDragState(
            initialTabs = displayTabs,
            onReorder = { reordered -> onEvent(BrowserUiEvent.ReorderTabs(reordered)) },
            coroutineScope = coroutineScope
        )

        LaunchedEffect(displayTabs) {
            dragState.updateTabs(displayTabs)
        }

        val activeIndex = remember(dragState.currentTabs, uiState.activeTabId, uiState.activeTabIndex) {
            val idx = dragState.currentTabs.indexOfFirst { it.id == uiState.activeTabId }
            if (idx >= 0) idx else if (dragState.currentTabs.isNotEmpty()) uiState.activeTabIndex.coerceIn(0, dragState.currentTabs.lastIndex) else 0
        }

        val activeTabWidthPx = with(density) { activeTabWidth.toPx() }
        val totalChipWidthPx = with(density) { totalChipWidth.toPx() }
        val inactiveTabWidthPx = with(density) { 38.dp.toPx() }
        val tabSpacingPx = with(density) { 5.dp.toPx() }

        LaunchedEffect(uiState.activeTabId, activeIndex, dragState.isDragging, dragState.isSettling) {
            if (!dragState.isDragging && !dragState.isSettling && activeIndex in dragState.currentTabs.indices) {
                val viewportWidthPx = lazyListState.layoutInfo.viewportSize.width.takeIf { it > 0 }?.toFloat()
                    ?: totalChipWidthPx
                val centeringOffsetPx = ((viewportWidthPx - activeTabWidthPx) / 2f).coerceAtLeast(0f)
                val scrollOffset = -centeringOffsetPx.toInt()
                lazyListState.animateScrollToItem(activeIndex, scrollOffset)
            }
        }

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = !dragState.isDragging && !dragState.isSettling,
            contentPadding = PaddingValues(start = 5.dp, end = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            itemsIndexed(
                items = dragState.currentTabs,
                key = { _, tab -> tab.id }
            ) { index, tab ->
                val isActive = index == activeIndex
                val tabIsHome = tab.url.isEmpty() || tab.url == "about:blank" || tab.title == "Home" || (isHome && isActive)
                val isDraggingThis = dragState.draggingTabId == tab.id
                val isSettlingThis = dragState.settlingTabId == tab.id
                val isElevated = isDraggingThis || isSettlingThis

                val itemTranslationX = when {
                    isDraggingThis -> dragState.dragOffsetPx
                    isSettlingThis -> dragState.settleOffsetAnim.value
                    else -> 0f
                }
                val itemScale = when {
                    isDraggingThis -> if (isActive) 1.05f else 1.08f
                    isSettlingThis -> dragState.settleScaleAnim.value
                    else -> 1.0f
                }

                val itemPlacementModifier = if (isDraggingThis) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        placementSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )
                }

                if (isActive) {
                    val currentTitle = if (tabIsHome) "Home" else when {
                        tab.title.isNotEmpty() && tab.title != "Home" && tab.title != "New Tab" -> tab.title
                        uiState.pageTitle.isNotEmpty() && uiState.pageTitle != "Home" -> uiState.pageTitle
                        else -> "Hori Browser"
                    }
                    val currentDomain = if (tabIsHome) "" else extractDomain(tab.url)

                    ActiveTabPill(
                        tabId = tab.id,
                        title = currentTitle,
                        domain = currentDomain,
                        url = tab.url.ifEmpty { uiState.currentUrl },
                        faviconUrl = tab.faviconUrl,
                        isHome = tabIsHome,
                        backgroundColor = centerPillBg,
                        border = centerPillBorder,
                        isDark = isDark,
                        isLoading = uiState.isLoading,
                        loadingProgress = uiState.loadingProgress,
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
                        modifier = Modifier
                            .width(activeTabWidth)
                            .then(itemPlacementModifier)
                            .zIndex(if (isElevated) 10f else 0f)
                            .graphicsLayer {
                                translationX = itemTranslationX
                                scaleX = itemScale
                                scaleY = itemScale
                            },
                        onDragStart = { dragState.onDragStart(tab.id) },
                        onDrag = { deltaX ->
                            dragState.onDrag(
                                tabId = tab.id,
                                deltaX = deltaX,
                                activeTabWidthPx = activeTabWidthPx,
                                inactiveTabWidthPx = inactiveTabWidthPx,
                                spacingPx = tabSpacingPx,
                                activeTabId = uiState.activeTabId,
                                onSwapHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            )
                        },
                        onDragEnd = { dragState.onDragEnd(isReleasedActive = true, originalTabs = displayTabs) },
                        onDragCancel = { dragState.onDragCancel(isReleasedActive = true) }
                    )
                } else {
                    InactiveTabButton(
                        tab = tab,
                        isDark = isDark,
                        backgroundColor = circleBtnBg,
                        border = null,
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
                            .then(itemPlacementModifier)
                            .zIndex(if (isElevated) 10f else 0f)
                            .graphicsLayer {
                                translationX = itemTranslationX
                                scaleX = itemScale
                                scaleY = itemScale
                            },
                        onDragStart = { dragState.onDragStart(tab.id) },
                        onDrag = { deltaX ->
                            dragState.onDrag(
                                tabId = tab.id,
                                deltaX = deltaX,
                                activeTabWidthPx = activeTabWidthPx,
                                inactiveTabWidthPx = inactiveTabWidthPx,
                                spacingPx = tabSpacingPx,
                                activeTabId = uiState.activeTabId,
                                onSwapHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            )
                        },
                        onDragEnd = { dragState.onDragEnd(isReleasedActive = false, originalTabs = displayTabs) },
                        onDragCancel = { dragState.onDragCancel(isReleasedActive = false) }
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
                    .width(36.dp)
                    .clip(RoundedCornerShape(topStart = 25.dp, bottomStart = 25.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                dockBg,
                                dockBg.copy(alpha = dockBg.alpha * 0.75f),
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
                .width(42.dp)
                .clip(RoundedCornerShape(topEnd = 25.dp, bottomEnd = 25.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            dockBg.copy(alpha = dockBg.alpha * 0.75f),
                            dockBg
                        )
                    )
                )
        )

        // Pinned New Tab Button (absolute right-0 over tab strip)
        AddTabDockButton(
            isDark = isDark,
            backgroundColor = Color.Transparent,
            border = null,
            onClick = {
                onEvent(BrowserUiEvent.OpenNewTab(""))
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        )
    }
}

/**
 * Circular inactive tab button with favicon or site initial glyph.
 * Transparent background, SereneMutedSlate icons and text, label-md typography.
 */
@Composable
fun InactiveTabButton(
    tab: Tab,
    isDark: Boolean,
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val touchSlop = with(density) { 8.dp.toPx() }
    val typography = LocalSereneTypography.current
    val normalTint = if (isDark) Color(0xFF94A3B8) else SereneMutedSlate

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .pointerInput(tab.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var totalDragX = 0f
                        var isDraggingInternal = false
                        val currentPointerId = longPress.id
                        var cancelled = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val dragChange = event.changes.firstOrNull { it.id == currentPointerId }
                            if (dragChange == null) {
                                cancelled = true
                                break
                            }
                            if (dragChange.changedToUp()) {
                                dragChange.consume()
                                break
                            }

                            val dragAmount = dragChange.positionChange()
                            totalDragX += dragAmount.x
                            dragChange.consume()

                            if (!isDraggingInternal && abs(totalDragX) > touchSlop) {
                                isDraggingInternal = true
                                currentOnDragStart?.invoke()
                            }

                            if (isDraggingInternal) {
                                currentOnDrag?.invoke(dragAmount.x)
                            }
                        }

                        if (isDraggingInternal) {
                            if (cancelled) {
                                currentOnDragCancel?.invoke()
                            } else {
                                currentOnDragEnd?.invoke()
                            }
                        } else if (!cancelled) {
                            showMenu = true
                        }
                    }
                }
            }
            .clickable(onClick = onClick)
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
                    tint = normalTint,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                val hasLocalFavicon = remember(tab.faviconUrl) {
                    FaviconManager.isValidLocalFavicon(tab.faviconUrl)
                }
                var isFaviconSuccess by remember(tab.faviconUrl) { mutableStateOf(hasLocalFavicon) }
                val imageModel = remember(tab.faviconUrl) {
                    FaviconManager.toImageModel(tab.faviconUrl)
                }

                if (!isFaviconSuccess) {
                    val initial = tab.title.trim().firstOrNull()?.uppercaseChar()
                    if (initial != null && initial.isLetterOrDigit() && tab.title != "New Tab") {
                        Text(
                            text = initial.toString(),
                            style = typography.labelMd.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = normalTint
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = tab.title,
                            tint = normalTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = tab.title,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit,
                        onSuccess = { isFaviconSuccess = true },
                        onError = { isFaviconSuccess = false }
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
    backgroundColor: Color = Color.Transparent,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalTint = if (isDark) Color(0xFF94A3B8) else SereneMutedSlate

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier
            .size(38.dp)
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
                tint = normalTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Contextual dropdown menu on tabs (active or inactive).
 * Adheres to Serene Canvas:
 * - Floating card with 24px radius (SereneShapes.Card), pure solid surface.
 * - Rim border and Level 2 elevation shadow.
 * - 48px touch targets for menu rows.
 * - Hairline dividers 1px solid #F1F5F9 (Dark: 1px solid #334155).
 * - Clean Plus Jakarta Sans typography.
 * - NO BLUR.
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
    val menuBg = if (isDark) DarkSurface else SereneCardSurface
    val menuBorder = SereneBorders.floatingBorder(isDark)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = SereneShapes.Card,
        containerColor = menuBg,
        shadowElevation = 12.dp,
        border = menuBorder,
        modifier = Modifier.width(172.dp),
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center
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

            HorizontalDivider(color = dividerColor, thickness = 1.dp)

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

            HorizontalDivider(color = dividerColor, thickness = 1.dp)

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
                HorizontalDivider(color = dividerColor, thickness = 1.dp)

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

/**
 * State controller managing drag & drop tab reordering and tactile spring release physics
 * in [TabStripRow].
 *
 * Provides smooth dragging, immediate slot swapping with haptic feedback,
 * and spring-driven release settlement back to translation 0f and scale 1.0f.
 */
internal class TabStripDragState(
    initialTabs: List<Tab>,
    val coroutineScope: CoroutineScope,
    val onReorder: (List<Tab>) -> Unit
) {
    var currentTabs by mutableStateOf(initialTabs)
        private set

    var draggingTabId by mutableStateOf<String?>(null)
        private set

    var settlingTabId by mutableStateOf<String?>(null)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    val isSettling: Boolean
        get() = settlingTabId != null

    val settleOffsetAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleScaleAnim = Animatable(1.0f)

    private var settleJob: Job? = null
    private var pendingTabs: List<Tab>? = null

    fun updateTabs(newTabs: List<Tab>) {
        if (!isDragging && !isSettling) {
            currentTabs = newTabs
            pendingTabs = null
        } else {
            pendingTabs = newTabs
        }
    }

    fun cancelSettlingImmediately() {
        if (isSettling) {
            settleJob?.cancel()
            settleJob = null
            settlingTabId = null
            coroutineScope.launch {
                settleOffsetAnim.snapTo(0f)
                settleScaleAnim.snapTo(1.0f)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun onDragStart(tabId: String) {
        if (isSettling) {
            cancelSettlingImmediately()
        }
        draggingTabId = tabId
        dragOffsetPx = 0f
        isDragging = true
    }

    fun computeSlotDistance(
        currentIndex: Int,
        targetIndex: Int,
        activeTabWidthPx: Float,
        inactiveTabWidthPx: Float,
        spacingPx: Float,
        activeTabId: String? = null
    ): Float {
        if (currentIndex !in currentTabs.indices || targetIndex !in currentTabs.indices) {
            return inactiveTabWidthPx + spacingPx
        }
        val currentTab = currentTabs[currentIndex]
        val targetTab = currentTabs[targetIndex]
        val isCurrentActive = currentTab.isActive || (activeTabId != null && currentTab.id == activeTabId)
        val isTargetActive = targetTab.isActive || (activeTabId != null && targetTab.id == activeTabId)
        val currentWidth = if (isCurrentActive) activeTabWidthPx else inactiveTabWidthPx
        val targetWidth = if (isTargetActive) activeTabWidthPx else inactiveTabWidthPx
        return (currentWidth + targetWidth) / 2f + spacingPx
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        activeTabWidthPx: Float,
        inactiveTabWidthPx: Float,
        spacingPx: Float,
        activeTabId: String? = null,
        onSwapHaptic: () -> Unit = {}
    ) {
        if (!isDragging || draggingTabId != tabId) return
        dragOffsetPx += deltaX
        val currentIndex = currentTabs.indexOfFirst { it.id == tabId }
        if (currentIndex != -1) {
            if (currentIndex < currentTabs.lastIndex) {
                val targetIndex = currentIndex + 1
                val distance = computeSlotDistance(
                    currentIndex = currentIndex,
                    targetIndex = targetIndex,
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = spacingPx,
                    activeTabId = activeTabId
                )
                if (dragOffsetPx > distance) {
                    val mutable = currentTabs.toMutableList()
                    Collections.swap(mutable, currentIndex, targetIndex)
                    currentTabs = mutable
                    dragOffsetPx -= distance
                    onSwapHaptic()
                    return
                }
            }
            if (currentIndex > 0) {
                val targetIndex = currentIndex - 1
                val distance = computeSlotDistance(
                    currentIndex = currentIndex,
                    targetIndex = targetIndex,
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = spacingPx,
                    activeTabId = activeTabId
                )
                if (dragOffsetPx < -distance) {
                    val mutable = currentTabs.toMutableList()
                    Collections.swap(mutable, currentIndex, targetIndex)
                    currentTabs = mutable
                    dragOffsetPx += distance
                    onSwapHaptic()
                    return
                }
            }
        }
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        swapThreshold: Float,
        onSwapHaptic: () -> Unit = {}
    ) {
        onDrag(
            tabId = tabId,
            deltaX = deltaX,
            activeTabWidthPx = swapThreshold,
            inactiveTabWidthPx = swapThreshold,
            spacingPx = 0f,
            activeTabId = null,
            onSwapHaptic = onSwapHaptic
        )
    }

    fun onDragEnd(isReleasedActive: Boolean, originalTabs: List<Tab>? = null) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        val finalOffset = dragOffsetPx
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        if (originalTabs != null && currentTabs != originalTabs) {
            onReorder(currentTabs)
        }

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f
                    )
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    )
                )
            }
            offsetJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun onDragCancel(isReleasedActive: Boolean) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        val finalOffset = dragOffsetPx
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f
                    )
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    )
                )
            }
            offsetJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }
}

@Composable
internal fun rememberTabStripDragState(
    initialTabs: List<Tab>,
    onReorder: (List<Tab>) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): TabStripDragState {
    val currentOnReorder by rememberUpdatedState(onReorder)
    return remember {
        TabStripDragState(
            initialTabs = initialTabs,
            coroutineScope = coroutineScope,
            onReorder = { currentOnReorder(it) }
        )
    }
}
