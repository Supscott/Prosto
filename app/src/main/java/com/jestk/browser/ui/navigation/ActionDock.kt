package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidOnSurfaceVariant
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LocalHazeState
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.PastelRose
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import dev.chrisbanes.haze.HazeState

/**
 * The grounded 4-button dock (Home, Search, Tabs Overview, Settings).
 * Features:
 * - Scroll-driven auto-hide offset translation via [BrowserUiState.bottomBarOffsetY]
 * - Swipe-up gesture to search
 * - Liquid frosted glass container
 */
@Composable
fun ActionDock(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current,
    hazeState: HazeState? = LocalHazeState.current
) {
    val density = LocalDensity.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val isHome = uiState.isHome

    val dockBg = if (isDark) Color(0xFF1E1B1C) else LiquidSurface

    Surface(
        shape = LiquidShapes.Dock,
        color = dockBg,
        border = LiquidBorders.specularBorder(isDark),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                translationY = uiState.bottomBarOffsetY
            }
            .swipeUpToSearch(density) {
                currentOnEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Home Button
            IconButton(
                onClick = {
                    if (isHome && uiState.activeOverlay == OverlayType.HOME) {
                        currentOnEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        currentOnEvent(BrowserUiEvent.OpenHome)
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (isHome && uiState.activeOverlay == OverlayType.HOME)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search Button
            IconButton(
                onClick = {
                    if (uiState.activeOverlay == OverlayType.SEARCH) {
                        currentOnEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        currentOnEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (uiState.activeOverlay == OverlayType.SEARCH)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tabs Overview Button
            IconButton(
                onClick = {
                    if (uiState.activeOverlay == OverlayType.TAB_GRID) {
                        currentOnEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        currentOnEvent(BrowserUiEvent.OpenTabGrid)
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterNone,
                    contentDescription = "Tabs Overview",
                    tint = if (uiState.activeOverlay == OverlayType.TAB_GRID)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Settings Button
            IconButton(
                onClick = {
                    if (uiState.activeScreen == ScreenType.SETTINGS) {
                        currentOnEvent(BrowserUiEvent.DismissOverlay)
                    } else {
                        currentOnEvent(BrowserUiEvent.OpenSettings)
                    }
                },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (uiState.activeScreen == ScreenType.SETTINGS)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Animated popup actions card anchored above the Menu button.
 * Contains: Home, Search, Tabs, Downloads, Settings.
 */
@Composable
fun ActionPopupMenu(
    expanded: Boolean,
    uiState: BrowserUiState,
    isDark: Boolean,
    hazeState: HazeState?,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHome = uiState.isHome

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.90f, animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.90f, animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        modifier = modifier
    ) {
        Surface(
            shape = LiquidShapes.Card,
            color = Color.Transparent,
            border = null,
            shadowElevation = 12.dp,
            modifier = Modifier
                .padding(bottom = 8.dp, end = 16.dp)
                .width(148.dp)
                .liquidFrostedGlass(
                    shape = LiquidShapes.Card,
                    isDark = isDark,
                    blurRadius = 24.dp,
                    tintColor = if (isDark) Color(0xFF1E1B1C) else Color.White,
                    alpha = if (isDark) 0.55f else 0.50f,
                    hazeState = hazeState
                )
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

                // Downloads
                PopupMenuItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    isActive = uiState.activeScreen == ScreenType.DOWNLOADS,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenDownloads)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    }
                )

                // Settings
                PopupMenuItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isActive = uiState.activeScreen == ScreenType.SETTINGS,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenSettings)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    }
                )
            }
        }
    }
}

/**
 * Dedicated Floating Menu Button (rounded rectangle 38x58dp) in the dock row.
 */
@Composable
fun ActionDockMenuButton(
    isExpanded: Boolean,
    isDark: Boolean,
    backgroundColor: Color,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = LiquidShapes.Card,
        color = backgroundColor,
        border = LiquidBorders.specularBorder(isDark),
        shadowElevation = 6.dp,
        modifier = modifier
            .size(width = 38.dp, height = 58.dp)
            .clip(LiquidShapes.Card)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Subtle, stylish prompt above the floating dock when Home is active,
 * guiding users to the swipe-up gesture to search.
 */
@Composable
fun SwipeUpHint(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunningInTest = remember {
        android.os.Build.FINGERPRINT == "robolectric" ||
            try {
                Class.forName("org.robolectric.Robolectric") != null
            } catch (_: Throwable) {
                false
            }
    }
    val bounceOffset = if (isRunningInTest || LocalInspectionMode.current) {
        0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "SwipeHintBounce")
        val bounce by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "SwipeHintBounceY"
        )
        bounce
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF2C2829).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.90f),
        border = LiquidBorders.frostedBorder(isDark, alpha = if (isDark) 0.14f else 0.85f),
        shadowElevation = 2.dp,
        modifier = modifier
            .offset(y = bounceOffset.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Swipe up to search",
                tint = if (isDark) PastelRose else LiquidPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Swipe up to search",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (isDark) Color(0xFFD0C5C6) else LiquidOnSurfaceVariant
            )
        }
    }
}

/**
 * Reusable menu item row used in popup actions and context menus.
 */
@Composable
fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBg = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else LiquidPrimaryFixed.copy(alpha = 0.65f)
    val activeTint = if (isDark) Color(0xFF9ECAFC) else MaterialTheme.colorScheme.primary
    val normalTint = if (isDark) Color(0xFFD0C5C6) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeBg else Color.Transparent,
        modifier = modifier
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

/**
 * Gesture modifier detecting upward swipe to trigger search.
 */
fun Modifier.swipeUpToSearch(
    density: Density,
    onSwipeUp: () -> Unit
): Modifier = this.pointerInput(density) {
    val swipeThresholdPx = with(density) { 30.dp.toPx() }
    var totalDragY = 0f
    var triggered = false
    var velocityTracker = VelocityTracker()

    detectVerticalDragGestures(
        onDragStart = {
            totalDragY = 0f
            triggered = false
            velocityTracker = VelocityTracker()
        },
        onDragEnd = {
            if (!triggered) {
                val velocityY = velocityTracker.calculateVelocity().y
                if (totalDragY < -swipeThresholdPx || (totalDragY < 0f && velocityY < -500f)) {
                    triggered = true
                    onSwipeUp()
                }
            }
        },
        onDragCancel = {
            totalDragY = 0f
            triggered = false
        },
        onVerticalDrag = { change, dragAmount ->
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            totalDragY += dragAmount
            if (!triggered && totalDragY < -swipeThresholdPx) {
                triggered = true
                onSwipeUp()
            }
        }
    )
}
