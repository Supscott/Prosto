package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalProstoTypography
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoBrandPrimary
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.theme.ProstoPaleBlue
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
                    onEngineSelected(targetEngine)
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

@Composable
internal fun SearchEngineSelectorButton(
    carouselState: SearchEngineCarouselState,
    isMenuOpen: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.cardBorder(isDark)
    val density = carouselState.density
    val doubleTapAction = onDoubleTap ?: { carouselState.scrollToFirst() }

    val containerWidth by animateDpAsState(
        targetValue = if (carouselState.isScrolling) 160.dp else 68.dp,
        animationSpec =
            spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "containerWidth",
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "arrowRotation",
    )

    val arrowAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "arrowAlpha",
    )

    val fadeAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "fadeAlpha",
    )

    val restingShiftPx = with(density) { (-11).dp.toPx() } * arrowAlpha

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier =
            modifier
                .width(containerWidth)
                .height(52.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Search engine selector"
                    onClick {
                        onClick()
                        true
                    }
                }.searchEngineButtonGestures(
                    carouselState = carouselState,
                    onClick = onClick,
                    onDoubleTap = doubleTapAction,
                ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Badges Carousel with Edge Fade Mask
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.drawWithContent {
                            drawContent()
                            if (fadeAlpha > 0.01f) {
                                val fadeWidth = 24.dp.toPx() * fadeAlpha
                                val w = size.width
                                if (fadeWidth > 0f && w > 0f) {
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                (fadeWidth / w) to Color.Black,
                                                ((w - fadeWidth) / w) to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                val list = if (carouselState.engines.isNotEmpty()) carouselState.engines else SearchEngine.DEFAULT_ENGINES
                val baseIndex = list.indexOfFirst { it.id == carouselState.currentEngine.id }.let { if (it == -1) 0 else it }
                val centerSlotFloat = -carouselState.animOffset.value / carouselState.itemSpacingPx
                val centerSlotInt = centerSlotFloat.roundToInt()
                val scrollProgress = if (carouselState.isScrolling) 1f else fadeAlpha
                val accentColor = if (isDark) DarkPrimary else ProstoBrandPrimary

                for (k in (centerSlotInt - 3)..(centerSlotInt + 3)) {
                    key(k) {
                        val engineIndex = (baseIndex + k).mod(list.size)
                        val engine = list[engineIndex]
                        val xPos = k * carouselState.itemSpacingPx + carouselState.animOffset.value + restingShiftPx

                        val itemScale =
                            SearchEngineCarouselInterpolation.calculateScale(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )
                        val itemAlpha =
                            SearchEngineCarouselInterpolation.calculateAlpha(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )
                        val indicatorAlpha =
                            SearchEngineCarouselInterpolation.calculateIndicatorAlpha(
                                slotIndex = k,
                                centerSlotFloat = centerSlotFloat,
                                scrollProgress = scrollProgress,
                            )

                        Box(
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        translationX = xPos
                                        scaleX = itemScale
                                        scaleY = itemScale
                                        alpha = itemAlpha
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Prosto Canvas active accent ring around badge
                            if (indicatorAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .graphicsLayer { alpha = indicatorAlpha }
                                            .border(
                                                width = 1.5.dp,
                                                color = accentColor,
                                                shape = CircleShape,
                                            ),
                                )
                            }

                            SearchEngineBadge(
                                engine = engine,
                                size = 26.dp,
                                isDark = isDark,
                            )

                            // Prosto Canvas active accent indicator pill below badge
                            if (indicatorAlpha > 0.01f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .offset(y = 18.dp)
                                            .size(width = 8.dp, height = 3.dp)
                                            .graphicsLayer { alpha = indicatorAlpha }
                                            .background(
                                                color = accentColor,
                                                shape = CircleShape,
                                            ),
                                )
                            }
                        }
                    }
                }
            }

            // Dropdown Indicator Arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                            alpha = arrowAlpha
                        },
            )
        }
    }
}

