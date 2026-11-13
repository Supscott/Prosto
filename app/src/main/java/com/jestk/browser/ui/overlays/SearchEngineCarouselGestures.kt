package com.jestk.browser.ui.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Modifier.searchEngineButtonGestures(
    carouselState: SearchEngineCarouselState,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val viewConfig = LocalViewConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    return this.pointerInput(carouselState) {
        val velocityTracker = VelocityTracker()
        var pendingSingleTapJob: Job? = null
        var lastTapUpTime = 0L
        var lastTapUpPosition: Offset? = null
        val doubleTapTimeoutMillis = viewConfig.doubleTapTimeoutMillis
        val maxDoubleTapSlop = viewConfig.touchSlop * 2f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTime = down.uptimeMillis
            val timeSinceLastTapUp = downTime - lastTapUpTime
            val isWithinSlop = lastTapUpPosition?.let { prevPos ->
                (down.position - prevPos).getDistance() <= maxDoubleTapSlop
            } ?: false

            val isPotentialDoubleTap = currentOnDoubleTap != null &&
                lastTapUpTime > 0L &&
                timeSinceLastTapUp in 0L..doubleTapTimeoutMillis &&
                isWithinSlop

            if (isPotentialDoubleTap) {
                pendingSingleTapJob?.cancel()
                pendingSingleTapJob = null
            }

            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            val axis = DragAxisLock(viewConfig.touchSlop)
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!isDrag) {
                        val isTap = !axis.isVerticalDrag && (change.uptimeMillis - downTime) < viewConfig.longPressTimeoutMillis
                        if (isTap) {
                            if (currentOnDoubleTap != null) {
                                if (isPotentialDoubleTap) {
                                    lastTapUpTime = 0L
                                    lastTapUpPosition = null
                                    currentOnDoubleTap?.invoke()
                                } else {
                                    lastTapUpTime = change.uptimeMillis
                                    lastTapUpPosition = change.position
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = coroutineScope.launch {
                                        delay(doubleTapTimeoutMillis)
                                        lastTapUpTime = 0L
                                        lastTapUpPosition = null
                                        currentOnClick()
                                    }
                                }
                            } else {
                                currentOnClick()
                            }
                        } else {
                            lastTapUpTime = 0L
                            lastTapUpPosition = null
                        }
                    } else {
                        lastTapUpTime = 0L
                        lastTapUpPosition = null
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                axis.onMove(change)
                if (!isDrag && axis.isHorizontalDrag) {
                    isDrag = true
                    pendingSingleTapJob?.cancel()
                    pendingSingleTapJob = null
                    lastTapUpTime = 0L
                    lastTapUpPosition = null
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
internal fun Modifier.searchEngineInputGestures(
    carouselState: SearchEngineCarouselState,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val viewConfig = LocalViewConfiguration.current

    return this.pointerInput(enabled, carouselState) {
        val velocityTracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            val axis = DragAxisLock(viewConfig.touchSlop)
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (isDrag) {
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                axis.onMove(change)
                if (!isDrag && axis.isHorizontalDrag) {
                    isDrag = true
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
internal fun Modifier.horizontalSwipeCycler(
    density: Density = LocalDensity.current,
    enabled: Boolean = true,
    thresholdDp: Dp = 64.dp,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(density) { thresholdDp.toPx() }

    return this.pointerInput(enabled, density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val axis = DragAxisLock(viewConfiguration.touchSlop)
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                axis.onMove(change)
                if (axis.isHorizontalDrag) {
                    isDrag = true
                }
                if (isDrag) {
                    if (axis.totalX <= -thresholdPx) {
                        axis.totalX += thresholdPx
                        change.consume()
                        currentOnSwipeLeft()
                    } else if (axis.totalX >= thresholdPx) {
                        axis.totalX -= thresholdPx
                        change.consume()
                        currentOnSwipeRight()
                    }
                }
            }
        }
    }
}
