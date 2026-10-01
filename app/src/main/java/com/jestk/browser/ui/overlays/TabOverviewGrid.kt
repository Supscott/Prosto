package com.jestk.browser.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.theme.sereneSkyBackground
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.io.File
import kotlin.math.roundToInt

/**
 * Tab Overview Grid - Liquid Pastel Sunset design system.
 * Features:
 * - Top navigation bar with back button, "Tabs Overview", and sunset orb icon
 * - Sub-header with tab count, search in tabs, and gradient "+" add button
 * - 2-column grid with aspect-[4/5] previews
 * - Active tab highlighted with multi-color gradient border (#7BA7D7 -> #F6B99D -> #D391A5)
 * - Floating bottom frosted "Done" pill button
 */
@Composable
fun TabOverviewGrid(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSearchVisible by remember { mutableStateOf(false) }
    var tabSearchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = isSearchVisible) {
        isSearchVisible = false
        tabSearchQuery = ""
        keyboardController?.hide()
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val displayedTabs = remember(uiState.tabs, tabSearchQuery) {
        if (tabSearchQuery.isBlank()) uiState.tabs
        else uiState.tabs.filter {
            it.title.contains(tabSearchQuery, ignoreCase = true) ||
            it.url.contains(tabSearchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .sereneSkyBackground(isDark = isDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Tabs Overview",
                    style = LocalSereneTypography.current.headlineSm,
                    color = if (isDark) DarkOnSurface else SereneDarkText
                )

                // Clean Badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Sub-Header: Tab Count & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabCountText = if (tabSearchQuery.isNotBlank()) {
                    "${displayedTabs.size} of ${uiState.tabs.size} tabs"
                } else if (uiState.tabs.size == 1) {
                    "1 tab"
                } else {
                    "${uiState.tabs.size} tabs"
                }
                Text(
                    text = tabCountText,
                    style = LocalSereneTypography.current.headlineMd,
                    color = if (isDark) DarkOnSurface else SereneDarkText
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search in tabs button
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) {
                                tabSearchQuery = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in tabs",
                            tint = if (isSearchVisible) SereneBrandPrimary else SereneMutedSlate,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Solid "+" Add Tab Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SereneBrandPrimary)
                            .clickable {
                                onEvent(BrowserUiEvent.OpenNewTab(""))
                                onEvent(BrowserUiEvent.DismissOverlay)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Expandable in-place tab search filter bar
            AnimatedVisibility(visible = isSearchVisible) {
                Surface(
                    shape = SereneShapes.Pill,
                    color = if (isDark) DarkSurface else Color.White,
                    border = SereneBorders.cardBorder(isDark),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SereneMutedSlate,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = tabSearchQuery,
                            onValueChange = { tabSearchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            textStyle = LocalSereneTypography.current.bodyMd.copy(
                                color = if (isDark) DarkOnSurface else SereneDarkText
                            ),
                            cursorBrush = SolidColor(SereneBrandPrimary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (tabSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Filter tabs by title or URL...",
                                            style = LocalSereneTypography.current.bodyMd,
                                            color = SereneMutedSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (tabSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    tabSearchQuery = ""
                                    searchFocusRequester.requestFocus()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = SereneMutedSlate,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. 2-Column Vertical Grid with aspect-[4/5] preview cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = displayedTabs,
                    key = { it.id }
                ) { tab ->
                    TwoColumnGridTabCard(
                        tab = tab,
                        isActive = tab.id == uiState.activeTabId,
                        isDark = isDark,
                        onSelect = {
                            onEvent(BrowserUiEvent.SelectTab(tab.id))
                            onEvent(BrowserUiEvent.DismissOverlay)
                        },
                        onClose = {
                            onEvent(BrowserUiEvent.CloseTab(tab.id))
                        }
                    )
                }
            }
        }

        // 4. Floating Bottom Action Bar (Done pill + Close All)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close All Button
            TextButton(
                onClick = { onEvent(BrowserUiEvent.CloseAllTabs) }
            ) {
                Text(
                    text = "Close All",
                    color = MaterialTheme.colorScheme.error,
                    style = LocalSereneTypography.current.labelMd.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Floating Centered "Done" Pill Button
            Surface(
                shape = SereneShapes.Pill,
                color = if (isDark) DarkSurface else Color.White,
                border = SereneBorders.floatingBorder(isDark),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .height(46.dp)
                    .clickable { onEvent(BrowserUiEvent.DismissOverlay) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        color = SereneBrandPrimary,
                        style = LocalSereneTypography.current.labelMd.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoColumnGridTabCard(
    tab: Tab,
    isActive: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var isDismissed by remember { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "TabDismissDrag"
    )
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 90.dp.toPx() } }

    val baseCardBg = if (isDark) DarkSurface else Color.White
    val borderStroke = if (isActive) BorderStroke(2.dp, SereneBrandPrimary) else SereneBorders.cardBorder(isDark)

    AnimatedVisibility(
        visible = !isDismissed,
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
    ) {
        Surface(
            shape = SereneShapes.Card,
            color = baseCardBg,
            border = borderStroke,
            shadowElevation = if (isActive) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clip(SereneShapes.Card)
                .clickable(onClick = onSelect)
                .pointerInput(tab.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (rawOffsetX > dismissThresholdPx || rawOffsetX < -dismissThresholdPx) {
                                isDismissed = true
                                onClose()
                            } else {
                                rawOffsetX = 0f
                            }
                        },
                        onDragCancel = { rawOffsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            rawOffsetX += dragAmount
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Strip (Favicon + Truncated Title + Close X)
                val headerBg = if (isActive) {
                    if (isDark) SereneBrandPrimary.copy(alpha = 0.20f) else SerenePaleBlue
                } else {
                    if (isDark) DarkSurfaceVariant.copy(alpha = 0.50f) else SereneCanvasFloor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(headerBg)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Favicon / Icon
                    var isFaviconSuccess by remember(tab.faviconUrl) { mutableStateOf(false) }
                    val imageModel = remember(tab.faviconUrl) {
                        FaviconManager.toImageModel(tab.faviconUrl)
                    }

                    if (!isFaviconSuccess) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape),
                            onSuccess = { isFaviconSuccess = true },
                            onError = { isFaviconSuccess = false }
                        )
                    }

                    // Title
                    Text(
                        text = tab.title.ifEmpty { "New Tab" },
                        style = LocalSereneTypography.current.labelMd.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isActive) SereneBrandPrimary else (if (isDark) DarkOnSurface else SereneDarkText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = if (isActive) SereneBrandPrimary else SereneMutedSlate,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Thumbnail Snapshot Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (isDark) DarkBackground else SereneCanvasFloor)
                ) {
                    val file = tab.snapshotPath?.let { File(it) }
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = tab.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Clean Placeholder
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = SereneMutedSlate.copy(alpha = 0.40f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridLightPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
        TabOverviewGrid(
            uiState = com.jestk.browser.ui.viewmodel.BrowserUiState(
                tabs = listOf(
                    com.jestk.browser.domain.model.Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin Programming", isActive = true),
                    com.jestk.browser.domain.model.Tab(id = "2", url = "https://developer.android.com", title = "Android Developers", isActive = false),
                    com.jestk.browser.domain.model.Tab(id = "3", url = "https://github.com", title = "GitHub", isActive = false),
                    com.jestk.browser.domain.model.Tab(id = "4", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                ),
                activeTabId = "1"
            ),
            onEvent = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TabOverviewGrid Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun TabOverviewGridDarkPreview() {
    com.jestk.browser.ui.theme.JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.DARK) {
        TabOverviewGrid(
            uiState = com.jestk.browser.ui.viewmodel.BrowserUiState(
                tabs = listOf(
                    com.jestk.browser.domain.model.Tab(id = "1", url = "https://kotlinlang.org", title = "Kotlin Programming", isActive = true),
                    com.jestk.browser.domain.model.Tab(id = "2", url = "https://developer.android.com", title = "Android Developers", isActive = false),
                    com.jestk.browser.domain.model.Tab(id = "3", url = "https://github.com", title = "GitHub", isActive = false),
                    com.jestk.browser.domain.model.Tab(id = "4", url = "https://duckduckgo.com", title = "DuckDuckGo", isActive = false)
                ),
                activeTabId = "1"
            ),
            onEvent = {}
        )
    }
}
