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
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Animated popup actions card anchored above the Menu button.
 * Adheres to Prosto Canvas design system:
 * - Floating card with 24px radius (ProstoShapes.Card), pure solid #FFFFFF (Dark: #1E293B).
 * - Level 2 elevation shadow and rim border.
 * - 48px touch targets for menu rows.
 * - Hairline dividers 1px solid #F1F5F9 (Dark: 1px solid #334155).
 * - Clean typography (body-md / label-md Inter).
 * - NO BLUR or backdrop haze.
 */
@Composable
fun ActionPopupMenu(
    expanded: Boolean,
    uiState: BrowserUiState,
    isDark: Boolean,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHome = uiState.isHome
    val menuBg = if (isDark) DarkSurface else ProstoCardSurface
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    AnimatedVisibility(
        visible = expanded,
        enter =
            fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.90f, animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 3 },
        exit =
            fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.90f, animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(bottom = 8.dp, end = 16.dp)
                    .width(172.dp)
                    .prostoFloatingSurface(
                        shape = ProstoShapes.Card,
                        isDark = isDark,
                        backgroundColor = menuBg,
                        elevation = 12.dp,
                    ),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.Center,
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
                    },
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Search
                PopupMenuItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    isActive = uiState.activeOverlay == OverlayType.SEARCH,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenSearch(prefilledUrl = null, openInNewTab = true))
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    },
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Tabs
                PopupMenuItem(
                    icon = Icons.Default.FilterNone,
                    label = "Tabs",
                    isActive = uiState.activeOverlay == OverlayType.TAB_GRID,
                    isDark = isDark,
                    trailingLabel = uiState.tabs.size.toString(),
                    onClick = {
                        onEvent(BrowserUiEvent.OpenTabGrid)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    },
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Downloads
                PopupMenuItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    isActive = uiState.activeScreen == ScreenType.DOWNLOADS,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenDownloads)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    },
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Settings
                PopupMenuItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isActive = uiState.activeScreen == ScreenType.SETTINGS,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenSettings)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    },
                )
            }
        }
    }
}

/**
 * Dedicated Floating Menu Button in the dock row.
 * Solid crisp surface, Level 2 rim border, active ripple, NO BLUR.
 */
@Composable
fun ActionDockMenuButton(
    isExpanded: Boolean,
    isDark: Boolean,
    backgroundColor: Color = if (isDark) DarkSurface else ProstoCardSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) Color(0xFF94A3B8) else ProstoMutedSlate

    Box(
        modifier =
            modifier
                .size(width = 44.dp, height = 50.dp)
                .prostoFloatingSurface(
                    shape = ProstoShapes.Card,
                    isDark = isDark,
                    backgroundColor = backgroundColor,
                    elevation = 10.dp,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Menu",
            tint = if (isExpanded) activeTint else normalTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Subtle, stylish prompt above the floating dock when Home is active,
 * guiding users to the swipe-up gesture to search.
 * Solid pill surface with micro-border, NO BLUR.
 */
@Composable
fun SwipeUpHint(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunningInTest =
        remember {
            android.os.Build.FINGERPRINT == "robolectric" ||
                try {
                    Class.forName("org.robolectric.Robolectric") != null
                } catch (_: Throwable) {
                    false
                }
        }
    val bounceOffset =
        if (isRunningInTest || LocalInspectionMode.current) {
            0f
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "SwipeHintBounce")
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "SwipeHintBounceY",
            )
            bounce
        }

    val typography = LocalProstoTypography.current

    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier =
            modifier
                .offset(y = bounceOffset.dp)
                .clip(ProstoShapes.Pill)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Swipe up to search",
                tint = if (isDark) ProstoAccent else ProstoBrandPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Swipe up to search",
                style =
                    typography.labelSm.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = if (isDark) Color(0xFF94A3B8) else ProstoMutedSlate,
            )
        }
    }
}

/**
 * Reusable menu item row used in popup actions and context menus.
 * 48px touch targets, active press ripple, Manrope title and Inter body typography.
 */
@Composable
fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isDark: Boolean,
    trailingLabel: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBg = if (isDark) Color(0x2656DBCF) else ProstoBrandPrimary.copy(alpha = 0.10f)
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) Color(0xFF94A3B8) else ProstoMutedSlate
    val typography = LocalProstoTypography.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeBg else Color.Transparent,
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeTint else normalTint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                modifier = if (trailingLabel != null) Modifier.weight(1f) else Modifier,
                style =
                    typography.bodyMd.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                color =
                    if (isActive) {
                        activeTint
                    } else if (isDark) {
                        Color(0xFFF1F5F9)
                    } else {
                        ProstoDarkText
                    },
            )
            trailingLabel?.let { trailingText ->
                Text(
                    text = trailingText,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    style = typography.labelSm.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isDark) Color(0xFFCBD5E1) else ProstoMutedSlate,
                )
            }
        }
    }
}

/**
 * Gesture modifier detecting upward swipe to trigger search.
 */
fun Modifier.swipeUpToSearch(
    density: Density,
    onSwipeUp: () -> Unit,
): Modifier =
    this.pointerInput(density) {
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
            },
        )
    }
