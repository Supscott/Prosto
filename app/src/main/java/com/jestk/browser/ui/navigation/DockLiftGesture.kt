package com.jestk.browser.ui.navigation

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
 * Continuous dock swipe-up gesture mirroring Android's recents: once the finger passes the touch
 * slop upward, every move reports the total upward travel to [onDrag], so the caller can make the
 * whole screen follow the finger. [onDragEnd] receives the upward release velocity (positive is
 * upward) and whether the pointer stream was cancelled instead of released.
 *
 * Downward drags are ignored and left unconsumed; horizontal drags are claimed by the tab strip
 * before the vertical slop is reached.
 */
fun Modifier.dockSwipeUpGesture(
    onDragStart: () -> Unit,
    onDrag: (upwardDragPx: Float) -> Unit,
    onDragEnd: (upwardVelocityPxPerSec: Float, cancelled: Boolean) -> Unit,
): Modifier = composed {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    pointerInput(Unit) {
        awaitEachGesture {
            // The very first statement inside awaitEachGesture must be a suspending call.
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            // Only an upward slop is consumed; a downward one keeps waiting and ends unclaimed.
            val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop < 0f) change.consume()
            } ?: return@awaitEachGesture

            velocityTracker.addPosition(slopChange.uptimeMillis, slopChange.position)
            currentOnDragStart()
            currentOnDrag(down.position.y - slopChange.position.y)

            val released = verticalDrag(slopChange.id) { change ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                currentOnDrag(down.position.y - change.position.y)
            }
            val upwardVelocity = if (released) -velocityTracker.calculateVelocity().y else 0f
            currentOnDragEnd(upwardVelocity, !released)
        }
    }
}
