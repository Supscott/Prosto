package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.TabFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State controller managing drag & drop tab reordering and tactile spring release physics
 * in [TabStripRow].
 *
 * Provides smooth dragging, immediate slot swapping with a scale bump,
 * and spring-driven release settlement back to translation 0f and scale 1.0f.
 */
internal const val SPRING_LOAD_HOVER_TIMEOUT_MS = 650L

internal class TabStripDragState(
    initialTabs: List<Tab>,
    val coroutineScope: CoroutineScope,
    val onReorder: (List<Tab>) -> Unit,
    val onRemoveTab: (String) -> Unit = {},
    val foldersProvider: () -> List<TabFolder> = { emptyList() },
    val onToggleFolderCollapse: (String) -> Unit = {},
    val onHoverVibration: () -> Unit = {},
    val onSpringLoadVibration: () -> Unit = {},
) {
    var currentTabs by mutableStateOf(initialTabs)
        internal set

    var draggingTabId by mutableStateOf<String?>(null)
        internal set

    var hoveredFolderId by mutableStateOf<String?>(null)
        internal set

    var springLoadedFolderId by mutableStateOf<String?>(null)
        internal set

    internal var springLoadJob: Job? = null
    private var draggedTabOriginalFolderId: String? = null

    var settlingTabId by mutableStateOf<String?>(null)
        internal set

    var removingTab by mutableStateOf<Tab?>(null)
        internal set

    val removingTabId: String?
        get() = removingTab?.id

    var lastVisualXPx by mutableFloatStateOf(0f)
        private set

    var removingVisualX by mutableFloatStateOf(0f)
        internal set

    var dragOffsetPx by mutableFloatStateOf(0f)
        internal set

    var dragOffsetYPx by mutableFloatStateOf(0f)
        internal set

    var isOverRemoveZone by mutableStateOf(false)
        internal set

    var isDragging by mutableStateOf(false)
        internal set

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

    internal enum class SwapDirection { LEFT, RIGHT }
    internal var lastSwapDirection: SwapDirection? = null
    internal var lastSwapReverseTargetKey: String? = null
    internal var swapBumpJob: Job? = null

    internal var settleJob: Job? = null
    internal var removeJob: Job? = null
    internal var pendingTabs: List<Tab>? = null

    fun updateTabs(newTabs: List<Tab>) {
        if (!isDragging && !isSettling && !isRemoving) {
            currentTabs = newTabs
            pendingTabs = null
        } else {
            val filtered = removingTab?.id?.let { id -> newTabs.filter { it.id != id } } ?: newTabs
            pendingTabs = filtered
        }
    }

    internal fun resetSwapState() {
        lastSwapDirection = null
        lastSwapReverseTargetKey = null
        swapBumpJob?.cancel()
        swapBumpJob = null
        if (swapScaleBump.value != 0f) {
            coroutineScope.launch {
                swapScaleBump.snapTo(0f)
            }
        }
    }

    fun cancelSettlingImmediately() {
        springLoadJob?.cancel()
        springLoadJob = null
        hoveredFolderId = null
        springLoadedFolderId = null
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
        draggedTabOriginalFolderId = currentTabs.find { it.id == tabId }?.folderId
        dragOffsetPx = 0f
        dragOffsetYPx = 0f
        lastVisualXPx = initialVisualX
        isOverRemoveZone = false
        isDragging = true
        hoveredFolderId = null
        springLoadedFolderId = null
        springLoadJob?.cancel()
        springLoadJob = null
        resetSwapState()
    }

    fun updateClosedFolderHover(hoveredFolderId: String?) {
        if (this.hoveredFolderId == hoveredFolderId) return
        this.hoveredFolderId = hoveredFolderId
        springLoadJob?.cancel()
        springLoadJob = null

        if (hoveredFolderId != null) {
            onHoverVibration()
            springLoadJob = coroutineScope.launch {
                delay(SPRING_LOAD_HOVER_TIMEOUT_MS)
                if (isDragging && draggingTabId != null && this@TabStripDragState.hoveredFolderId == hoveredFolderId) {
                    springLoadedFolderId = hoveredFolderId
                    this@TabStripDragState.hoveredFolderId = null
                    onSpringLoadVibration()
                    onToggleFolderCollapse(hoveredFolderId)
                }
            }
        }
    }

    fun onSpringLoadedFolderExited() {
        val folderId = springLoadedFolderId ?: return
        springLoadedFolderId = null
        val draggedId = draggingTabId
        if (draggedId != null) {
            val idx = currentTabs.indexOfFirst { it.id == draggedId }
            if (idx >= 0 && currentTabs[idx].folderId == folderId) {
                val mutable = currentTabs.toMutableList()
                mutable[idx] = mutable[idx].copy(folderId = draggedTabOriginalFolderId)
                currentTabs = mutable
            }
        }
        onToggleFolderCollapse(folderId)
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
        val defaultWidth = if (isActive) metrics.activeTabWidthPx else metrics.inactiveTabWidthPx
        return metrics.getItemWidth(tab.id, defaultWidth)
    }
}

@Composable
internal fun rememberTabStripDragState(
    initialTabs: List<Tab>,
    onReorder: (List<Tab>) -> Unit,
    onRemoveTab: (String) -> Unit = {},
    foldersProvider: () -> List<TabFolder> = { emptyList() },
    onToggleFolderCollapse: (String) -> Unit = {},
    onHoverVibration: () -> Unit = {},
    onSpringLoadVibration: () -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): TabStripDragState {
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnRemoveTab by rememberUpdatedState(onRemoveTab)
    val currentFoldersProvider by rememberUpdatedState(foldersProvider)
    val currentOnToggleFolderCollapse by rememberUpdatedState(onToggleFolderCollapse)
    val currentOnHoverVibration by rememberUpdatedState(onHoverVibration)
    val currentOnSpringLoadVibration by rememberUpdatedState(onSpringLoadVibration)
    return remember {
        TabStripDragState(
            initialTabs = initialTabs,
            coroutineScope = coroutineScope,
            onReorder = { currentOnReorder(it) },
            onRemoveTab = { currentOnRemoveTab(it) },
            foldersProvider = { currentFoldersProvider() },
            onToggleFolderCollapse = { currentOnToggleFolderCollapse(it) },
            onHoverVibration = { currentOnHoverVibration() },
            onSpringLoadVibration = { currentOnSpringLoadVibration() },
        )
    }
}