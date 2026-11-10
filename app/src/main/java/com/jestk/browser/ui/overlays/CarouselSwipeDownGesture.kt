package com.jestk.browser.ui.overlays

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Downward drag on the workspaces carousel that scrubs the "open workspace" transition.
 *
 * [onStart] decides whether the drag is accepted (return false to leave it to the pager).
 * [onDrag] receives the total downward travel since touch-down (never negative).
 * [onEnd] receives that total, the downward release velocity and whether the stream was cancelled.
 */
internal fun Modifier.carouselSwipeDownGesture(
    onStart: () -> Boolean,
    onDrag: (downwardDragPx: Float) -> Unit,
    onEnd: (downwardDragPx: Float, downwardVelocityPxPerSec: Float, cancelled: Boolean) -> Unit,
): Modifier = composed {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)

    pointerInput(Unit) {
        awaitEachGesture {
            // The very first statement inside awaitEachGesture must be a suspending call.
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop > 0f) change.consume()
            } ?: return@awaitEachGesture
            if (!currentOnStart()) return@awaitEachGesture

            velocityTracker.addPosition(slopChange.uptimeMillis, slopChange.position)
            var downward = (slopChange.position.y - down.position.y).coerceAtLeast(0f)
            currentOnDrag(downward)

            val released = verticalDrag(slopChange.id) { change ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                downward = (change.position.y - down.position.y).coerceAtLeast(0f)
                currentOnDrag(downward)
            }
            val velocity = if (released) velocityTracker.calculateVelocity().y else 0f
            currentOnEnd(downward, velocity, !released)
        }
    }
}
