package com.jestk.browser.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoAccent
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoCardSurface
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = if (isDark) DarkSurface else ProstoCardSurface,
) {
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate

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
            contentDescription = stringResource(R.string.dock_menu),
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
                } catch (_: Exception) {
                    false
                }
        }
    val bounceState =
        if (isRunningInTest || LocalInspectionMode.current) {
            null
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "SwipeHintBounce")
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "SwipeHintBounceY",
            )
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
                    val y = bounceState?.value ?: 0f
                    IntOffset(0, y.dp.roundToPx())
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
) {
    val activeBg = if (isDark) Color(0x2656DBCF) else ProstoBrandPrimary.copy(alpha = 0.10f)
    val activeTint = if (isDark) ProstoAccent else ProstoBrandPrimary
    val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
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
 * Interactive dock lift gesture modifier with velocity discrimination.
 *
 * Discrimination rules:
 * - A fast upward flick (duration < 250ms or velocity > 600 dp/s) fires [onSwipeUpSearch].
 * - Interactive upward lift moves the dock up with damping and applies subtle scaling:
 *   - Holding (>180ms) or passing threshold (>60dp) triggers haptic feedback
 *     (HapticFeedbackType.LongPress) and fires [onLiftWorkspaces].
 * - Releasing below threshold springs the dock back to idle without triggering Search or Workspaces.
 */
fun Modifier.dockLiftGestureModifier(
    density: Density,
    hapticFeedback: HapticFeedback? = null,
    vibration: VibrationFeedback? = null,
    onDragOffset: ((Float) -> Unit)? = null,
    onSwipeUpSearch: () -> Unit,
    onLiftWorkspaces: () -> Unit,
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val liftOffset = remember { Animatable(0f) }
    val currentOnSwipeUpSearch by rememberUpdatedState(onSwipeUpSearch)
    val currentOnLiftWorkspaces by rememberUpdatedState(onLiftWorkspaces)
    val currentHapticFeedback = hapticFeedback ?: runCatching { LocalHapticFeedback.current }.getOrNull()
    val currentVibration = vibration ?: runCatching { LocalVibrationFeedback.current }.getOrNull()

    val thresholdPx = with(density) { 60.dp.toPx() }
    val fastVelocityPx = with(density) { -600.dp.toPx() }
    val ultraFastVelocityPx = with(density) { -1000.dp.toPx() }
    val minHoldDragPx = with(density) { 10.dp.toPx() }
    val minFlickDistancePx = with(density) { 24.dp.toPx() }
    val maxLiftPx = with(density) { 32.dp.toPx() }

    this
        .graphicsLayer {
            translationY = liftOffset.value
        }
        .pointerInput(density) {
            awaitEachGesture {
                // CRITICAL LESSON 1: The very first statement inside awaitEachGesture must be a suspending call.
                val down = awaitFirstDown(requireUnconsumed = false)
                val startTime = down.uptimeMillis
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var totalDragY = 0f
                var triggered = false
                var lastEventTime = startTime
                var holdJob: Job? = null

                var overSlop = 0f
                val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    overSlop = over
                }

                if (drag != null) {
                    lastEventTime = drag.uptimeMillis
                    velocityTracker.addPosition(drag.uptimeMillis, drag.position)
                    totalDragY += overSlop

                    coroutineScope.launch {
                        liftOffset.snapTo((totalDragY * 0.35f).coerceIn(-maxLiftPx, 0f))
                        onDragOffset?.invoke(liftOffset.value)
                    }

                    val initialElapsed = drag.uptimeMillis - startTime
                    val remainingHold = (180L - initialElapsed).coerceAtLeast(0L)
                    holdJob = coroutineScope.launch {
                        delay(remainingHold)
                        val velY = velocityTracker.calculateVelocity().y
                        val isMovingFast = velY < -300f
                        if (!triggered && totalDragY <= -minHoldDragPx && !isMovingFast) {
                            triggered = true
                            currentHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentVibration?.confirm()
                            currentOnLiftWorkspaces()
                        }
                    }

                    if (!triggered && totalDragY <= -thresholdPx) {
                        triggered = true
                        holdJob.cancel()
                        currentHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentVibration?.confirm()
                        currentOnLiftWorkspaces()
                    }

                    val dragSuccess = verticalDrag(drag.id) { change ->
                        lastEventTime = change.uptimeMillis
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val dragAmount = change.positionChange().y
                        totalDragY += dragAmount
                        change.consume()

                        coroutineScope.launch {
                            liftOffset.snapTo((totalDragY * 0.35f).coerceIn(-maxLiftPx, 0f))
                            onDragOffset?.invoke(liftOffset.value)
                        }

                        val elapsed = change.uptimeMillis - startTime
                        val velY = velocityTracker.calculateVelocity().y
                        val isMovingFast = velY < -300f
                        if (!triggered && totalDragY < 0f) {
                            val passedDistance = totalDragY <= -thresholdPx
                            val passedHold = elapsed >= 180L && totalDragY <= -minHoldDragPx && !isMovingFast
                            if (passedDistance || passedHold) {
                                triggered = true
                                holdJob.cancel()
                                currentHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentVibration?.confirm()
                                currentOnLiftWorkspaces()
                            }
                        }
                    }

                    holdJob.cancel()

                    if (dragSuccess) {
                        if (!triggered) {
                            val duration = lastEventTime - startTime
                            val velocityY = velocityTracker.calculateVelocity().y
                            val velocityYDp = velocityY / density.density

                            val isFastFlick = (duration < 250L && totalDragY <= -minFlickDistancePx) ||
                                (duration < 250L && (velocityYDp < -600f || velocityY < -500f)) ||
                                velocityYDp < -600f || velocityY < fastVelocityPx || velocityY < ultraFastVelocityPx

                            if (isFastFlick) {
                                triggered = true
                                currentOnSwipeUpSearch()
                            } else if (totalDragY <= -thresholdPx) {
                                triggered = true
                                currentHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentVibration?.confirm()
                                currentOnLiftWorkspaces()
                            }
                        }
                    }

                    coroutineScope.launch {
                        liftOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                        onDragOffset?.invoke(0f)
                    }
                }
            }
        }
}

/**
 * Convenient alias for [dockLiftGestureModifier].
 */
fun Modifier.dockLiftGesture(
    density: Density,
    hapticFeedback: HapticFeedback? = null,
    vibration: VibrationFeedback? = null,
    onDragOffset: ((Float) -> Unit)? = null,
    onSwipeUpSearch: () -> Unit,
    onLiftWorkspaces: () -> Unit,
): Modifier = dockLiftGestureModifier(
    density = density,
    hapticFeedback = hapticFeedback,
    vibration = vibration,
    onDragOffset = onDragOffset,
    onSwipeUpSearch = onSwipeUpSearch,
    onLiftWorkspaces = onLiftWorkspaces,
)

/**
 * Backward-compatibility wrapper for swipeUpToSearch.
 */
fun Modifier.swipeUpToSearch(
    density: Density,
    onSwipeUp: () -> Unit,
): Modifier = dockLiftGestureModifier(
    density = density,
    onSwipeUpSearch = onSwipeUp,
    onLiftWorkspaces = {},
)
