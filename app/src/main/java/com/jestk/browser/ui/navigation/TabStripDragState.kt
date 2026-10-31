package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Collections

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
)

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

/**
 * State controller managing drag & drop tab reordering and tactile spring release physics
 * in [TabStripRow].
 *
 * Provides smooth dragging, immediate slot swapping with vibration feedback,
 * and spring-driven release settlement back to translation 0f and scale 1.0f.
 */
internal class TabStripDragState(
    initialTabs: List<Tab>,
    val coroutineScope: CoroutineScope,
    val onReorder: (List<Tab>) -> Unit,
    val onRemoveTab: (String) -> Unit = {},
) {
    var currentTabs by mutableStateOf(initialTabs)
        private set

    var draggingTabId by mutableStateOf<String?>(null)
        private set

    var settlingTabId by mutableStateOf<String?>(null)
        private set

    var removingTab by mutableStateOf<Tab?>(null)
        private set

    val removingTabId: String?
        get() = removingTab?.id

    var lastVisualXPx by mutableFloatStateOf(0f)
        private set

    var removingVisualX by mutableFloatStateOf(0f)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    var dragOffsetYPx by mutableFloatStateOf(0f)
        private set

    var isOverRemoveZone by mutableStateOf(false)
        private set

    var isDragging by mutableStateOf(false)
        private set

    val isSettling: Boolean
        get() = settlingTabId != null

    val isRemoving: Boolean
        get() = removingTab != null

    val settleOffsetAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleOffsetYAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleScaleAnim = Animatable(1.0f)
    val swapScaleBump = Animatable(0f)

    val removeScaleAnim = Animatable(1.0f)
    val removeAlphaAnim = Animatable(1.0f)
    val removeOffsetYAnim = Animatable(0f)

    private enum class SwapDirection { LEFT, RIGHT }
    private var lastSwapDirection: SwapDirection? = null
    private var lastSwapReverseTargetIndex: Int = -1
    private var swapBumpJob: Job? = null

    private var settleJob: Job? = null
    private var removeJob: Job? = null
    private var pendingTabs: List<Tab>? = null

    fun updateTabs(newTabs: List<Tab>) {
        if (!isDragging && !isSettling && !isRemoving) {
            currentTabs = newTabs
            pendingTabs = null
        } else {
            val filtered = removingTab?.id?.let { id -> newTabs.filter { it.id != id } } ?: newTabs
            pendingTabs = filtered
        }
    }

    private fun resetSwapState() {
        lastSwapDirection = null
        lastSwapReverseTargetIndex = -1
        swapBumpJob?.cancel()
        swapBumpJob = null
        if (swapScaleBump.value != 0f) {
            coroutineScope.launch {
                swapScaleBump.snapTo(0f)
            }
        }
    }

    fun cancelSettlingImmediately() {
        if (isSettling) {
            settleJob?.cancel()
            settleJob = null
            settlingTabId = null
            resetSwapState()
            coroutineScope.launch {
                settleOffsetAnim.snapTo(0f)
                settleOffsetYAnim.snapTo(0f)
                settleScaleAnim.snapTo(1.0f)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
        if (isRemoving) {
            removeJob?.cancel()
            removeJob = null
            val removedId = removingTab?.id
            removingTab = null
            coroutineScope.launch {
                removeScaleAnim.snapTo(1.0f)
                removeAlphaAnim.snapTo(1.0f)
                removeOffsetYAnim.snapTo(0f)
            }
            if (removedId != null) {
                onRemoveTab(removedId)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun updateVisualX(visualX: Float) {
        lastVisualXPx = visualX
    }

    fun onDragStart(tabId: String, initialVisualX: Float = 0f) {
        if (isSettling || isRemoving) {
            cancelSettlingImmediately()
        }
        draggingTabId = tabId
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        lastVisualXPx = initialVisualX
        isOverRemoveZone = false
        isDragging = true
        resetSwapState()
    }

    fun updateDragOffset(offsetPx: Float, offsetYPx: Float = dragOffsetYPx) {
        if (isDragging) {
            dragOffsetPx = offsetPx
            dragOffsetYPx = offsetYPx
        }
    }

    fun updateVerticalDrag(
        offsetY: Float,
        removeThresholdPx: Float,
        onEnterRemoveZone: () -> Unit = {},
        onExitRemoveZone: () -> Unit = {},
    ) {
        if (!isDragging) return
        dragOffsetYPx = if (offsetY > 0f) offsetY * 0.15f else offsetY
        val wasOverRemove = isOverRemoveZone
        val nowOverRemove = dragOffsetYPx <= -removeThresholdPx
        if (wasOverRemove != nowOverRemove) {
            isOverRemoveZone = nowOverRemove
            if (nowOverRemove) {
                onEnterRemoveZone()
            } else {
                onExitRemoveZone()
            }
        }
    }

    internal fun getTabWidth(
        tab: Tab,
        metrics: TabStripLayoutMetrics,
    ): Float {
        if (tab.id == metrics.draggingTabId) return metrics.inactiveTabWidthPx
        val isActive = tab.isActive || (metrics.activeTabId != null && tab.id == metrics.activeTabId)
        return if (isActive) metrics.activeTabWidthPx else metrics.inactiveTabWidthPx
    }

    internal fun getSlotStart(
        index: Int,
        tabs: List<Tab>,
        metrics: TabStripLayoutMetrics,
    ): Float {
        var start = 0f
        for (i in 0 until index.coerceIn(0, tabs.size)) {
            start += getTabWidth(tabs[i], metrics) + metrics.spacingPx
        }
        return start
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        metrics: TabStripLayoutMetrics,
        onSwapVibration: () -> Unit = {}
    ) {
        if (!isDragging || draggingTabId != tabId) return
        dragOffsetPx += deltaX
        if (isOverRemoveZone) {
            return
        }
        val currentIndex = currentTabs.indexOfFirst { it.id == tabId }
        if (currentIndex == -1) return

        val draggedTab = currentTabs[currentIndex]
        val draggedWidth = getTabWidth(draggedTab, metrics)
        val currentSlotStart = getSlotStart(currentIndex, currentTabs, metrics)
        val visualStart = currentSlotStart + dragOffsetPx
        val visualEnd = visualStart + draggedWidth
        val visualCenter = visualStart + 0.5f * draggedWidth

        // Check swap right
        if (currentIndex < currentTabs.lastIndex) {
            val targetIndex = currentIndex + 1
            val targetTab = currentTabs[targetIndex]
            val targetWidth = getTabWidth(targetTab, metrics)
            val targetSlotStart = currentSlotStart + draggedWidth + metrics.spacingPx
            val isReverseSwap = lastSwapDirection == SwapDirection.LEFT && targetIndex == lastSwapReverseTargetIndex
            val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f

            val canSwapRight = if (draggedWidth > targetWidth) {
                // Wide active tab moving right: triggers as soon as its leading edge enters 50% of target slot
                val targetTriggerX = targetSlotStart + 0.5f * targetWidth + hysteresisMargin
                visualEnd > targetTriggerX
            } else {
                // Inactive tab moving right onto active tab (or same size): triggers when its center crosses target center
                val targetCenter = targetSlotStart + 0.5f * targetWidth
                val targetTriggerX = targetCenter + hysteresisMargin
                visualCenter > targetTriggerX
            }

            if (canSwapRight) {
                val mutable = currentTabs.toMutableList()
                Collections.swap(mutable, currentIndex, targetIndex)
                currentTabs = mutable

                val newSlotStart = currentSlotStart + targetWidth + metrics.spacingPx
                dragOffsetPx = visualStart - newSlotStart

                lastSwapDirection = SwapDirection.RIGHT
                lastSwapReverseTargetIndex = currentIndex

                triggerSwapFeedback(onSwapVibration)
                return
            }
        }

        // Check swap left
        if (currentIndex > 0) {
            val targetIndex = currentIndex - 1
            val targetTab = currentTabs[targetIndex]
            val targetWidth = getTabWidth(targetTab, metrics)
            val targetSlotStart = currentSlotStart - (targetWidth + metrics.spacingPx)
            val targetSlotEnd = targetSlotStart + targetWidth
            val isReverseSwap = lastSwapDirection == SwapDirection.RIGHT && targetIndex == lastSwapReverseTargetIndex
            val hysteresisMargin = if (isReverseSwap) metrics.hysteresisPx else 0f

            val canSwapLeft = if (draggedWidth > targetWidth) {
                // Wide active tab moving left: triggers as soon as its leading edge enters 50% of target slot
                val targetTriggerX = targetSlotEnd - 0.5f * targetWidth - hysteresisMargin
                visualStart < targetTriggerX
            } else {
                // Inactive tab moving left onto active tab (or same size): triggers when its center crosses target center
                val targetCenter = targetSlotStart + 0.5f * targetWidth
                val targetTriggerX = targetCenter - hysteresisMargin
                visualCenter < targetTriggerX
            }

            if (canSwapLeft) {
                val mutable = currentTabs.toMutableList()
                Collections.swap(mutable, currentIndex, targetIndex)
                currentTabs = mutable

                val newSlotStart = targetSlotStart
                dragOffsetPx = visualStart - newSlotStart

                lastSwapDirection = SwapDirection.LEFT
                lastSwapReverseTargetIndex = currentIndex

                triggerSwapFeedback(onSwapVibration)
                return
            }
        }
    }

    private fun triggerSwapFeedback(onSwapVibration: () -> Unit) {
        onSwapVibration()
        swapBumpJob?.cancel()
        swapBumpJob = coroutineScope.launch {
            swapScaleBump.snapTo(0.06f)
            swapScaleBump.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            )
            swapBumpJob = null
        }
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        activeTabWidthPx: Float,
        inactiveTabWidthPx: Float,
        spacingPx: Float,
        activeTabId: String? = null,
        hysteresisPx: Float = 10f,
        onSwapVibration: () -> Unit = {}
    ) {
        onDrag(
            tabId = tabId,
            deltaX = deltaX,
            metrics = TabStripLayoutMetrics(
                activeTabWidthPx = activeTabWidthPx,
                inactiveTabWidthPx = inactiveTabWidthPx,
                spacingPx = spacingPx,
                activeTabId = activeTabId,
                hysteresisPx = hysteresisPx
            ),
            onSwapVibration = onSwapVibration
        )
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        swapThreshold: Float,
        onSwapVibration: () -> Unit = {}
    ) {
        onDrag(
            tabId = tabId,
            deltaX = deltaX,
            activeTabWidthPx = swapThreshold,
            inactiveTabWidthPx = swapThreshold,
            spacingPx = 0f,
            activeTabId = null,
            hysteresisPx = 0f,
            onSwapVibration = onSwapVibration
        )
    }

    fun onDragEnd(
        isReleasedActive: Boolean,
        originalTabs: List<Tab>? = null,
        onConfirmVibration: () -> Unit = {},
        currentScale: Float? = null,
        currentAlpha: Float? = null,
    ) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        if (isOverRemoveZone) {
            isDragging = false
            draggingTabId = null
            isOverRemoveZone = false
            resetSwapState()
            onConfirmVibration()

            val removedTab = currentTabs.firstOrNull { it.id == tabId }
            val remainingTabs = currentTabs.filter { it.id != tabId }

            if (originalTabs != null && currentTabs != originalTabs) {
                onReorder(remainingTabs)
            }

            // Immediately drop tab from dock list so LazyRow closes the gap without leaving a ghost icon
            currentTabs = remainingTabs
            onRemoveTab(tabId)

            val startScale = currentScale ?: 0.60f
            val startAlpha = currentAlpha ?: 0.45f
            val startY = dragOffsetYPx

            removingTab = removedTab
            removingVisualX = lastVisualXPx

            removeJob?.cancel()
            removeJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                removeScaleAnim.snapTo(startScale)
                removeAlphaAnim.snapTo(startAlpha)
                removeOffsetYAnim.snapTo(startY)

                val scaleJob = launch {
                    removeScaleAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                }
                val alphaJob = launch {
                    removeAlphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    )
                }
                val offsetJob = launch {
                    removeOffsetYAnim.animateTo(
                        targetValue = startY - 30f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                }

                scaleJob.join()
                alphaJob.join()
                offsetJob.join()

                removingTab = null
                removeScaleAnim.snapTo(1.0f)
                removeAlphaAnim.snapTo(1.0f)
                removeOffsetYAnim.snapTo(0f)
                dragOffsetPx = 0f
                dragOffsetYPx = 0f
                removeJob = null

                pendingTabs?.let {
                    currentTabs = it.filter { t -> t.id != tabId }
                    pendingTabs = null
                }
            }
            return
        }

        val finalOffset = dragOffsetPx
        val finalOffsetY = dragOffsetYPx
        val startScale = currentScale ?: (if (isReleasedActive) 1.05f else 1.08f)

        if (originalTabs != null && currentTabs != originalTabs) {
            onReorder(currentTabs)
        }

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        resetSwapState()

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleOffsetYAnim.snapTo(finalOffsetY)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val offsetYJob = launch {
                settleOffsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                )
            }
            offsetJob.join()
            offsetYJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleOffsetYAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun onDragCancel(isReleasedActive: Boolean) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        val finalOffset = dragOffsetPx
        val finalOffsetY = dragOffsetYPx
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        isOverRemoveZone = false
        resetSwapState()

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleOffsetYAnim.snapTo(finalOffsetY)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val offsetYJob = launch {
                settleOffsetYAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f,
                    ),
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                )
            }
            offsetJob.join()
            offsetYJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
            settleOffsetYAnim.snapTo(0f)
            settleScaleAnim.snapTo(1.0f)
            settleJob = null

            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }
}

@Composable
internal fun rememberTabStripDragState(
    initialTabs: List<Tab>,
    onReorder: (List<Tab>) -> Unit,
    onRemoveTab: (String) -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): TabStripDragState {
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnRemoveTab by rememberUpdatedState(onRemoveTab)
    return remember {
        TabStripDragState(
            initialTabs = initialTabs,
            coroutineScope = coroutineScope,
            onReorder = { currentOnReorder(it) },
            onRemoveTab = { currentOnRemoveTab(it) },
        )
    }
}
