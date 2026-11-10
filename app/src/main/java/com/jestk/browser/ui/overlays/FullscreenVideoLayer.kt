package com.jestk.browser.ui.overlays

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jestk.browser.R
import com.jestk.browser.ui.views.FullscreenViewHost
import com.jestk.browser.ui.views.FullscreenViewHostOwner
import com.jestk.browser.util.findActivity
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Shows the WebView's HTML5 fullscreen view with the swipe-down exit gesture.
 *
 * The [FullscreenViewHost] container stays composed (at zero size) even when nothing is
 * fullscreen, because `onShowCustomView` attaches the view to it synchronously. The same
 * AndroidView node must be kept across the transition: a new node would detach the fullscreen
 * view again and the page would report itself hidden.
 */
@Composable
fun FullscreenVideoLayer(
    customView: View?,
    isFullscreen: Boolean,
    onExitFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val host = remember(context) {
        (context.findActivity() as? FullscreenViewHostOwner)?.fullscreenViewHost ?: FullscreenViewHost(context)
    }
    val isShowing = isFullscreen && customView != null
    val currentOnExit by rememberUpdatedState(onExitFullscreen)
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 70.dp.toPx() } }
    val velocityThresholdPx = remember(density) { with(density) { 1000.dp.toPx() } }
    val minDragDistancePx = remember(density) { with(density) { 20.dp.toPx() } }

    val layerModifier = if (isShowing) {
        Modifier
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
                                totalDeltaY > abs(totalDeltaX) * 1.2f
                            ) {
                                val velocityY = velocityTracker.calculateVelocity().y
                                if (totalDeltaY > swipeThresholdPx || velocityY > velocityThresholdPx) {
                                    hasTriggered = true
                                    currentOnExit()
                                }
                            }
                            break
                        }

                        totalDeltaY = change.position.y - startY
                        totalDeltaX = change.position.x - startX

                        if (!hasTriggered && totalDeltaY > swipeThresholdPx &&
                            totalDeltaY > abs(totalDeltaX) * 1.2f
                        ) {
                            hasTriggered = true
                            change.consume()
                            currentOnExit()
                            break
                        }
                    }
                }
            }
    } else {
        Modifier.size(0.dp)
    }

    Box(modifier = layerModifier) {
        AndroidView(
            factory = {
                (host.container.parent as? ViewGroup)?.removeView(host.container)
                host.container
            },
            update = {
                // Covers hosts without an owner Activity; attach() is a no-op when already attached.
                if (isShowing) host.attach(customView)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isShowing) {
            FullscreenExitHint(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun FullscreenExitHint(modifier: Modifier = Modifier) {
    var showExitHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3500)
        showExitHint = false
    }

    AnimatedVisibility(
        visible = showExitHint,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
        exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400)) { -it },
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.8f),
            contentColor = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.full_screen_exit_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
