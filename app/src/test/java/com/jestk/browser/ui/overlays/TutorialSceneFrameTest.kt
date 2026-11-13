package com.jestk.browser.ui.overlays

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Timeline invariants of the dock and folder tutorial scenes. */
class TutorialSceneFrameTest {

    private val capsule = MockDockMetrics.capsuleWidth(379f)
    private val pills = listOf(130f, 110f, 108f, 96f, 104f, 110f)
    private val home = MockSite("", "Home", "", Color.Gray, isHome = true)

    private fun dock(p: Float) = dockSceneFrame(p, capsule, pills, home, homePillWidth = 90f)

    private fun activeIndices(frame: DockSceneFrame) =
        frame.entries.withIndex()
            .filter { (it.value as StripEntry.TabEntry).let { tab -> tab.expansion * tab.appear > 0.99f } }
            .map { it.index }

    @Test
    fun dockStartsOnGitHubUnscrolledWithTabsBehindTheEdge() {
        val frame = dock(0f)
        assertThat(activeIndices(frame)).containsExactly(2)
        assertThat(frame.scroll).isEqualTo(0f)
        assertThat(frame.maxScroll).isGreaterThan(0f)
        assertThat(frame.touch).isNull()
    }

    @Test
    fun tapMovesTheActivePillToYouTube() {
        assertThat(activeIndices(dock(0.40f))).containsExactly(1)
    }

    @Test
    fun newTabEndsActiveAndFullyVisibleBesideThePlusButton() {
        val frame = dock(1f)
        val last = frame.entries.lastIndex
        assertThat(activeIndices(frame)).containsExactly(last)
        val right = frame.entries.startOf(last) + frame.entries[last].width - frame.scroll
        assertThat(right).isAtMost(capsule - MockDockMetrics.PAD_END + 0.5f)
        assertThat(frame.scroll).isWithin(0.01f).of(frame.maxScroll)
    }

    @Test
    fun theFingerAlwaysSitsInsideTheCapsule() {
        (0..100).map { it / 100f }.mapNotNull { dock(it).touch }.forEach { touch ->
            assertThat(touch.x).isIn(com.google.common.collect.Range.closed(0f, capsule))
        }
    }

    @Test
    fun pulledUpTabIsClosedAndTheGapCloses() {
        val pulled = dock(0.82f)
        assertThat(pulled.dragged?.remove).isEqualTo(1f)
        assertThat((pulled.entries[DOCK_SCENE_REMOVED_INDEX] as StripEntry.TabEntry).lifted).isTrue()
        val done = dock(1f)
        assertThat(done.dragged).isNull()
        assertThat(done.entries[DOCK_SCENE_REMOVED_INDEX].slot).isEqualTo(0f)
    }

    @Test
    fun draggedTabDropsIntoTheFolderAndShowsUpWhenItExpands() {
        fun folder(p: Float) = folderSceneFrame(p, capsule, "Work", workChipWidth = 70f, activePillWidth = 110f)
        fun members(frame: DockSceneFrame) = frame.entries.filterIsInstance<StripEntry.TabEntry>().filter { it.folderColor != null }
        val chip = { frame: DockSceneFrame -> frame.entries.first() as StripEntry.FolderEntry }

        assertThat(members(folder(0f)).map { it.appear }).containsExactly(0f, 0f)
        assertThat(chip(folder(0f)).expanded).isEqualTo(0f)

        val hovering = folder(0.30f)
        assertThat(hovering.dragged).isNotNull()
        assertThat(hovering.dragged!!.x).isWithin(0.5f).of(MockDockMetrics.PAD_START + 35f)
        assertThat((0..13).map { chip(folder(0.26f + it * 0.01f)).blink }.min()).isLessThan(0.6f)

        val expanded = folder(1f)
        assertThat(expanded.dragged).isNull()
        assertThat(chip(expanded).expanded).isEqualTo(1f)
        assertThat(members(expanded).map { it.site.title }).containsExactly("Jira", "Slack", "Wikipedia").inOrder()
        assertThat(members(expanded).map { it.appear }).containsExactly(1f, 1f, 1f)
        assertThat(members(expanded).last().isLastInFolder).isTrue()
    }

