package com.jestk.browser.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import kotlin.math.abs

/**
 * Reports whether the current touch stream is a vertical gesture. The axis is decided once, shortly
 * before touch slop, from the dominant direction, and stays fixed until the pointer lifts, so a
 * vertical swipe with slight horizontal drift never scrolls a horizontal list.
 * Events are only observed, never consumed.
 */
internal fun Modifier.verticalAxisLock(onLockChanged: (Boolean) -> Unit): Modifier = composed {
    val decisionPx = LocalViewConfiguration.current.touchSlop * 0.5f
    pointerInput(decisionPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var decided = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!decided) {
                    val dx = abs(change.position.x - down.position.x)
                    val dy = abs(change.position.y - down.position.y)
                    if (maxOf(dx, dy) > decisionPx) {
                        decided = true
                        if (dy > dx) onLockChanged(true)
                    }
                }
            }
            onLockChanged(false)
        }
    }
}
