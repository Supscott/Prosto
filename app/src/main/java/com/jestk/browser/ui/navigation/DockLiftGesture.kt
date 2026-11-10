package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                    val remainingHold = (210L - initialElapsed).coerceAtLeast(0L)
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
                        if (!triggered && totalDragY <= -minHoldDragPx && elapsed >= 210L && !isMovingFast) {
                            triggered = true
                            holdJob?.cancel()
                            currentHapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentVibration?.confirm()
                            currentOnLiftWorkspaces()
                        }
                    }

                    holdJob?.cancel()

                    if (dragSuccess) {
                        if (!triggered) {
                            val duration = lastEventTime - startTime
                            val velocityY = velocityTracker.calculateVelocity().y
                            val velocityYDp = velocityY / density.density

                            val isFastFlick = (duration < 250L && totalDragY <= -minFlickDistancePx) ||
                                (duration < 250L && (velocityYDp < -500f || velocityY < -500f)) ||
                                velocityYDp < -500f || velocityY < fastVelocityPx || velocityY < ultraFastVelocityPx

                            if (isFastFlick) {
                                triggered = true
                                currentOnSwipeUpSearch()
                            } else if (duration >= 210L && totalDragY <= -thresholdPx) {
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
