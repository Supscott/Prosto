package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

/** What the dock pill has to do when the active tab (or its target width) changes. */
internal enum class TabSwitchStep {
    /** The pill already belongs to the active tab: only retarget its width. */
    Settle,

    /** Collapse the previous pill to a circle first, then open the new one with a bump. */
    CollapseThenAdopt,

    /** The previous pill cannot collapse (gone, or the switch is structural): open the new pill at once. */
    Adopt,
}

/**
 * Pure decision of the sequential switch.
 *
 * The collapse phase only makes sense when both the pill's tab and the new active tab are drawn in the
 * strip and the new tab is not a fresh one (a new tab inserts itself, so only its bump-expand is shown).
 */
internal fun resolveTabSwitchStep(
    pillTabId: String?,
    activeTabId: String?,
    stripTabIds: Set<String>,
    activeTabIsNew: Boolean,
): TabSwitchStep =
    when {
        pillTabId == activeTabId -> TabSwitchStep.Settle
        pillTabId == null || activeTabId == null -> TabSwitchStep.Adopt
        activeTabIsNew -> TabSwitchStep.Adopt
        pillTabId !in stripTabIds || activeTabId !in stripTabIds -> TabSwitchStep.Adopt
        else -> TabSwitchStep.CollapseThenAdopt
    }

internal object TabSwitchAnimationSpecs {
    val CollapsedWidth = 38.dp
    const val CollapseMillis = 120

    /** Damping 0.6 overshoots the open width by about 9% of the travel, then settles in ~250 ms. */
    const val ExpandDamping = 0.6f
    const val ExpandStiffness = 800f
    const val WidthVisibilityThresholdDp = 0.1f
}

/**
 * Drives the dock pill through a tab switch: the previous pill collapses to a circle (~120 ms), then the
 * new active tab draws as the pill and opens with a small overshoot. The page switch itself is not delayed,
 * only the dock drawing lags. A new switch retargets from the current width at once (no animation queue).
 */
@Stable
internal class TabSwitchAnimationState(initialPillTabId: String?, initialWidth: Dp) {
    /** The tab currently drawn as the active pill; may trail the real active tab during the collapse. */
    var pillTabId by mutableStateOf(initialPillTabId)
        private set

    /** True while the pill shows no content (collapse and the first frame of the open phase). */
    var isCompact by mutableStateOf(false)
        private set

    /** True while neighbours must follow the pill width directly instead of through a placement spring. */
    var isSequencing by mutableStateOf(false)
        private set

    private val width = Animatable(initialWidth.value)

    /** Read in the layout phase only, so the per-frame width does not recompose the dock items. */
    val pillWidth: Dp get() = width.value.dp

    suspend fun switchTo(step: TabSwitchStep, activeTabId: String?, activeWidth: Dp) {
        when (step) {
            TabSwitchStep.Settle -> {
                isCompact = false
                isSequencing = abs(width.value - activeWidth.value) > 0.5f
                width.animateTo(activeWidth.value, expandSpec())
                isSequencing = false
            }
            TabSwitchStep.CollapseThenAdopt -> {
                isSequencing = true
                isCompact = true
                val collapsed = TabSwitchAnimationSpecs.CollapsedWidth.value
                if (width.value > collapsed + 0.5f) {
                    width.animateTo(
                        collapsed,
                        tween(TabSwitchAnimationSpecs.CollapseMillis, easing = FastOutSlowInEasing),
                    )
                }
                adopt(activeTabId, activeWidth, sequencing = true)
            }
            TabSwitchStep.Adopt -> adopt(activeTabId, activeWidth, sequencing = false)
        }
    }

    /** Drag and settle keep the old behaviour: the pill belongs to the active tab and mirrors [widths]. */
    suspend fun follow(activeTabId: String?, widths: Flow<Dp>) {
        pillTabId = activeTabId
        isCompact = false
        isSequencing = false
        widths.collect { width.snapTo(it.value) }
    }

    private suspend fun adopt(activeTabId: String?, activeWidth: Dp, sequencing: Boolean) {
        pillTabId = activeTabId
        isSequencing = sequencing
        isCompact = true
        width.snapTo(TabSwitchAnimationSpecs.CollapsedWidth.value)
        // One compact frame first, so the content fades in while the pill opens.
        withFrameNanos { }
        isCompact = false
        width.animateTo(activeWidth.value, expandSpec())
        isSequencing = false
    }

    private fun expandSpec() =
        spring(
            dampingRatio = TabSwitchAnimationSpecs.ExpandDamping,
            stiffness = TabSwitchAnimationSpecs.ExpandStiffness,
            visibilityThreshold = TabSwitchAnimationSpecs.WidthVisibilityThresholdDp,
        )
}

/**
 * @param bypass true while a drag or settle is running; the pill then mirrors [draggedWidth] (today's behaviour).
 */
@Composable
internal fun rememberTabSwitchAnimation(
    activeTabId: String?,
    dockItems: List<DockItem>,
    activeWidth: Dp,
    bypass: Boolean,
    draggedWidth: Dp,
): TabSwitchAnimationState {
    val stripTabIds = remember(dockItems) { dockItems.mapNotNullTo(HashSet()) { (it as? DockItem.TabItem)?.tab?.id } }
    val state = remember { TabSwitchAnimationState(activeTabId, activeWidth) }

    // Ids drawn by the previous composition: an active id missing from them is a freshly created tab.
    val knownIds = remember { arrayOf(stripTabIds) }
    val activeTabIsNew = activeTabId != null && activeTabId !in knownIds[0]
    SideEffect { knownIds[0] = stripTabIds }

    val currentDraggedWidth = rememberUpdatedState(draggedWidth)
    LaunchedEffect(activeTabId, activeWidth, bypass) {
        if (bypass) {
            state.follow(activeTabId, snapshotFlow { currentDraggedWidth.value })
        } else {
            val step = resolveTabSwitchStep(state.pillTabId, activeTabId, stripTabIds, activeTabIsNew)
            state.switchTo(step, activeTabId, activeWidth)
        }
    }
    return state
}

/** Forces the item width from [widthProvider] in the layout phase (no recomposition per animation frame). */
internal fun Modifier.layoutWidth(widthProvider: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val target = widthProvider().roundToPx()
        val w = target.coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
