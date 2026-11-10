package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** Corner radius of a workspace card preview at rest, in dp. */
internal const val WORKSPACE_CARD_CORNER_RADIUS_DP = 24f

/** Share of the root height a dock swipe must travel to shrink the app fully into the carousel. */
private const val FULL_DRAG_HEIGHT_FRACTION = 0.32f
private const val SETTLE_OPEN_PROGRESS = 0.25f
private const val SETTLE_FLING_VELOCITY_DP_PER_S = 300f
private const val FALLBACK_CARD_SCALE = 0.62f

/**
 * Shared, frame-exact state of the Android Recents-like workspace switcher transition.
 *
 * [progress] is the single driver: `0` is the live app at full screen, `1` is the carousel at
 * rest. The live app content (WebView / Home) is itself the active workspace card: it is
 * transformed into [activeCardRect] (see [workspaceSwitcherLiveContent]), so opening and closing
 * never swap a live page for a stale snapshot. The carousel applies the same transform to its
 * neighbours, so they travel with the live card.
 *
 * Selecting a different workspace expands that card's snapshot to full screen instead, while
 * everything else, including the live card (see [liveAlphaOverride]), fades out.
 */
@Stable
class WorkspaceSwitcherState(
    /** Release velocity that settles a drag in its direction regardless of distance. */
    private val flingVelocityPxPerSec: Float,
) {
    val progress = Animatable(0f)

    /** Size of the app root; the live content fills it at progress 0. */
    var rootSize by mutableStateOf(IntSize.Zero)

    /** Rest rect of the active card preview in root coordinates, reported by the carousel. */
    var restCardRect by mutableStateOf<Rect?>(null)

    /** Horizontal shift of the active card caused by carousel scrolling; read in the draw phase. */
    var activeCardScrollX by mutableStateOf<() -> Float>({ 0f })

    /** True while a finger on the dock drives [progress]. */
    var isDragging by mutableStateOf(false)

    /**
     * Set while a non-active workspace card expands: the live card fades with the other cards.
     * Read in the draw phase; null means fully opaque.
     */
    var liveAlphaOverride by mutableStateOf<(() -> Float)?>(null)

    private var dragStartProgress = 0f

    val rootRect: Rect
        get() = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())

    /** Current rect of the active card, including carousel scroll. */
    val activeCardRect: Rect
        get() = (restCardRect ?: fallbackCardRect()).translate(activeCardScrollX(), 0f)

    /** Where the live content is drawn at the current [progress]. */
    val liveRect: Rect
        get() = lerp(rootRect, activeCardRect, progress.value.coerceIn(0f, 1f))

    /** Opacity of the live card: it fades with the other cards while another one expands. */
    val liveAlpha: Float
        get() = liveAlphaOverride?.invoke() ?: 1f

    /** Whether the live content must be transformed at all (identity otherwise, no clip). */
    val isTransforming: Boolean
        get() = progress.value > 0f || liveAlphaOverride != null

    private fun fallbackCardRect(): Rect {
        val root = rootRect
        val w = root.width * FALLBACK_CARD_SCALE
        val h = root.height * FALLBACK_CARD_SCALE
        return Rect(root.center.x - w / 2f, root.center.y - h / 2f, root.center.x + w / 2f, root.center.y + h / 2f)
    }

    fun fullDragDistancePx(): Float =
        (rootSize.height * FULL_DRAG_HEIGHT_FRACTION).coerceAtLeast(1f)

    /** Starts a dock drag from the current progress; the next [dragTo] interrupts any animation. */
    fun startDrag() {
        isDragging = true
        dragStartProgress = progress.value
    }

    /** [upwardDragPx] is the total upward finger travel since the drag started. */
    suspend fun dragTo(upwardDragPx: Float) {
        progress.snapTo((dragStartProgress + upwardDragPx / fullDragDistancePx()).coerceIn(0f, 1f))
    }

    /**
     * Settles a released dock drag; [upwardVelocityPxPerSec] is positive for an upward fling.
     * Returns true if the carousel opened, false if the app sprang back to full screen.
     */
    suspend fun endDrag(upwardVelocityPxPerSec: Float): Boolean {
        val open = when {
            upwardVelocityPxPerSec > flingVelocityPxPerSec -> true
            upwardVelocityPxPerSec < -flingVelocityPxPerSec -> false
            else -> progress.value >= SETTLE_OPEN_PROGRESS
        }
        // Cleared before animating: a new drag may interrupt this settle and set it again.
        isDragging = false
        if (open) animateOpen() else animateClose()
        return open
    }

    suspend fun animateOpen() {
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    suspend fun animateClose() {
        progress.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }

    /** Resets every transform so the live app is plain full screen again. */
    suspend fun reset() {
        liveAlphaOverride = null
        isDragging = false
        progress.snapTo(0f)
    }
}

@Composable
fun rememberWorkspaceSwitcherState(): WorkspaceSwitcherState {
    val density = LocalDensity.current
    return remember(density) {
        WorkspaceSwitcherState(flingVelocityPxPerSec = with(density) { SETTLE_FLING_VELOCITY_DP_PER_S.dp.toPx() })
    }
}

/**
 * Draws the live app content (WebView / Home) as the active workspace card: scaled into
 * [WorkspaceSwitcherState.liveRect] with rounded corners. Identity when the switcher is idle.
 */
fun Modifier.workspaceSwitcherLiveContent(
    state: WorkspaceSwitcherState,
    cornerRadiusPx: Float,
    shadowElevationPx: Float,
): Modifier = graphicsLayer {
    if (!state.isTransforming || state.rootSize.width == 0) return@graphicsLayer
    val p = state.progress.value.coerceIn(0f, 1f)
    val rect = state.liveRect
    val scale = rect.width / state.rootSize.width
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = rect.left
    translationY = rect.top
    alpha = state.liveAlpha
    // The shape is laid out before scaling, so the radius is divided by the scale.
    val radius = lerp(0f, cornerRadiusPx, p) / scale.coerceAtLeast(0.01f)
    shape = RoundedCornerShape(radius)
    clip = true
    shadowElevation = shadowElevationPx * p
}
