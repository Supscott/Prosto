package com.jestk.browser.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
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
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkPrimary
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * State and gesture controller for the search engine logo carousel in SearchOverlay.
 * Provides synchronized dragging, spline-based fling decay with inertia,
 * spring snapping to the nearest engine slot, and haptic feedback on slot crossing.
 */
internal class SearchEngineCarouselState(
    initialEngine: SearchEngine,
    val density: Density,
    val coroutineScope: CoroutineScope,
    val haptic: HapticFeedback,
    var engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    val onEngineSelected: (SearchEngine) -> Unit
) {
    var currentEngine by mutableStateOf(initialEngine)
    val animOffset = Animatable(0f)
    var isDragging by mutableStateOf(false)
    var isFlinging by mutableStateOf(false)
    val isScrolling: Boolean get() = isDragging || isFlinging || abs(animOffset.value) > 0.5f

    val itemSpacingDp: Dp = 64.dp
    val itemSpacingPx: Float = with(density) { itemSpacingDp.toPx() }

    private var lastHapticSlot: Int = 0
    private var activeJob: Job? = null

    fun onDragStart() {
        activeJob?.cancel()
        isDragging = true
        isFlinging = false
        lastHapticSlot = (-animOffset.value / itemSpacingPx).roundToInt()
    }

    fun onDrag(dragAmount: Float) {
        activeJob?.cancel()
        coroutineScope.launch {
            val newOffset = animOffset.value + dragAmount
            animOffset.snapTo(newOffset)
            val currentSlot = (-newOffset / itemSpacingPx).roundToInt()
            if (currentSlot != lastHapticSlot) {
                lastHapticSlot = currentSlot
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun onDragEnd(velocity: Float) {
        isDragging = false
        val minFlingVelocity = 400f // px/sec
        activeJob = coroutineScope.launch {
            val currentOffset = animOffset.value
            val targetSlot: Int

            if (abs(velocity) >= minFlingVelocity) {
                isFlinging = true
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
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialVelocity = velocity
                    ) {
                        val currentSlot = (-value / itemSpacingPx).roundToInt()
                        if (currentSlot != lastHapticSlot) {
                            lastHapticSlot = currentSlot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                } else {
                    animOffset.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) {
                        val currentSlot = (-value / itemSpacingPx).roundToInt()
                        if (currentSlot != lastHapticSlot) {
                            lastHapticSlot = currentSlot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }

                // Settle and commit
                val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                val currentIndex = list.indexOfFirst { it.id == currentEngine.id }.let { if (it == -1) 0 else it }
                val finalIndex = (currentIndex + targetSlot).mod(list.size)
                val newEngine = list[finalIndex]

                currentEngine = newEngine
                animOffset.snapTo(0f)
                lastHapticSlot = 0
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
            lastHapticSlot = 0
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
                activeJob = coroutineScope.launch {
                    isFlinging = true
                    try {
                        animOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        animOffset.snapTo(0f)
                        lastHapticSlot = 0
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
        lastHapticSlot = (-animOffset.value / itemSpacingPx).roundToInt()

        activeJob = coroutineScope.launch {
            try {
                animOffset.animateTo(
                    targetValue = targetX,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    val currentSlot = (-value / itemSpacingPx).roundToInt()
                    if (currentSlot != lastHapticSlot) {
                        lastHapticSlot = currentSlot
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                currentEngine = targetEngine
                animOffset.snapTo(0f)
                lastHapticSlot = 0
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
    haptic: HapticFeedback = LocalHapticFeedback.current,
    onEngineSelected: (SearchEngine) -> Unit
): SearchEngineCarouselState {
    val currentOnEngineSelected by rememberUpdatedState(onEngineSelected)
    val state = remember(density) {
        SearchEngineCarouselState(
            initialEngine = initialEngine,
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            engines = engines,
            onEngineSelected = { currentOnEngineSelected(it) }
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
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) DarkSurface else Color.White
    val border = SereneBorders.cardBorder(isDark)
    val density = carouselState.density

    val arrowRotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "arrowRotation"
    )

    val arrowAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "arrowAlpha"
    )

    val fadeAlpha by animateFloatAsState(
        targetValue = if (carouselState.isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "fadeAlpha"
    )

    val restingShiftPx = with(density) { (-11).dp.toPx() } * arrowAlpha

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = 4.dp,
        modifier = modifier
            .width(68.dp)
            .height(52.dp)
            .semantics {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .searchEngineButtonGestures(
                carouselState = carouselState,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Badges Carousel with Edge Fade Mask
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        if (fadeAlpha > 0.01f) {
                            val fadeWidth = 14.dp.toPx() * fadeAlpha
                            val w = size.width
                            if (fadeWidth > 0f && w > 0f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        (fadeWidth / w) to Color.Black,
                                        ((w - fadeWidth) / w) to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val list = if (carouselState.engines.isNotEmpty()) carouselState.engines else SearchEngine.DEFAULT_ENGINES
                val baseIndex = list.indexOfFirst { it.id == carouselState.currentEngine.id }.let { if (it == -1) 0 else it }
                val centerSlotFloat = -carouselState.animOffset.value / carouselState.itemSpacingPx
                val centerSlotInt = centerSlotFloat.roundToInt()

                for (k in (centerSlotInt - 2)..(centerSlotInt + 2)) {
                    key(k) {
                        val engineIndex = (baseIndex + k).mod(list.size)
                        val engine = list[engineIndex]
                        val xPos = k * carouselState.itemSpacingPx + carouselState.animOffset.value + restingShiftPx

                        SearchEngineBadge(
                            engine = engine,
                            size = 26.dp,
                            isDark = isDark,
                            modifier = Modifier.graphicsLayer {
                                translationX = xPos
                            }
                        )
                    }
                }
            }

            // Dropdown Indicator Arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Search engine selector",
                tint = if (isDark) DarkOnSurfaceVariant else SereneMutedSlate,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = arrowRotation
                        alpha = arrowAlpha
                    }
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
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    val carouselState = remember(selectedEngine, density, engines) {
        SearchEngineCarouselState(
            initialEngine = selectedEngine,
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            engines = engines,
            onEngineSelected = { target ->
                val list = if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
                val oldIdx = list.indexOfFirst { it.id == selectedEngine.id }.let { if (it == -1) 0 else it }
                val newIdx = list.indexOfFirst { it.id == target.id }.let { if (it == -1) 0 else it }
                val diff = (newIdx - oldIdx).mod(list.size)
                if (diff == 1) {
                    currentOnSwipeLeft()
                } else if (diff == list.size - 1) {
                    currentOnSwipeRight()
                }
            }
        )
    }

    SearchEngineSelectorButton(
        carouselState = carouselState,
        isMenuOpen = isMenuOpen,
        isDark = isDark,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
internal fun Modifier.searchEngineButtonGestures(
    carouselState: SearchEngineCarouselState,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val viewConfig = LocalViewConfiguration.current

    return this.pointerInput(carouselState) {
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
                    if (!isDrag) {
                        currentOnClick()
                    } else {
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
internal fun Modifier.searchEngineInputGestures(
    carouselState: SearchEngineCarouselState,
    enabled: Boolean = true
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
    onSwipeRight: () -> Unit
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
    isDark: Boolean = false
) {
    val context = LocalContext.current
    var isImageError by remember(engine.iconUrl) { mutableStateOf(false) }

    val imageModel = remember(engine.iconUrl) {
        FaviconManager.toImageModel(engine.iconUrl)
    }

    val imageRequest = remember(imageModel, context) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
        contentAlignment = Alignment.Center
    ) {
        if (!isImageError && imageModel != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = engine.displayName,
                contentScale = ContentScale.Fit,
                onError = { isImageError = true },
                onSuccess = { isImageError = false },
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = engine.displayName.take(1).uppercase(),
                style = LocalSereneTypography.current.labelSm.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp
                ),
                color = if (isDark) DarkPrimary else SereneBrandPrimary
            )
        }
    }
}

@Composable
internal fun SearchEngineSwitcherRow(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    engines: List<SearchEngine> = SearchEngine.DEFAULT_ENGINES,
    elevation: Dp = 0.dp
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        engines.forEach { engine ->
            val isSelected = engine.id == selectedEngine.id
            SearchEngineChip(
                engine = engine,
                isSelected = isSelected,
                isDark = isDark,
                elevation = elevation,
                onClick = { onSelectEngine(engine) }
            )
        }
    }
}

@Composable
internal fun SearchEngineChip(
    engine: SearchEngine,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp
) {
    val containerColor = when {
        isSelected && isDark -> DarkSurfaceVariant
        isSelected && !isDark -> SerenePaleBlue
        !isSelected && isDark -> DarkSurface
        else -> Color.White
    }

    val border = when {
        isSelected -> BorderStroke(1.5.dp, SereneBrandPrimary)
        isDark -> BorderStroke(1.dp, DarkSurfaceVariant)
        else -> BorderStroke(1.dp, Color(0x2664748B))
    }

    val contentColor = when {
        isSelected && isDark -> DarkOnSurface
        isSelected && !isDark -> SereneBrandPrimary
        isDark -> DarkOnSurfaceVariant
        else -> SereneDarkText
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = border,
        shadowElevation = elevation,
        onClick = onClick,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchEngineBadge(
                engine = engine,
                size = 20.dp,
                isDark = isDark
            )

            Text(
                text = engine.displayName,
                style = LocalSereneTypography.current.labelMd.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}
