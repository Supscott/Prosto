package com.jestk.browser.ui.navigation

import com.jestk.browser.domain.model.TabFolder

/**
 * Encapsulates dimension metrics and hysteresis configuration for tab strip drag & drop reordering.
 */
internal data class TabStripLayoutMetrics(
    val activeTabWidthPx: Float,
    val inactiveTabWidthPx: Float,
    val spacingPx: Float,
    val activeTabId: String? = null,
    val hysteresisPx: Float = 10f,
    val draggingTabId: String? = null,
    val itemWidthProvider: ((String) -> Float?)? = null,
    val folderProvider: ((String) -> TabFolder?)? = null,
) {
    fun getItemWidth(key: String, fallbackWidth: Float): Float {
        return itemWidthProvider?.invoke(key) ?: fallbackWidth
    }
}

/**
 * Computes proportional auto-scroll step when a tab is dragged near dock boundaries.
 * Returns negative delta to scroll left (backward), positive delta to scroll right (forward),
 * or 0f when the item is within comfortable bounds or the list cannot scroll further.
 */
internal fun computeAutoScrollDelta(
    itemStartInViewport: Float,
    itemEndInViewport: Float,
    viewportWidthPx: Float,
    rightPinnedOffsetPx: Float,
    edgeTriggerThresholdPx: Float,
    maxScrollStepPx: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Float {
    val rightLimit = viewportWidthPx - rightPinnedOffsetPx
    return when {
        itemStartInViewport < edgeTriggerThresholdPx && canScrollBackward -> {
            val fraction = ((edgeTriggerThresholdPx - itemStartInViewport) / edgeTriggerThresholdPx).coerceIn(0.2f, 1f)
            -maxScrollStepPx * fraction
        }
        itemEndInViewport > (rightLimit - edgeTriggerThresholdPx) && canScrollForward -> {
            val fraction = ((itemEndInViewport - (rightLimit - edgeTriggerThresholdPx)) / edgeTriggerThresholdPx).coerceIn(0.2f, 1f)
            maxScrollStepPx * fraction
        }
        else -> 0f
    }
}
