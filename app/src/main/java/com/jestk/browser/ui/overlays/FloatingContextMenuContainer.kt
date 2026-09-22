package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.liquidFrostedGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Floating contextual modal container that anchors near/under the user's touch location,
 * matching standard modern mobile browser interactions (e.g. Chrome / Safari).
 *
 * Positioning rules:
 * - Below the finger if vertical space allows, with smooth scale-down from touch anchor.
 * - Above the finger if close to screen bottom or bottom navigation bar.
 * - Horizontally clamped within safe margins, centered near the finger touch.
 * - Dismisses on tapping anywhere on the outside scrim.
 */
@Composable
fun FloatingContextMenuContainer(
    anchor: Offset?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current

    val animScale = remember { Animatable(0.92f) }
    val animAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            animAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        launch {
            animScale.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 480f))
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        var cardSize by remember { mutableStateOf(IntSize.Zero) }

        // Margins and clearances
        val horizontalMarginPx = with(density) { 16.dp.toPx() }
        val verticalMarginTopPx = with(density) { 52.dp.toPx() } // Status bar clearance
        val verticalMarginBottomPx = with(density) { 76.dp.toPx() } // Bottom dock clearance
        val fingerGapPx = with(density) { 14.dp.toPx() } // Gap from touch point

        val defaultCardWidthPx = with(density) { 300.dp.toPx() }
        val defaultCardHeightPx = with(density) { 230.dp.toPx() }

        val actualCardWidthPx = if (cardSize.width > 0) cardSize.width.toFloat() else defaultCardWidthPx
        val actualCardHeightPx = if (cardSize.height > 0) cardSize.height.toFloat() else defaultCardHeightPx

        // Horizontal positioning: center on anchor.x, clamp to screen bounds
        val finalX =
            if (anchor != null && anchor.x > 0f) {
                val desiredX = anchor.x - (actualCardWidthPx / 2f)
                val minX = horizontalMarginPx
                val maxX = (screenWidthPx - actualCardWidthPx - horizontalMarginPx).coerceAtLeast(minX)
                desiredX.coerceIn(minX, maxX)
            } else {
                ((screenWidthPx - actualCardWidthPx) / 2f).coerceAtLeast(horizontalMarginPx)
            }

        // Vertical positioning: under finger if fits, else above finger, else centered
        val isAnchorProvided = anchor != null && anchor.y > 0f
        val fitsBelow = isAnchorProvided && (anchor.y + fingerGapPx + actualCardHeightPx <= screenHeightPx - verticalMarginBottomPx)
        val fitsAbove = isAnchorProvided && (anchor.y - fingerGapPx - actualCardHeightPx >= verticalMarginTopPx)

        val finalY =
            when {
                fitsBelow -> anchor.y + fingerGapPx
                fitsAbove -> anchor.y - fingerGapPx - actualCardHeightPx
                isAnchorProvided -> {
                    val spaceBelow = screenHeightPx - verticalMarginBottomPx - (anchor.y + fingerGapPx)
                    val spaceAbove = anchor.y - fingerGapPx - verticalMarginTopPx
                    val target =
                        if (spaceBelow >= spaceAbove) {
                            anchor.y + fingerGapPx
                        } else {
                            anchor.y - fingerGapPx - actualCardHeightPx
                        }
                    val minY = verticalMarginTopPx
                    val maxY = (screenHeightPx - actualCardHeightPx - verticalMarginBottomPx).coerceAtLeast(minY)
                    target.coerceIn(minY, maxY)
                }
                else -> {
                    val target = (screenHeightPx - actualCardHeightPx) * 0.44f
                    val minY = verticalMarginTopPx
                    val maxY = (screenHeightPx - actualCardHeightPx - verticalMarginBottomPx).coerceAtLeast(minY)
                    target.coerceIn(minY, maxY)
                }
            }

        // Bloom pivot origin from touch point
        val pivotX =
            if (anchor != null && actualCardWidthPx > 0f) {
                ((anchor.x - finalX) / actualCardWidthPx).coerceIn(0.1f, 0.9f)
            } else {
                0.5f
            }
        val pivotY = if (fitsBelow) 0f else if (fitsAbove) 1f else 0.5f
        val transformOrigin = TransformOrigin(pivotX, pivotY)

        val cardBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.94f) else Color(0xFFFFF8F8).copy(alpha = 0.96f)

        // 1. Outside dimming scrim - tapping dismisses
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.35f else 0.20f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )

        // 2. Floating Context Menu Card
        Surface(
            shape = LiquidShapes.Card,
            color = cardBg,
            border = LiquidBorders.specularBorder(isDark),
            shadowElevation = 16.dp,
            modifier =
                Modifier
                    .offset { IntOffset(finalX.roundToInt(), finalY.roundToInt()) }
                    .onSizeChanged { cardSize = it }
                    .graphicsLayer {
                        scaleX = animScale.value
                        scaleY = animScale.value
                        alpha = animAlpha.value
                        this.transformOrigin = transformOrigin
                    }
                    .widthIn(min = 280.dp, max = 320.dp)
                    .liquidFrostedGlass(
                        shape = LiquidShapes.Card,
                        isDark = isDark,
                        blurRadius = 24.dp,
                        tintColor = if (isDark) Color(0xFF1E1B1C) else Color(0xFFFFF8F8),
                        alpha = if (isDark) 0.65f else 0.60f,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Prevent dismissing when clicking the card itself
                    ),
        ) {
            content()
        }
    }
}

/**
 * Reusable compact action row for context menus.
 */
@Composable
fun ContextMenuActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    titleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
