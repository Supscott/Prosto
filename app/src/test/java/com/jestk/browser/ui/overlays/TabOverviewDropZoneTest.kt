package com.jestk.browser.ui.overlays

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabOverviewDropZoneTest {
    private val card = Size(100f, 140f)

    /** Three cards in a row at x = 0, 110, 220 and a second row at y = 150. */
    private fun slot(col: Int, row: Int = 0) = Rect(Offset(col * 110f, row * 150f), card)

    private fun ghostAt(center: Offset) = Rect(center - Offset(card.width / 2, card.height / 2), card)

    private fun decide(
        ghostCenter: Offset,
        target: Rect,
        gap: Rect,
        eligible: Boolean = true,
        previous: TabOverviewDropZone = TabOverviewDropZone.None,
    ) = decideTabOverviewDropZone(ghostAt(ghostCenter), target, gap.center, eligible, previous)

    @Test
    fun dragLeftOntoNeighbour_nearHalfGroups_farHalfMoves() {
        val target = slot(0)
        val gap = slot(1)
        // The ghost enters tab 1 from the right: its right half is the near half.
        assertThat(decide(Offset(target.right - 5f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(target.center.x + 20f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(target.center.x - 20f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Reorder)
        assertThat(decide(Offset(target.left + 3f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }

    @Test
    fun dragRightOntoNeighbour_nearHalfIsLeft() {
        val target = slot(2)
        val gap = slot(1)
        assertThat(decide(Offset(target.left + 5f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(target.right - 5f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }

    @Test
    fun dragAcrossRows_splitsAlongVerticalAxis() {
        val target = slot(1, row = 1)
        val gap = slot(1, row = 0)
        // Moving down: the top half groups, the bottom half moves.
        assertThat(decide(Offset(target.center.x, target.top + 10f), target, gap))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(target.center.x, target.bottom - 10f), target, gap))
            .isEqualTo(TabOverviewDropZone.Reorder)
        // Moving up from the second row into the first: the bottom half groups.
        assertThat(decide(Offset(slot(1).center.x, slot(1).bottom - 10f), slot(1), slot(1, row = 1)))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(slot(1).center.x, slot(1).top + 10f), slot(1), slot(1, row = 1)))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }

    @Test
    fun hysteresisKeepsTheCurrentDecisionNearTheBoundary() {
        val target = slot(0)
        val gap = slot(1)
        // Dragging left, 5 px (5% of the card) past the centre.
        val justPast = Offset(target.center.x - 5f, target.center.y)
        assertThat(decide(justPast, target, gap, previous = TabOverviewDropZone.Group))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(justPast, target, gap, previous = TabOverviewDropZone.Reorder))
            .isEqualTo(TabOverviewDropZone.Reorder)
        assertThat(decide(justPast, target, gap)).isEqualTo(TabOverviewDropZone.Reorder)
        // 5 px short of the centre: a card already in Reorder stays there until 10 px short.
        val justBefore = Offset(target.center.x + 5f, target.center.y)
        assertThat(decide(justBefore, target, gap, previous = TabOverviewDropZone.Group))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(justBefore, target, gap, previous = TabOverviewDropZone.Reorder))
            .isEqualTo(TabOverviewDropZone.Reorder)
        assertThat(decide(justBefore, target, gap)).isEqualTo(TabOverviewDropZone.Group)
        val wellBefore = Offset(target.center.x + 15f, target.center.y)
        assertThat(decide(wellBefore, target, gap, previous = TabOverviewDropZone.Reorder))
            .isEqualTo(TabOverviewDropZone.Group)
        // Clearly past the hysteresis band: every history agrees.
        val wellPast = Offset(target.center.x - 15f, target.center.y)
        assertThat(decide(wellPast, target, gap, previous = TabOverviewDropZone.Group))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }

    @Test
    fun firstContactAtTheExactCentre_isStillNear() {
        val target = slot(2)
        assertThat(decide(target.center, target, slot(1))).isEqualTo(TabOverviewDropZone.Group)
    }

    @Test
    fun ineligibleTarget_reordersAsSoonAsTheGhostCentreIsOver_it() {
        val target = slot(0)
        val gap = slot(1)
        assertThat(decide(Offset(target.right - 5f, target.center.y), target, gap, eligible = false))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }

    @Test
    fun ghostCentreOutsideTarget_isNone() {
        val target = slot(0)
        val gap = slot(1)
        // The ghost body overlaps the target but its centre is in the gap between cards.
        assertThat(decide(Offset(target.right + 5f, target.center.y), target, gap))
            .isEqualTo(TabOverviewDropZone.None)
    }

    @Test
    fun zoneDependsOnlyOnTheGhost_notOnWhereTheFingerHoldsIt() {
        // Finger grabbed at the card's top-left corner: the ghost centre is half a card away from
        // it. The decision takes the ghost rect alone, so the grab offset cannot skip the band.
        val target = slot(0)
        val gap = slot(1)
        val grabOffset = Offset(2f, 2f)
        val ghost = ghostAt(Offset(target.center.x + 30f, target.center.y))
        val finger = ghost.topLeft + grabOffset
        // The finger sits at the very top of the target: far outside the old central 70% band.
        assertThat(finger.y - target.top).isLessThan(5f)
        assertThat(decideTabOverviewDropZone(ghost, target, gap.center, true, TabOverviewDropZone.None))
            .isEqualTo(TabOverviewDropZone.Group)
    }

    @Test
    fun afterReorder_nextCardInTheSameDirectionOffersGroupingOnItsNearHalf() {
        // [A B C], A dragged right over B: B moved to slot 0, the gap now sits in slot 1.
        val gap = slot(1)
        val next = slot(2)
        assertThat(decide(Offset(next.left + 5f, next.center.y), next, gap))
            .isEqualTo(TabOverviewDropZone.Group)
        assertThat(decide(Offset(next.center.x + 20f, next.center.y), next, gap))
            .isEqualTo(TabOverviewDropZone.Reorder)
    }
}
