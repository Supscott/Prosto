package com.jestk.browser.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jestk.browser.R
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.ProstoBorders
import com.jestk.browser.ui.theme.ProstoMutedSlate
import com.jestk.browser.ui.viewmodel.BrowserUiState
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
internal fun TabStripDragOverlays(
    uiState: BrowserUiState,
    dragState: TabStripDragState,
    lazyListState: LazyListState,
    isDark: Boolean,
    activeTabWidth: Dp,
    centerPillBg: Color,
    centerPillBorder: BorderStroke?,
    circleBtnBg: Color,
    removeThresholdPx: Float,
    homeTitle: String,
    fallbackTitle: String,
) {
    val isHome = uiState.isHome

    // Floating Dragged / Settling Tab Overlay (outside LazyRow to prevent clipping above dock)
    val elevatedKey = dragState.draggingTabId ?: dragState.settlingTabId
    val elevatedTab = if (elevatedKey != null && !elevatedKey.startsWith("folder_")) {
        dragState.currentTabs.firstOrNull { it.id == elevatedKey }
    } else {
        null
    }
    val elevatedFolder = if (elevatedKey != null && elevatedKey.startsWith("folder_")) {
        val fId = elevatedKey.removePrefix("folder_")
        uiState.folders.firstOrNull { it.id == fId }
    } else {
        null
    }
    if (elevatedTab != null) {
        val isDraggingThis = dragState.draggingTabId == elevatedTab.id
        val isSettlingThis = dragState.settlingTabId == elevatedTab.id
        val isElevatedActive = elevatedTab.id == uiState.activeTabId
        val tabIsHome = elevatedTab.isHomeTab || (isHome && isElevatedActive)
        val isOverRemoveZone = isDraggingThis && dragState.isOverRemoveZone

        // Offsets, scale and alpha change on every pointer move: they are read in the draw phase
        // (inside graphicsLayer) so a drag does not recompose the pill. Only the remove cross
        // needs composition, and it gets a progress quantized to 1/20 steps.
        val quantizedRemoveProgress by remember(dragState, removeThresholdPx) {
            derivedStateOf {
                val progress = removeProgressOf(dragState.dragOffsetYPx, removeThresholdPx)
                (progress * REMOVE_PROGRESS_STEPS).roundToInt() / REMOVE_PROGRESS_STEPS
            }
        }
        val baseDragScale = if (isElevatedActive) 1.05f else 1.08f
        val tabLayer: GraphicsLayerScope.() -> Unit = {
            val dragY = dragState.dragOffsetYPx
            val removeProgress = removeProgressOf(dragY, removeThresholdPx)
            val overDistance = ((-dragY - removeThresholdPx) / removeThresholdPx).coerceIn(0f, 1f)
            val dynamicDragScale =
                (baseDragScale - (removeProgress * 0.20f) - (overDistance * 0.05f)).coerceAtLeast(0.80f)
            val dynamicDragAlpha =
                (1.0f - (removeProgress * 0.15f) - (overDistance * 0.05f)).coerceIn(0.80f, 1.0f)
            val itemScale =
                when {
                    isDraggingThis -> dynamicDragScale
                    isSettlingThis -> dragState.settleScaleAnim.value
                    else -> 1.0f
                }
            val effectiveScale = itemScale + if (isDraggingThis) dragState.swapScaleBump.value else 0f
            translationX = overlayOffsetX(dragState, lazyListState, elevatedTab.id, isDraggingThis, isSettlingThis)
            translationY = overlayOffsetY(dragState, isDraggingThis, isSettlingThis)
            scaleX = effectiveScale
            scaleY = effectiveScale
            alpha = if (isDraggingThis) dynamicDragAlpha else 1.0f
        }

        val tabRemoveProgress = if (isDraggingThis) quantizedRemoveProgress else 0f

        val renderedOverlayActiveWidth by animateDpAsState(
            targetValue = if (isDraggingThis) 38.dp else activeTabWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "OverlayActiveTabDragWidth",
        )

        if (isElevatedActive) {
            val displayInfo = resolveTabDisplayInfo(tab = elevatedTab, isActive = isElevatedActive, isHome = tabIsHome, uiState = uiState, homeTitle = homeTitle, fallbackTitle = fallbackTitle)

            val activePillBgActual =
                if (isDraggingThis) {
                    if (isDark) DarkSurface else Color.White
                } else {
                    centerPillBg
                }
            val activePillBorderActual =
                if (isDraggingThis) {
                    ProstoBorders.floatingBorder(isDark)
                } else {
                    centerPillBorder
                }

            ActiveTabPill(
                title = displayInfo.title,
                domain = displayInfo.domain,
                url = displayInfo.url,
                faviconUrl = elevatedTab.faviconUrl,
                isHome = tabIsHome,
                backgroundColor = activePillBgActual,
                border = activePillBorderActual,
                isDark = isDark,
                isLoading = uiState.isLoading,
                loadingProgress = LocalLoadingProgress.current.value,
                onOpenSearch = {},
                onClose = {},
                onAddFavorite = { _, _ -> },
                onShare = {},
                onReload = {},
                modifier =
                    Modifier
                        .width(renderedOverlayActiveWidth)
                        .zIndex(20f)
                        .graphicsLayer(tabLayer),
                menuExpanded = false,
                onMenuDismiss = {},
                isCompact = isDraggingThis,
                isRemovePending = isOverRemoveZone,
                removeProgress = tabRemoveProgress,
            )
        } else {
            val isTabLoaded = uiState.isTabLoaded(elevatedTab.id)
            val inactiveBtnBgActual =
                if (isDraggingThis) {
                    if (isDark) DarkSurface else Color.White
                } else {
                    circleBtnBg
                }
            val inactiveBtnBorderActual =
                if (isDraggingThis) {
                    ProstoBorders.floatingBorder(isDark)
                } else {
                    null
                }

            InactiveTabButton(
                tab = elevatedTab,
                isDark = isDark,
                isLoaded = isTabLoaded,
                grayUnloadedTabs = uiState.settings.isGrayUnloadedTabsEnabled,
                backgroundColor = inactiveBtnBgActual,
                border = inactiveBtnBorderActual,
                isRemovePending = isOverRemoveZone,
                removeProgress = tabRemoveProgress,
                onClick = {},
                onClose = {},
                onAddFavorite = { _, _ -> },
                modifier =
                    Modifier
                        .zIndex(20f)
                        .graphicsLayer {
                            tabLayer()
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                        },
                menuExpanded = false,
                onMenuDismiss = {},
            )
        }
    }

    if (elevatedFolder != null) {
        val isDraggingThis = dragState.draggingTabId == elevatedKey
        val isSettlingThis = dragState.settlingTabId == elevatedKey

        FolderDockIndicator(
            folder = elevatedFolder,
            onClick = {},
            modifier =
                Modifier
                    .zIndex(20f)
                    .graphicsLayer {
                        val itemScale =
                            when {
                                isDraggingThis -> 1.08f
                                isSettlingThis -> dragState.settleScaleAnim.value
                                else -> 1.0f
                            }
                        val effectiveScale = itemScale + if (isDraggingThis) dragState.swapScaleBump.value else 0f
                        translationX = overlayOffsetX(dragState, lazyListState, elevatedKey, isDraggingThis, isSettlingThis)
                        translationY = overlayOffsetY(dragState, isDraggingThis, isSettlingThis)
                        scaleX = effectiveScale
                        scaleY = effectiveScale
                        shadowElevation = 8.dp.toPx()
                    },
        )
    }

    // Floating Vanishing Tab Overlay during removal release animation
    val removingTab = dragState.removingTab
    if (removingTab != null) {
        val normalTint = if (isDark) DarkOnSurfaceVariant else ProstoMutedSlate
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        translationX = dragState.removingVisualX
                        translationY = dragState.removeOffsetYAnim.value
                        scaleX = dragState.removeScaleAnim.value
                        scaleY = dragState.removeScaleAnim.value
                        alpha = dragState.removeAlphaAnim.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDark) DarkSurface else Color.White,
                border = ProstoBorders.floatingBorder(isDark),
                shadowElevation = 0.dp,
                modifier = Modifier.size(38.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.a11y_remove_tab),
                        tint = normalTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private const val REMOVE_PROGRESS_STEPS = 20f

private fun removeProgressOf(dragOffsetYPx: Float, removeThresholdPx: Float): Float =
    ((-dragOffsetYPx).coerceAtLeast(0f) / removeThresholdPx).coerceIn(0f, 1f)

/** Read in the draw phase only: it follows the finger and the settle spring every frame. */
private fun overlayOffsetX(
    dragState: TabStripDragState,
    lazyListState: LazyListState,
    key: String?,
    isDragging: Boolean,
    isSettling: Boolean,
): Float =
    when {
        isDragging -> dragState.lastVisualXPx
        isSettling -> {
            val slotItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            val slotOffset = slotItem?.offset?.toFloat() ?: dragState.lastVisualXPx
            slotOffset + dragState.settleOffsetAnim.value
        }
        else -> 0f
    }

private fun overlayOffsetY(dragState: TabStripDragState, isDragging: Boolean, isSettling: Boolean): Float =
    when {
        isDragging -> dragState.dragOffsetYPx
        isSettling -> dragState.settleOffsetYAnim.value
        else -> 0f
    }
