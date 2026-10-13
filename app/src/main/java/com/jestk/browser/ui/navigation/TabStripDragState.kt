package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
 * State controller managing drag & drop tab reordering and tactile spring release physics
 * in [TabStripRow].
 *
 * Provides smooth dragging, immediate slot swapping with haptic feedback,
 * and spring-driven release settlement back to translation 0f and scale 1.0f.
 */
internal class TabStripDragState(
    initialTabs: List<Tab>,
    val coroutineScope: CoroutineScope,
    val onReorder: (List<Tab>) -> Unit
) {
    var currentTabs by mutableStateOf(initialTabs)
        private set

    var draggingTabId by mutableStateOf<String?>(null)
        private set

    var settlingTabId by mutableStateOf<String?>(null)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    val isSettling: Boolean
        get() = settlingTabId != null

    val settleOffsetAnim = Animatable(0f, visibilityThreshold = 0.5f)
    val settleScaleAnim = Animatable(1.0f)

    private var settleJob: Job? = null
    private var pendingTabs: List<Tab>? = null

    fun updateTabs(newTabs: List<Tab>) {
        if (!isDragging && !isSettling) {
            currentTabs = newTabs
            pendingTabs = null
        } else {
            pendingTabs = newTabs
        }
    }

    fun cancelSettlingImmediately() {
        if (isSettling) {
            settleJob?.cancel()
            settleJob = null
            settlingTabId = null
            coroutineScope.launch {
                settleOffsetAnim.snapTo(0f)
                settleScaleAnim.snapTo(1.0f)
            }
            pendingTabs?.let {
                currentTabs = it
                pendingTabs = null
            }
        }
    }

    fun onDragStart(tabId: String) {
        if (isSettling) {
            cancelSettlingImmediately()
        }
        draggingTabId = tabId
        dragOffsetPx = 0f
        isDragging = true
    }

    fun computeSlotDistance(
        currentIndex: Int,
        targetIndex: Int,
        activeTabWidthPx: Float,
        inactiveTabWidthPx: Float,
        spacingPx: Float,
        activeTabId: String? = null
    ): Float {
        if (currentIndex !in currentTabs.indices || targetIndex !in currentTabs.indices) {
            return inactiveTabWidthPx + spacingPx
        }
        val currentTab = currentTabs[currentIndex]
        val targetTab = currentTabs[targetIndex]
        val isCurrentActive = currentTab.isActive || (activeTabId != null && currentTab.id == activeTabId)
        val isTargetActive = targetTab.isActive || (activeTabId != null && targetTab.id == activeTabId)
        val currentWidth = if (isCurrentActive) activeTabWidthPx else inactiveTabWidthPx
        val targetWidth = if (isTargetActive) activeTabWidthPx else inactiveTabWidthPx
        return (currentWidth + targetWidth) / 2f + spacingPx
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        activeTabWidthPx: Float,
        inactiveTabWidthPx: Float,
        spacingPx: Float,
        activeTabId: String? = null,
        onSwapHaptic: () -> Unit = {}
    ) {
        if (!isDragging || draggingTabId != tabId) return
        dragOffsetPx += deltaX
        val currentIndex = currentTabs.indexOfFirst { it.id == tabId }
        if (currentIndex != -1) {
            if (currentIndex < currentTabs.lastIndex) {
                val targetIndex = currentIndex + 1
                val distance = computeSlotDistance(
                    currentIndex = currentIndex,
                    targetIndex = targetIndex,
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = spacingPx,
                    activeTabId = activeTabId
                )
                if (dragOffsetPx > distance) {
                    val mutable = currentTabs.toMutableList()
                    Collections.swap(mutable, currentIndex, targetIndex)
                    currentTabs = mutable
                    dragOffsetPx -= distance
                    onSwapHaptic()
                    return
                }
            }
            if (currentIndex > 0) {
                val targetIndex = currentIndex - 1
                val distance = computeSlotDistance(
                    currentIndex = currentIndex,
                    targetIndex = targetIndex,
                    activeTabWidthPx = activeTabWidthPx,
                    inactiveTabWidthPx = inactiveTabWidthPx,
                    spacingPx = spacingPx,
                    activeTabId = activeTabId
                )
                if (dragOffsetPx < -distance) {
                    val mutable = currentTabs.toMutableList()
                    Collections.swap(mutable, currentIndex, targetIndex)
                    currentTabs = mutable
                    dragOffsetPx += distance
                    onSwapHaptic()
                    return
                }
            }
        }
    }

    fun onDrag(
        tabId: String,
        deltaX: Float,
        swapThreshold: Float,
        onSwapHaptic: () -> Unit = {}
    ) {
        onDrag(
            tabId = tabId,
            deltaX = deltaX,
            activeTabWidthPx = swapThreshold,
            inactiveTabWidthPx = swapThreshold,
            spacingPx = 0f,
            activeTabId = null,
            onSwapHaptic = onSwapHaptic
        )
    }

    fun onDragEnd(isReleasedActive: Boolean, originalTabs: List<Tab>? = null) {
        val tabId = draggingTabId ?: return
        if (!isDragging) return

        val finalOffset = dragOffsetPx
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        if (originalTabs != null && currentTabs != originalTabs) {
            onReorder(currentTabs)
        }

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f
                    )
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    )
                )
            }
            offsetJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
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
        val startScale = if (isReleasedActive) 1.05f else 1.08f

        isDragging = false
        draggingTabId = null
        dragOffsetPx = 0f

        settlingTabId = tabId
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffsetAnim.snapTo(finalOffset)
            settleScaleAnim.snapTo(startScale)

            val offsetJob = launch {
                settleOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        visibilityThreshold = 0.5f
                    )
                )
            }
            val scaleJob = launch {
                settleScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    )
                )
            }
            offsetJob.join()
            scaleJob.join()

            settlingTabId = null
            settleOffsetAnim.snapTo(0f)
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
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): TabStripDragState {
    val currentOnReorder by rememberUpdatedState(onReorder)
    return remember {
        TabStripDragState(
            initialTabs = initialTabs,
            coroutineScope = coroutineScope,
            onReorder = { currentOnReorder(it) }
        )
    }
}