    @Test
    fun workspaceSceneShrinksIntoCardsAndOpensWork() {
        val width = 379f
        val rest = workspaceSceneFrame(0f, width)
        assertThat(rest.overview).isEqualTo(0f)
        assertThat(workspaceCardPlacement(0, rest, width).scale).isEqualTo(1f)
        // At rest the other workspace is entirely off screen.
        val restWork = workspaceCardPlacement(1, rest, width)
        assertThat(restWork.centerX - width * restWork.scale / 2).isAtLeast(width)

        val overview = workspaceSceneFrame(0.34f, width)
        assertThat(overview.overview).isEqualTo(1f)
        val peek = workspaceCardPlacement(1, overview, width)
        assertThat(peek.centerX - width * peek.scale / 2).isLessThan(width)

        val done = workspaceSceneFrame(1f, width)
        assertThat(done.overview).isEqualTo(0f)
        assertThat(done.activeIndex).isEqualTo(1)
        assertThat(workspaceCardPlacement(1, done, width).centerX).isWithin(0.01f).of(width / 2)
        assertThat(done.touch).isNull()
    }

    @Test
    fun searchFlingsToDuckDuckGoThenCompletesAndOffersTheOpenTab() {
        val width = 379f
        val rest = searchSceneFrame(0f, width)
        assertThat(rest.enginePos).isEqualTo(0f)
        assertThat(rest.typed).isEqualTo(0)
        val flung = searchSceneFrame(0.36f, width)
        assertThat(flung.enginePos).isEqualTo(1f)
        assertThat(flung.carouselOpen).isEqualTo(0f)
        val typing = searchSceneFrame(0.425f, width)
        val (keyX, keyY) = SearchSceneMetrics.keyCenter('y', width)
        assertThat(typing.touch!!.x).isWithin(0.01f).of(keyX)
        assertThat(typing.touch!!.y).isWithin(0.01f).of(keyY)
        val typed = searchSceneFrame(0.56f, width)
        assertThat(typed.typed).isEqualTo(SEARCH_SCENE_TYPED.length)
        assertThat(typed.suggestion).isEqualTo(1f)
        assertThat(SEARCH_SCENE_MATCH).startsWith(SEARCH_SCENE_TYPED)
    }

    @Test
    fun protectionCountsEveryBlockedAdAndTracker() {
        val width = 379f
        val start = protectionSceneFrame(0f, width)
        assertThat(start.adsBlocked).isEqualTo(ProtectionSceneMetrics.ADS_BEFORE)
        assertThat(start.trackersStopped).isEqualTo(ProtectionSceneMetrics.TRACKERS_BEFORE)
        assertThat(start.trackers.map { it.alpha }).containsExactly(1f, 1f, 1f, 1f)
        val end = protectionSceneFrame(1f, width)
        assertThat(end.adsBlocked).isEqualTo(ProtectionSceneMetrics.ADS_BEFORE + AdCollapses.size)
        assertThat(end.trackersStopped).isEqualTo(ProtectionSceneMetrics.TRACKERS_BEFORE + TrackerCatches.size)
        assertThat(end.trackers.map { it.alpha }).containsExactly(0f, 0f, 0f, 0f)
        assertThat(end.adCollapse).containsExactly(1f, 1f)
    }

    @Test
    fun defaultBrowserSelectsProstoThenOpensTheChatLinkInIt() {
        val width = 379f
        assertThat(defaultSceneFrame(0f, width).selected).isEqualTo(0f)
        val chosen = defaultSceneFrame(0.30f, width)
        assertThat(chosen.selected).isEqualTo(1f)
        assertThat(chosen.toChat).isEqualTo(0f)
        val end = defaultSceneFrame(1f, width)
        assertThat(end.toChat).isEqualTo(1f)
        assertThat(end.opened).isEqualTo(1f)
        assertThat(end.touch).isNull()
    }
}
