package com.jestk.browser.ui.overlays

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.sign

/** What dropping the ghost at its current position would mean for the card under it. */
internal enum class TabOverviewDropZone {
    /** The ghost centre is not over the target card. */
    None,

    /** Near half of the target: release creates or joins a folder. */
    Group,

    /** Far half of the target (or an ineligible target): the placeholder moves to its slot. */
    Reorder,
}

private const val HYSTERESIS_FRACTION = 0.1f
private const val CENTER_TIE_PX = 1.5f

/**
 * Decides the drop zone from what the user watches, the ghost card, not from the finger.
 *
 * The target is the card the ghost centre is over. The card is split across the drag direction
 * (the axis along which the ghost has travelled further from [referenceCenter], the dragged
 * item's current slot, measured in card sizes): the half the ghost enters from is [Group], the
 * other half is [Reorder]. A target that cannot absorb the dragged item always means [Reorder].
 *
 * [previous] (the last decision for this same target, [TabOverviewDropZone.None] on first contact)
 * moves the boundary 10% of the card size, away from the zone already chosen, so a finger that
 * rests on the boundary does not flicker between the two.
 */
internal fun decideTabOverviewDropZone(
    ghostRect: Rect,
    targetRect: Rect,
    referenceCenter: Offset,
    eligibleForGrouping: Boolean,
    previous: TabOverviewDropZone,
): TabOverviewDropZone {
    val ghostCenter = ghostRect.center
    if (!targetRect.contains(ghostCenter)) return TabOverviewDropZone.None
    if (!eligibleForGrouping) return TabOverviewDropZone.Reorder

    val dx = ghostCenter.x - referenceCenter.x
    val dy = ghostCenter.y - referenceCenter.y
    val horizontal = abs(dx) / targetRect.width >= abs(dy) / targetRect.height
    val targetCenter = targetRect.center

    // Signed distance of the ghost centre past the target centre, positive on the far side.
    val progress: Float
    val cardExtent: Float
    if (horizontal) {
        progress = sign(dx) * (ghostCenter.x - targetCenter.x)
        cardExtent = targetRect.width
    } else {
        progress = sign(dy) * (ghostCenter.y - targetCenter.y)
        cardExtent = targetRect.height
    }
    val hysteresis = cardExtent * HYSTERESIS_FRACTION
    val reorderAbove =
        when (previous) {
            TabOverviewDropZone.Group -> hysteresis
            TabOverviewDropZone.Reorder -> -hysteresis
            // First contact: the exact centre, give or take rounding, still counts as near.
            TabOverviewDropZone.None -> CENTER_TIE_PX
        }
    return if (progress > reorderAbove) TabOverviewDropZone.Reorder else TabOverviewDropZone.Group
}
