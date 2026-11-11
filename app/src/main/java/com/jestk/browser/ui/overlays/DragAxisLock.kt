package com.jestk.browser.ui.overlays

import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs

/**
 * Tracks one touch stream and decides once whether it is a horizontal or a vertical gesture, so a
 * vertical swipe with slight horizontal drift never starts a horizontal drag.
 */
internal class DragAxisLock(private val touchSlop: Float) {
    /** Horizontal travel since touch-down; callers may rebase it after consuming a step. */
    var totalX = 0f
    private var totalY = 0f
    private var isVertical = false
    private var isHorizontal = false

    /** True once the stream travelled past touch slop mainly horizontally; latched. */
    val isHorizontalDrag: Boolean get() = isHorizontal

    fun onMove(change: PointerInputChange) {
        totalX += change.position.x - change.previousPosition.x
        totalY += change.position.y - change.previousPosition.y
        if (isVertical || isHorizontal) return
        if (abs(totalY) > touchSlop && abs(totalY) >= abs(totalX)) {
            isVertical = true
        } else if (abs(totalX) > touchSlop) {
            isHorizontal = true
        }
    }
}
