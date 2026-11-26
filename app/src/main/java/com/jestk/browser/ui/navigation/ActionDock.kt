package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalActiveWorkspaceColor
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoDestructive
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoShapes
import com.jestk.browser.ui.theme.prostoFloatingSurface
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/** The three-dots menu is at least this wide, grows to its widest label, and never past the max. */
internal val ACTION_MENU_MIN_WIDTH = 172.dp
internal val ACTION_MENU_MAX_WIDTH = 240.dp

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
                    // Follows the widest label (also at a large font scale), within the clamp.
                    .widthIn(min = ACTION_MENU_MIN_WIDTH, max = ACTION_MENU_MAX_WIDTH)
                    .width(IntrinsicSize.Max)
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
                    label = stringResource(R.string.tab_home_title),
                    isActive = isHome && uiState.activeOverlay == OverlayType.HOME,
                    isDark = isDark,
                    onClick = {
                        onEvent(BrowserUiEvent.OpenHome)
                        onEvent(BrowserUiEvent.SetMenuExpanded(false))
                    },
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Tabs
                PopupMenuItem(
                    icon = Icons.Default.FilterNone,
                    label = stringResource(R.string.menu_tabs),
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
                    label = stringResource(R.string.downloads_title),
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
                    label = stringResource(R.string.settings),
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
 * Floating square button beside the dock capsule (search, menu).
 * Solid crisp surface, Level 2 rim border, active ripple, NO BLUR.
 * [isActive] tints the icon with the workspace accent color.
 */
@Composable
fun DockSideButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = if (isDark) DarkSurface else ProstoCardSurface,
    border: BorderStroke? = null,
) {
    val workspaceColor = LocalActiveWorkspaceColor.current
    val activeTint = workspaceColor ?: if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate

    Box(
        modifier =
            modifier
                .size(width = 44.dp, height = 50.dp)
                .prostoFloatingSurface(
                    shape = ProstoShapes.Card,
                    isDark = isDark,
                    backgroundColor = backgroundColor,
                    border = border,
                    elevation = 10.dp,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeTint else normalTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val SWIPE_HINT_BOUNCES = 3

/**
 * Subtle, stylish prompt above the floating dock when Home is active,
 * guiding users to the swipe-up gesture that changes the workspace.
 * Solid pill surface with micro-border, NO BLUR.
 */
@Composable
fun SwipeUpHint(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A few bounces draw the eye, then the hint rests. A finite animation also lets tests and
    // Robolectric reach idle without detecting the test environment.
    val bounceOffset = remember { Animatable(0f) }
    val isPreview = LocalInspectionMode.current
    LaunchedEffect(isPreview) {
        if (isPreview) return@LaunchedEffect
        repeat(SWIPE_HINT_BOUNCES) {
            bounceOffset.animateTo(-3f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
            bounceOffset.animateTo(0f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
        }
    }

    val typography = LocalProstoTypography.current

    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else ProstoCardSurface,
        border = ProstoBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier =
            modifier
                .offset {
                    IntOffset(0, bounceOffset.value.dp.roundToPx())
                }
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
                contentDescription = stringResource(R.string.dock_swipe_up_hint),
                tint = if (isDark) ProstoAccent else ProstoBrandPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.dock_swipe_up_hint),
                style =
                    typography.labelSm.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    destructive: Boolean = false,
) {
    val activeBg = if (isDark) Color(0x2656DBCF) else ProstoBrandPrimary.copy(alpha = 0.10f)
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (destructive) ProstoDestructive else if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
    val typography = LocalProstoTypography.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) activeBg else Color.Transparent,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                // One merged node: the row text is the label, the icon stays decorative (PRO-192).
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeTint else normalTint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                // The longest Russian label needs a third line at font scale 1.3 within the 240dp cap.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style =
                    typography.bodyMd.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                color =
                    if (isActive) {
                        activeTint
                    } else if (destructive) {
                        ProstoDestructive
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
