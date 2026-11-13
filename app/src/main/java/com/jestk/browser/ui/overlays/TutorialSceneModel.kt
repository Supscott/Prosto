package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color

/**
 * Pure frame model for the animated tutorial scenes. Every scene is a function of the story
 * progress (0..1), so holding a story pauses it and going back replays it from the start.
 * All lengths are in dp and mirror the real dock (TabStripRow, ActiveTabPill, FolderDockIndicator).
 */
internal object MockDockMetrics {
    const val TAB = 38f
    const val SPACING = 3f
    const val PAD_START = 5f
    const val PAD_END = 46f
    const val DOCK_HEIGHT = 50f
    const val DOCK_ROW_HEIGHT = 54f
    const val SIDE_BUTTON = 44f
    const val ROW_SPACING = 4f
    const val PILL_HEIGHT = 42f
    const val PILL_OVERHEAD = 68f
    const val PILL_MIN = 80f
    const val PILL_MAX_FRACTION = 0.45f
    const val ADD_BUTTON_END = 4f
    const val PAGE_HEIGHT = 140f
    const val PAGE_TO_DOCK = 16f

    /** Width of the dock capsule inside a scene of [sceneWidth] (minus the search and menu buttons). */
    fun capsuleWidth(sceneWidth: Float): Float = sceneWidth - 2 * (SIDE_BUTTON + ROW_SPACING)

    /** Vertical centre of the dock row inside a scene. */
    val dockCenterY: Float get() = PAGE_HEIGHT + PAGE_TO_DOCK + DOCK_ROW_HEIGHT / 2

    /** Same clamp as TabStripRow: overhead + text, at least the minimum, at most 45% of the capsule. */
    fun pillWidth(textWidth: Float, capsuleWidth: Float): Float {
        val max = capsuleWidth * PILL_MAX_FRACTION
        return (PILL_OVERHEAD + textWidth).coerceAtLeast(minOf(PILL_MIN, max)).coerceAtMost(max)
    }
}

/** A site shown in a scene; [letter] stands in for its favicon. */
internal data class MockSite(
    val letter: String,
    val title: String,
    val domain: String,
    val color: Color,
    val isHome: Boolean = false,
)

internal sealed interface StripEntry {
    /** 0 = gone (no room taken), 1 = fully present. */
    val appear: Float

    /**
     * [expansion] 0 = inactive 38dp circle, 1 = active pill of [pillWidth]. A [lifted] tab is being
     * dragged: its slot stays empty in the strip and the tab is drawn as [DockSceneFrame.dragged].
     */
    data class TabEntry(
        val site: MockSite,
        val pillWidth: Float,
        val expansion: Float,
        override val appear: Float = 1f,
        val folderColor: Color? = null,
        val isLastInFolder: Boolean = false,
        val lifted: Boolean = false,
    ) : StripEntry

    /**
     * [expanded] 0 = collapsed (arrow right), 1 = expanded (arrow left). A blank [title] is a circle.
     * [blink] is the chip alpha while a dragged tab hovers over it, as in FolderDockIndicator.
     */
    data class FolderEntry(
        val title: String,
        val color: Color,
        val chipWidth: Float,
        val expanded: Float,
        val blink: Float = 1f,
    ) : StripEntry {
        override val appear: Float get() = 1f
    }
}

internal val StripEntry.width: Float
    get() = when (this) {
        is StripEntry.TabEntry -> lerp(MockDockMetrics.TAB, pillWidth, expansion)
        is StripEntry.FolderEntry -> chipWidth
    }

/** Room an entry takes in the strip, including the gap after it. */
internal val StripEntry.slot: Float get() = (width + MockDockMetrics.SPACING) * appear

internal fun List<StripEntry>.contentWidth(): Float =
    MockDockMetrics.PAD_START + sumOf { it.slot.toDouble() }.toFloat() - MockDockMetrics.SPACING + MockDockMetrics.PAD_END

/** Start x of entry [index] in strip content coordinates (before scrolling). */
internal fun List<StripEntry>.startOf(index: Int): Float =
    MockDockMetrics.PAD_START + take(index).sumOf { it.slot.toDouble() }.toFloat()

/** A finger on the screen: position in scene dp, [press] squeezes it, [ripple] 0..1 runs a ring. */
internal data class TouchFrame(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val press: Float = 0f,
    val ripple: Float = 0f,
)

/**
 * A tab lifted off the strip by a long press, centred at [x], [y] in scene dp. [remove] 0..1 is
 * how far it has been pulled up towards the remove threshold, as in TabStripDragOverlays.
 */
internal data class DraggedTab(
    val site: MockSite,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    val remove: Float = 0f,
)

internal data class DockSceneFrame(
    val entries: List<StripEntry>,
    val scroll: Float,
    val maxScroll: Float,
    val touch: TouchFrame?,
    val dragged: DraggedTab? = null,
)

/** Screen x of the centre of entry [index] once the strip is scrolled by [scroll]. */
internal fun List<StripEntry>.centerOf(index: Int, scroll: Float): Float = startOf(index) + this[index].width / 2 - scroll

internal fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/** Linear 0..1 progress of [p] through the [start]..[end] window. */
internal fun phase(p: Float, start: Float, end: Float): Float = ((p - start) / (end - start)).coerceIn(0f, 1f)

internal fun eased(p: Float, start: Float, end: Float): Float = FastOutSlowInEasing.transform(phase(p, start, end))

/** 1 while the finger is down between [start] and [end], with short ramps at both ends. */
internal fun pressed(p: Float, start: Float, end: Float): Float =
    minOf(phase(p, start, start + 0.02f), 1f - phase(p, end - 0.02f, end))

/** Visible between [start] and [end], fading in and out over 0.04 of the story. */
internal fun visible(p: Float, start: Float, end: Float): Float =
    phase(p, start, start + 0.04f) * (1f - phase(p, end - 0.04f, end))

/** 0..1 triangle wave with [halfPeriod] per ramp, starting at 0 when [p] reaches [start]. */
internal fun pulse(p: Float, start: Float, halfPeriod: Float): Float {
    if (p < start) return 0f
    val t = ((p - start) / halfPeriod) % 2f
    return if (t < 1f) t else 2f - t
}

/** The most visible of several fingers, or null when none is on screen. */
internal fun List<TouchFrame>.current(): TouchFrame? = filter { it.alpha > 0f }.maxByOrNull { it.alpha }
