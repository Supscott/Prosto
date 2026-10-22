package com.jestk.browser.ui.overlays

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jestk.browser.R
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoDarkText
import com.jestk.browser.ui.theme.ProstoShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating Prosto Canvas notification pill offering to open a web link or intent in a native app.
 * Features:
 * - Auto-dismiss after 5 seconds, paused while interacting/touching.
 * - Horizontal swipe-to-dismiss gesture with spring physics.
 * - Action button ("Открыть") and dismiss button ("✕").
 * - Shows app icon and specific app name if available.
 */
@Composable
fun ExternalAppNotificationPill(
    prompt: ExternalAppPrompt,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissDurationMs: Long = 5000L,
    onTimeout: () -> Unit = onDismiss
) {
    val isDark = LocalIsDarkTheme.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember(prompt.id) { Animatable(0f, visibilityThreshold = 0.5f) }
    var isInteracting by remember(prompt.id) { mutableStateOf(false) }
    var remainingTimeMs by remember(prompt.id) { mutableLongStateOf(autoDismissDurationMs) }

    // Auto-dismiss timer: counts down every 100ms when not touched/interacting
    LaunchedEffect(prompt.id, isInteracting) {
        if (!isInteracting && remainingTimeMs > 0L) {
            while (remainingTimeMs > 0L) {
                delay(100L)
                if (!isInteracting) {
                    remainingTimeMs = (remainingTimeMs - 100L).coerceAtLeast(0L)
                    if (remainingTimeMs <= 0L) {
                        currentOnTimeout()
                        break
                    }
                }
            }
        }
    }

    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 90.dp.toPx() }

    Surface(
        shape = ProstoShapes.Pill,
        color = if (isDark) DarkSurface else Color.White,
        border = ProstoBorders.floatingBorder(isDark),
        shadowElevation = 8.dp,
        modifier = modifier
            .wrapContentSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                val progress = (abs(offsetX.value) / (dismissThresholdPx * 1.5f)).coerceIn(0f, 1f)
                alpha = 1f - progress * 0.7f
            }
            .pointerInput(prompt.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isInteracting = true
                    },
                    onDragEnd = {
                        isInteracting = false
                        coroutineScope.launch {
                            val currentOffset = offsetX.value
                            if (abs(currentOffset) > dismissThresholdPx) {
                                val targetX = if (currentOffset > 0) currentOffset + 800f else currentOffset - 800f
                                offsetX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
                                )
                                currentOnDismiss()
                            } else {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        visibilityThreshold = 0.5f
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        isInteracting = false
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    visibilityThreshold = 0.5f
                                )
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // App Icon or generic open-in-new icon
            val appIconBitmap = remember(prompt.appIcon) {
                prompt.appIcon?.let { icon ->
                    BitmapFactory.decodeByteArray(icon.pngBytes, 0, icon.pngBytes.size)?.asImageBitmap()
                }
            }
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = ProstoBrandPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Descriptive text
            val titleText = if (prompt.appName.isNotBlank()) {
                stringResource(R.string.external_app_open_in, prompt.appName)
            } else {
                stringResource(R.string.external_app_open_generic)
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else ProstoDarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            // Open Action Button
            Text(
                text = stringResource(R.string.external_app_open_action),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = ProstoBrandPrimary
                ),
                modifier = Modifier
                    .clickable { currentOnOpen() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )

            // Dismiss Button ("✕")
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.dismiss),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { currentOnDismiss() },
            )
        }
    }
}