@Composable
internal fun SearchEngineSelectorButton(
    selectedEngine: SearchEngine,
    isMenuOpen: Boolean,
    isDark: Boolean,
    density: Density,
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    onSelectEngine: ((SearchEngine) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val vibration = LocalVibrationFeedback.current
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnSelectEngine by rememberUpdatedState(onSelectEngine)

    val carouselState =
        remember(selectedEngine, density, engines) {
            SearchEngineCarouselState(
                initialEngine = selectedEngine,
                density = density,
                coroutineScope = coroutineScope,
                vibration = vibration,
                engines = engines,
                onEngineSelected = { target ->
                    currentOnSelectEngine?.invoke(target)
                    val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                    val oldIdx = list.indexOfFirst { it.id == selectedEngine.id }.let { if (it == -1) 0 else it }
                    val newIdx = list.indexOfFirst { it.id == target.id }.let { if (it == -1) 0 else it }
                    val diff = (newIdx - oldIdx).mod(list.size)
                    if (diff == 1) {
                        currentOnSwipeLeft()
                    } else if (diff == list.size - 1) {
                        currentOnSwipeRight()
                    }
                },
            )
        }

    SearchEngineSelectorButton(
        carouselState = carouselState,
        isMenuOpen = isMenuOpen,
        isDark = isDark,
        onClick = onClick,
        modifier = modifier,
        onDoubleTap = onDoubleTap,
    )
}

@Composable
internal fun Modifier.searchEngineButtonGestures(
    carouselState: SearchEngineCarouselState,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val viewConfig = LocalViewConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    return this.pointerInput(carouselState) {
        val velocityTracker = VelocityTracker()
        var pendingSingleTapJob: Job? = null
        var lastTapUpTime = 0L
        var lastTapUpPosition: Offset? = null
        val doubleTapTimeoutMillis = viewConfig.doubleTapTimeoutMillis
        val maxDoubleTapSlop = viewConfig.touchSlop * 2f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTime = down.uptimeMillis
            val timeSinceLastTapUp = downTime - lastTapUpTime
            val isWithinSlop = lastTapUpPosition?.let { prevPos ->
                (down.position - prevPos).getDistance() <= maxDoubleTapSlop
            } ?: false

            val isPotentialDoubleTap = currentOnDoubleTap != null &&
                lastTapUpTime > 0L &&
                timeSinceLastTapUp in 0L..doubleTapTimeoutMillis &&
                isWithinSlop

            if (isPotentialDoubleTap) {
                pendingSingleTapJob?.cancel()
                pendingSingleTapJob = null
            }

            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!isDrag) {
                        val isTap = (change.uptimeMillis - downTime) < viewConfig.longPressTimeoutMillis
                        if (isTap) {
                            if (currentOnDoubleTap != null) {
                                if (isPotentialDoubleTap) {
                                    lastTapUpTime = 0L
                                    lastTapUpPosition = null
                                    currentOnDoubleTap?.invoke()
                                } else {
                                    lastTapUpTime = change.uptimeMillis
                                    lastTapUpPosition = change.position
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = coroutineScope.launch {
                                        delay(doubleTapTimeoutMillis)
                                        lastTapUpTime = 0L
                                        lastTapUpPosition = null
                                        currentOnClick()
                                    }
                                }
                            } else {
                                currentOnClick()
                            }
                        } else {
                            lastTapUpTime = 0L
                            lastTapUpPosition = null
                        }
                    } else {
                        lastTapUpTime = 0L
                        lastTapUpPosition = null
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (!isDrag && abs(totalDragX) > viewConfig.touchSlop) {
                    isDrag = true
                    pendingSingleTapJob?.cancel()
                    pendingSingleTapJob = null
                    lastTapUpTime = 0L
                    lastTapUpPosition = null
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
internal fun Modifier.searchEngineInputGestures(
    carouselState: SearchEngineCarouselState,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val viewConfig = LocalViewConfiguration.current

    return this.pointerInput(enabled, carouselState) {
        val velocityTracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (isDrag) {
                        val velocity = velocityTracker.calculateVelocity().x
                        carouselState.onDragEnd(velocity)
                    }
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (!isDrag && abs(totalDragX) > viewConfig.touchSlop) {
                    isDrag = true
                    carouselState.onDragStart()
                }
                if (isDrag) {
                    change.consume()
                    carouselState.onDrag(dragX)
                }
            }
        }
    }
}

@Composable
internal fun Modifier.horizontalSwipeCycler(
    density: Density = LocalDensity.current,
    enabled: Boolean = true,
    thresholdDp: Dp = 64.dp,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(density) { thresholdDp.toPx() }

    return this.pointerInput(enabled, density) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDragX = 0f
            var isDrag = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dragX = change.position.x - change.previousPosition.x
                totalDragX += dragX
                if (abs(totalDragX) > viewConfiguration.touchSlop) {
                    isDrag = true
                }
                if (isDrag) {
                    if (totalDragX <= -thresholdPx) {
                        totalDragX += thresholdPx
                        change.consume()
                        currentOnSwipeLeft()
                    } else if (totalDragX >= thresholdPx) {
                        totalDragX -= thresholdPx
                        change.consume()
                        currentOnSwipeRight()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchEngineBadge(
    engine: SearchEngine,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    isDark: Boolean = false,
) {
    val context = LocalContext.current
    var isImageError by remember(engine.iconUrl) { mutableStateOf(false) }

    val imageModel =
        remember(engine.iconUrl) {
            FaviconManager.toImageModel(engine.iconUrl)
        }

    val imageRequest =
        remember(imageModel, context) {
            ImageRequest
                .Builder(context)
                .data(imageModel)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(false)
                .build()
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isDark) DarkSurfaceVariant else ProstoPaleBlue),
        contentAlignment = Alignment.Center,
    ) {
        if (!isImageError && imageModel != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = engine.displayName,
                contentScale = ContentScale.Fit,
                onError = { isImageError = true },
                onSuccess = { isImageError = false },
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = engine.displayName.take(1).uppercase(),
                style =
                    LocalProstoTypography.current.labelSm.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.45f).sp,
                    ),
                color = if (isDark) DarkPrimary else ProstoBrandPrimary,
            )
        }
    }
}
