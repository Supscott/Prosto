package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calculations for scale, alpha, and indicator visibility of items in the search engine carousel.
 * Provides smooth continuous interpolation during drag and fling gestures, smoothly settling
 * to resting state when inactive.
 */
internal object SearchEngineCarouselInterpolation {
    const val ACTIVE_SCALE = 1.15f
    const val INACTIVE_SCALE = 1.0f
    const val ACTIVE_ALPHA = 1.0f
    const val INACTIVE_ALPHA = 0.45f

    /**
     * Calculates scale for an item at [slotIndex] given the continuous [centerSlotFloat].
     * Interpolates smoothly from [ACTIVE_SCALE] (dist = 0) down to [INACTIVE_SCALE] (dist >= 1).
     */
    fun calculateScale(
        slotIndex: Int,
        centerSlotFloat: Float,
        scrollProgress: Float = 1f,
    ): Float {
        val dist = abs(slotIndex - centerSlotFloat)
        val focusFraction = (1f - dist).coerceIn(0f, 1f)
        return INACTIVE_SCALE + (ACTIVE_SCALE - INACTIVE_SCALE) * focusFraction * scrollProgress
    }

    /**
     * Calculates alpha for an item at [slotIndex] given the continuous [centerSlotFloat].
     * During scroll, interpolates smoothly from [ACTIVE_ALPHA] (dist = 0) down to [INACTIVE_ALPHA] (dist >= 1).
     * When resting (scrollProgress = 0), returns 1f for slot 0, and 0f for other slots.
     */
    fun calculateAlpha(
        slotIndex: Int,
        centerSlotFloat: Float,
        scrollProgress: Float = 1f,
    ): Float {
        val dist = abs(slotIndex - centerSlotFloat)
        val focusFraction = (1f - dist).coerceIn(0f, 1f)
        val scrollAlpha = INACTIVE_ALPHA + (ACTIVE_ALPHA - INACTIVE_ALPHA) * focusFraction
        return if (slotIndex == 0) {
            1f - (1f - scrollAlpha) * scrollProgress
        } else {
            scrollAlpha * scrollProgress
        }
    }

    /**
     * Calculates the alpha for the active indicator (ring and dot) for an item at [slotIndex].
     * The indicator is only visible for the item within 0.5 slots of center (dist < 0.5f),
     * and fades out to 0f at dist >= 0.5f.
     */
    fun calculateIndicatorAlpha(
        slotIndex: Int,
        centerSlotFloat: Float,
        scrollProgress: Float = 1f,
    ): Float {
        val dist = abs(slotIndex - centerSlotFloat)
        if (dist >= 0.5f) return 0f
        val focus = (1f - dist * 2f).coerceIn(0f, 1f)
        return focus * scrollProgress
    }
}

/**
 * State and gesture controller for the search engine logo carousel in SearchOverlay.
 * Provides synchronized dragging, spline-based fling decay with inertia,
 * spring snapping to the nearest engine slot, and vibration feedback on slot crossing.
 */
