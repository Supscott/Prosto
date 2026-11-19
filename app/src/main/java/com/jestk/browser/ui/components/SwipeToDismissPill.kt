package com.jestk.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DISMISS_FRACTION = 0.4f
private const val FLING_VELOCITY_DP_PER_SEC = 600f
private const val FLY_OUT_MS = 150

/**
 * Lets the user dismiss a floating notification pill with a horizontal swipe (left or right) instead
 * of a close icon. The pill follows the finger and fades; past [DISMISS_FRACTION] of its width or on
 * a fast fling it flies out and [onDismiss] runs, otherwise it springs back. Taps on the pill's own
 * actions are unaffected (dragging starts only after the touch slop). A TalkBack "Dismiss" custom
 * action calls [onDismiss] directly. [onInteraction] reports whether a finger is dragging, so a
 * pill with an auto-dismiss timer can pause it.
 *
 * [resetKey] restarts the gesture state when the pill shows new content.
 */
@Composable
fun Modifier.swipeToDismissPill(
    resetKey: Any?,
    onDismiss: () -> Unit,
    onInteraction: (Boolean) -> Unit = {},
): Modifier {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnInteraction by rememberUpdatedState(onInteraction)
    val scope = rememberCoroutineScope()
    val offsetX = remember(resetKey) { Animatable(0f, visibilityThreshold = 0.5f) }
    var widthPx by remember(resetKey) { mutableFloatStateOf(1f) }
    val flingThresholdPx = with(LocalDensity.current) { FLING_VELOCITY_DP_PER_SEC.dp.toPx() }
    val dismissLabel = stringResource(R.string.dismiss)

    val dragState = rememberDraggableState { delta ->
        scope.launch { offsetX.snapTo(offsetX.value + delta) }
    }

    return this
        .onSizeChanged { widthPx = it.width.coerceAtLeast(1).toFloat() }
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .graphicsLayer {
            val progress = (abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
            alpha = 1f - progress * 0.8f
        }
        .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStarted = { currentOnInteraction(true) },
            onDragStopped = { velocity ->
                currentOnInteraction(false)
                scope.launch {
                    val offset = offsetX.value
                    val flung = abs(velocity) > flingThresholdPx && velocity * offset >= 0f
                    if (abs(offset) > widthPx * DISMISS_FRACTION || flung) {
                        val direction = if (offset != 0f) offset else velocity
                        val target = if (direction >= 0f) widthPx * 1.5f else -widthPx * 1.5f
                        offsetX.animateTo(target, tween(FLY_OUT_MS, easing = FastOutLinearInEasing))
                        currentOnDismiss()
                    } else {
                        offsetX.animateTo(
                            0f,
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                visibilityThreshold = 0.5f,
                            ),
                        )
                    }
                }
            },
        )
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(dismissLabel) {
                    currentOnDismiss()
                    true
                },
            )
        }
}
