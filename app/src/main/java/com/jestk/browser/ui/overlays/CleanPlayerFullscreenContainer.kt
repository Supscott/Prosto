package com.jestk.browser.ui.overlays

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fullscreen container for YouTube Clean Player HTML5 video playback.
 * Renders the custom video surface view edge-to-edge and supports swipe-down-to-exit gesture.
 */
@Composable
fun CleanPlayerFullscreenContainer(
    customView: View,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 70.dp.toPx() } }
    val velocityThresholdPx = remember(density) { with(density) { 1000.dp.toPx() } }
    val minDragDistancePx = remember(density) { with(density) { 20.dp.toPx() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val startY = down.position.y
                    val startX = down.position.x
                    var totalDeltaY = 0f
                    var totalDeltaX = 0f
                    var hasTriggered = false
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change =
                            event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (change.changedToUp() || !change.pressed) {
                            if (!hasTriggered && totalDeltaY > minDragDistancePx &&
                                totalDeltaY > kotlin.math.abs(totalDeltaX) * 1.2f
                            ) {
                                val velocityY = velocityTracker.calculateVelocity().y
                                if (totalDeltaY > swipeThresholdPx || velocityY > velocityThresholdPx) {
                                    hasTriggered = true
                                    onDismiss()
                                }
                            }
                            break
                        }

                        val currentPos = change.position
                        totalDeltaY = currentPos.y - startY
                        totalDeltaX = currentPos.x - startX

                        if (!hasTriggered && totalDeltaY > swipeThresholdPx &&
                            totalDeltaY > kotlin.math.abs(totalDeltaX) * 1.2f
                        ) {
                            hasTriggered = true
                            change.consume()
                            onDismiss()
                            break
                        }
                    }
                }
            },
    ) {
        AndroidView<View>(
            factory = {
                (customView.parent as? ViewGroup)?.removeView(customView)
                customView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    keepScreenOn = true
                    if (this is ViewGroup) {
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    }
                    if (!hasFocus()) {
                        requestFocus()
                    }
                }
            },
            update = { view ->
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                view.keepScreenOn = true
            },
            onReset = { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