internal class SearchEngineCarouselState(
    initialEngine: SearchEngine,
    val density: Density,
    val coroutineScope: CoroutineScope,
    val vibration: VibrationFeedback,
    var engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    val onEngineSelected: (SearchEngine) -> Unit,
) {
    var currentEngine by mutableStateOf(initialEngine)
    val animOffset = Animatable(0f, visibilityThreshold = 0.5f)
    var isDragging by mutableStateOf(false)
    var isFlinging by mutableStateOf(false)
    val isScrolling: Boolean get() = isDragging || isFlinging || abs(animOffset.value) > 0.5f

    val itemSpacingDp: Dp = 40.dp
    val itemSpacingPx: Float = with(density) { itemSpacingDp.toPx() }

    private var lastVibrationSlot: Int = 0
    private var activeJob: Job? = null

    private fun vibrateOnSlotChange(offset: Float) {
        val slot = (-offset / itemSpacingPx).roundToInt()
        if (slot != lastVibrationSlot) {
            lastVibrationSlot = slot
            vibration.tick()
        }
    }

    fun onDragStart() {
        activeJob?.cancel()
        isDragging = true
        isFlinging = false
        lastVibrationSlot = (-animOffset.value / itemSpacingPx).roundToInt()
    }

    fun onDrag(dragAmount: Float) {
        activeJob?.cancel()
        coroutineScope.launch {
            val newOffset = animOffset.value + dragAmount
            animOffset.snapTo(newOffset)
            vibrateOnSlotChange(newOffset)
        }
    }

    fun onDragEnd(velocity: Float) {
        isDragging = false
        val minFlingVelocity = 400f // px/sec
        activeJob =
            coroutineScope.launch {
                isFlinging = true
                val currentOffset = animOffset.value
                val targetSlot: Int

                if (abs(velocity) >= minFlingVelocity) {
                    val decay = splineBasedDecay<Float>(density)
                    val predictedDelta = decay.calculateTargetValue(0f, velocity)
                    val maxSlots = 6
                    val maxDistance = maxSlots * itemSpacingPx
                    val clampedDelta = predictedDelta.coerceIn(-maxDistance, maxDistance)
                    val predictedTarget = currentOffset + clampedDelta

                    var slot = (-predictedTarget / itemSpacingPx).roundToInt()
                    val currentBaseSlot = (-currentOffset / itemSpacingPx).toInt()
                    if (velocity < -minFlingVelocity && slot <= currentBaseSlot) {
                        slot = currentBaseSlot + 1
                    } else if (velocity > minFlingVelocity && slot >= currentBaseSlot) {
                        slot = currentBaseSlot - 1
                    }
                    targetSlot = slot
                } else {
                    targetSlot = (-currentOffset / itemSpacingPx).roundToInt()
                }

                val targetX = -targetSlot * itemSpacingPx

                try {
                    if (abs(velocity) >= minFlingVelocity) {
                        animOffset.animateTo(
                            targetValue = targetX,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            initialVelocity = velocity,
                        ) {
                            vibrateOnSlotChange(value)
                        }
                    } else {
                        animOffset.animateTo(
                            targetValue = targetX,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        ) {
                            vibrateOnSlotChange(value)
                        }
                    }

                    // Settle and commit
                    val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                    val currentIndex = list.indexOfFirst { it.id == currentEngine.id }.let { if (it == -1) 0 else it }
                    val finalIndex = (currentIndex + targetSlot).mod(list.size)
                    val newEngine = list[finalIndex]

                    currentEngine = newEngine
                    animOffset.snapTo(0f)
                    lastVibrationSlot = 0
                    isFlinging = false
                    isDragging = false
                    onEngineSelected(newEngine)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        isFlinging = false
                    } else {
                        throw e
                    }
                }
            }
    }

    fun setEngineDirectly(engine: SearchEngine) {
        activeJob?.cancel()
        currentEngine = engine
        coroutineScope.launch {
            animOffset.snapTo(0f)
            lastVibrationSlot = 0
            isDragging = false
            isFlinging = false
            onEngineSelected(engine)
        }
    }

    fun animateToEngine(targetEngine: SearchEngine) {
        val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
        val currentIndex = list.indexOfFirst { it.id == currentEngine.id }.let { if (it == -1) 0 else it }
        val targetIndex = list.indexOfFirst { it.id == targetEngine.id }
        if (targetIndex == -1) {
            setEngineDirectly(targetEngine)
            return
        }

        val size = list.size
        if (size <= 1) {
            setEngineDirectly(targetEngine)
            return
        }

        val rawDiff = (targetIndex - currentIndex).mod(size)
        val targetSlot = if (rawDiff <= size / 2) rawDiff else rawDiff - size

        if (targetSlot == 0) {
            if (animOffset.value != 0f) {
                activeJob?.cancel()
                activeJob =
                    coroutineScope.launch {
                        isFlinging = true
                        try {
                            animOffset.animateTo(
                                targetValue = 0f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                            )
                            animOffset.snapTo(0f)
                            lastVibrationSlot = 0
                            isFlinging = false
                            isDragging = false
                        } catch (_: kotlinx.coroutines.CancellationException) {
                            isFlinging = false
                        }
                    }
            }
            onEngineSelected(targetEngine)
            return
        }

        activeJob?.cancel()
        // Report the selection once, up front: a cancelled animation must not lose it.
        onEngineSelected(targetEngine)
        isFlinging = true
        val targetX = -targetSlot * itemSpacingPx
        lastVibrationSlot = (-animOffset.value / itemSpacingPx).roundToInt()

        activeJob =
            coroutineScope.launch {
                try {
                    animOffset.animateTo(
                        targetValue = targetX,
                        animationSpec =
                            spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMedium,
                            ),
                    ) {
                        vibrateOnSlotChange(value)
                    }

                    currentEngine = targetEngine
                    animOffset.snapTo(0f)
                    lastVibrationSlot = 0
                    isFlinging = false
                    isDragging = false
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        isFlinging = false
                    } else {
                        throw e
                    }
                }
            }
    }

    /**
     * Smoothly scrolls and snaps the carousel to the first search engine in the list.
     */
    fun scrollToFirst() {
        val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
        val firstEngine = list.firstOrNull() ?: return
        animateToEngine(firstEngine)
    }
}

/**
 * Creates and remembers a [SearchEngineCarouselState].
 */
@Composable
internal fun rememberSearchEngineCarouselState(
    initialEngine: SearchEngine,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    density: Density = LocalDensity.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    vibration: VibrationFeedback = LocalVibrationFeedback.current,
    onEngineSelected: (SearchEngine) -> Unit,
): SearchEngineCarouselState {
    val currentOnEngineSelected by rememberUpdatedState(onEngineSelected)
    val state =
        remember(density) {
            SearchEngineCarouselState(
                initialEngine = initialEngine,
                density = density,
                coroutineScope = coroutineScope,
                vibration = vibration,
                engines = engines,
                onEngineSelected = { currentOnEngineSelected(it) },
            )
        }
    state.engines = engines
    return state
}
