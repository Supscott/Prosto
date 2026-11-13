package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun TabStripDragState.onDragEnd(
    isReleasedActive: Boolean,
    originalTabs: List<Tab>? = null,
    onConfirmVibration: () -> Unit = {},
    currentScale: Float? = null,
    currentAlpha: Float? = null,
) {
    val tabId = draggingTabId ?: return
    if (!isDragging) return

    val currentHovered = hoveredFolderId
    if (currentHovered != null) {
        springLoadJob?.cancel()
        springLoadJob = null
        hoveredFolderId = null

        val draggedTab = currentTabs.firstOrNull { it.id == tabId }
        if (draggedTab != null) {
            val mutable = currentTabs.toMutableList()
            mutable.removeAll { it.id == tabId }
            val lastIdx = mutable.indexOfLast { it.folderId == currentHovered }
            val updatedTab = draggedTab.copy(folderId = currentHovered)
            if (lastIdx >= 0) {
                mutable.add(lastIdx + 1, updatedTab)
            } else {
                val allFolders = foldersProvider()
                val folderIdx = allFolders.indexOfFirst { it.id == currentHovered }
                val nextFolderWithTabs = allFolders.drop(folderIdx + 1).firstOrNull { f ->
                    mutable.any { it.folderId == f.id }
                }
                val insertIdx = if (nextFolderWithTabs != null) {
                    mutable.indexOfFirst { it.folderId == nextFolderWithTabs.id }
                } else {
                    mutable.size
                }
                mutable.add(insertIdx, updatedTab)
            }
            currentTabs = mutable
        }
    }

    val springId = springLoadedFolderId
    if (springId != null) {
        springLoadedFolderId = null
        val droppedTab = currentTabs.firstOrNull { it.id == tabId }
        if (droppedTab != null && droppedTab.folderId != springId) {
            // Drop occurred inside the spring-opened folder before swapping with a member tab
            val mutable = currentTabs.toMutableList()
            val oldIdx = mutable.indexOfFirst { it.id == tabId }
            if (oldIdx >= 0) {
                val removed = mutable.removeAt(oldIdx)
                val updatedTab = removed.copy(folderId = springId)
                val lastFolderTabIdx = mutable.indexOfLast { it.folderId == springId }
                val insertIdx = if (lastFolderTabIdx >= 0) {
                    lastFolderTabIdx + 1
                } else {
                    val allFolders = foldersProvider()
                    val folderIdx = allFolders.indexOfFirst { it.id == springId }
                    val nextFolderWithTabs = allFolders.drop(folderIdx + 1).firstOrNull { f ->
                        mutable.any { it.folderId == f.id }
                    }
                    if (nextFolderWithTabs != null) {
                        mutable.indexOfFirst { it.folderId == nextFolderWithTabs.id }
                    } else {
                        mutable.size
                    }
                }
                mutable.add(insertIdx.coerceIn(0, mutable.size), updatedTab)
                currentTabs = mutable
            }
        }
    }

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

        removingTab = removedTab
        // Immediately drop tab from dock list so LazyRow closes the gap without leaving a ghost icon
        currentTabs = remainingTabs
        onRemoveTab(tabId)

        val startScale = currentScale ?: 0.60f
        val startAlpha = currentAlpha ?: 0.45f
        val startY = dragOffsetYPx
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

internal fun TabStripDragState.onDragCancel(isReleasedActive: Boolean) {
    val tabId = draggingTabId ?: return
    if (!isDragging) return

    hoveredFolderId = null
    springLoadJob?.cancel()
    springLoadJob = null
    onSpringLoadedFolderExited()

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
