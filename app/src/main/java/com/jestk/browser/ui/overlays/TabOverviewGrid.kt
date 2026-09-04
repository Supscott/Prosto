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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jestk.browser.ui.theme.ActiveTabBorderBrush
import com.jestk.browser.ui.theme.AddTabGradientBrush
import com.jestk.browser.ui.theme.LiquidError
import com.jestk.browser.ui.theme.LiquidOnSurface
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryContainer
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.PastelPeach
import com.jestk.browser.ui.theme.liquidSunsetBackground
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
    val isDark = isSystemInDarkTheme()

    var isSearchVisible by remember { mutableStateOf(false) }
    var tabSearchQuery by remember { mutableStateOf("") }

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
            .liquidSunsetBackground(isDark = isDark)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Tabs Overview",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Sunset Gradient Orb Badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AddTabGradientBrush)
                )
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
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search in tabs button
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) tabSearchQuery = ""
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in tabs",
                            tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Gradient "+" Add Tab Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AddTabGradientBrush)
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
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDark) Color(0xFF242021).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.90f),
                    border = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.80f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = LiquidPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        OutlinedTextField(
                            value = tabSearchQuery,
                            onValueChange = { tabSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Filter tabs by title or URL...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = LiquidPrimaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (tabSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { tabSearchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.outline,
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Floating Centered "Done" Pill Button
            val doneBtnBg = if (isDark) Color(0xFF2C2829).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.88f)
            val doneBorder = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.60f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = doneBtnBg,
                border = doneBorder,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onEvent(BrowserUiEvent.DismissOverlay) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(
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

    val baseCardBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)
    val inactiveBorder = if (isDark) BorderStroke(1.dp, Color(0xFF4D4546).copy(alpha = 0.50f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.80f))

    AnimatedVisibility(
        visible = !isDismissed,
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
    ) {
        // Outer container handles the active multi-color gradient border
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = baseCardBg,
            border = if (isActive) BorderStroke(2.5.dp, ActiveTabBorderBrush) else inactiveBorder,
            shadowElevation = if (isActive) 8.dp else 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(24.dp))
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
                    if (isDark) LiquidPrimary.copy(alpha = 0.35f) else LiquidPrimaryFixed.copy(alpha = 0.50f)
                } else {
                    if (isDark) Color(0xFF282425).copy(alpha = 0.60f) else LiquidSurfaceContainerHigh.copy(alpha = 0.50f)
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
                    if (!tab.faviconUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = tab.faviconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(PastelPeach.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else (if (isDark) PastelPeach else LiquidSecondary),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Title
                    Text(
                        text = tab.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Thumbnail Snapshot Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (isDark) Color(0xFF161314) else Color(0xFFF9F2F3))
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Subtle Bottom Gradient Overlay for visual polish
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        LiquidTertiary.copy(alpha = 0.12f)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}
