package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Custom PopupPositionProvider that anchors the tab context menu horizontally to the
 * pressed tab and vertically aligns its bottom edge to the standard dock elevation height
 * (matching ActionPopupMenu's 64.dp elevation above the navigation bar / dock bottom).
 */
internal class TabContextMenuPositionProvider(
    private val density: Density,
    private val margin: androidx.compose.ui.unit.Dp = 12.dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val marginPx = with(density) { margin.roundToPx() }
        val isPill = anchorBounds.height >= with(density) { 40.dp.roundToPx() }
        // For pill (42dp): 14dp gap above pill top places bottom at 64dp above navigation bar
        // For circle (38dp): 16dp gap above circle top aligns to the exact same 64dp elevation
        val bottomGapPx = with(density) { (if (isPill) 14.dp else 16.dp).roundToPx() }

        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))

        val targetBottomY = anchorBounds.top - bottomGapPx
        val y = (targetBottomY - popupContentSize.height).coerceAtLeast(marginPx)

        return IntOffset(x, y)
    }
}

/**
 * Clipping shape that allows shadow to spread outwards on top, left, and right,
 * but clips at the bottom boundary (size.height + dockGapPx) so the modal's downward
 * elevation shadow does not overlay onto the dock surface, keeping the shadow
 * visually underneath the dock capsule.
 */
internal class DockShadowUnderClipShape(
    val dockGapPx: Float,
    val overflowPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect =
            Rect(
                left = -overflowPx,
                top = -overflowPx,
                right = size.width + overflowPx,
                bottom = size.height + dockGapPx,
            )
        return Outline.Rectangle(rect)
    }
}
